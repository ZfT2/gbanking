package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.gui.BaseWorker;

public class CreditcardCsvImportBean extends AbstractBookingImportBean {

	private static final char UTF8_BYTE_ORDER_MARK = '\uFEFF';
	private static final Pattern PAYMENT_RECIPIENT_PATTERN = Pattern.compile(
			"^Bezahlung von\\s+([A-Z]{2}\\d{2}[A-Z0-9]{11,30})\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
			.setDelimiter(';')
			.setHeader()
			.setSkipHeaderRecord(true)
			.setTrim(true)
			.get();

	private final List<RejectedRow> rejectedRows = new ArrayList<>();

	public CreditcardCsvImportBean(BaseWorker worker, BankAccount contextAccount) {
		super(worker, contextAccount);
	}

	public void importFileToDatabase(String importFile) throws IOException {
		Path importPath = prepareImportFile(importFile, "UI_PROGRESS_READ_CREDITCARD_CSV_FILE");
		BankAccount account = requireContextAccount();
		ParsedCsv parsedCsv = readCsv(importPath);

		rejectedRows.clear();
		importRecords(parsedCsv, account);
		finishImport();
	}

	public List<RejectedRow> getRejectedRows() {
		return List.copyOf(rejectedRows);
	}

	private BankAccount requireContextAccount() {
		if (!hasContextAccount()) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_ACCOUNT_REQUIRED"));
		}
		BankAccount account = getContextAccount();
		if (account.getCurrency() == null || account.getCurrency().isBlank()) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_ACCOUNT_CURRENCY_REQUIRED", account.getAccountName()));
		}
		return account;
	}

	private ParsedCsv readCsv(Path importPath) throws IOException {
		try (Reader reader = newUtf8ReaderWithoutBom(importPath); CSVParser parser = CSV_FORMAT.parse(reader)) {
			CreditcardCsvFormat format = CreditcardCsvFormat.detect(parser.getHeaderMap().keySet())
					.orElseThrow(() -> new GBankingException(getText("ERROR_CREDITCARD_IMPORT_UNSUPPORTED_FORMAT",
							String.join(", ", parser.getHeaderMap().keySet()))));
			List<CSVRecord> records = parser.getRecords();
			if (records.isEmpty()) {
				throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_NO_BOOKINGS"));
			}
			return new ParsedCsv(format, records);
		}
	}

	private Reader newUtf8ReaderWithoutBom(Path importPath) throws IOException {
		PushbackReader reader = new PushbackReader(Files.newBufferedReader(importPath, StandardCharsets.UTF_8), 1);
		try {
			int firstCharacter = reader.read();
			if (firstCharacter != UTF8_BYTE_ORDER_MARK && firstCharacter != -1) {
				reader.unread(firstCharacter);
			}
			return reader;
		} catch (IOException exception) {
			try {
				reader.close();
			} catch (IOException closeException) {
				exception.addSuppressed(closeException);
			}
			throw exception;
		}
	}

	private void importRecords(ParsedCsv parsedCsv, BankAccount account) {
		ImportBankAccount importAccount = toImportAccount(account);
		int processedRecords = 0;

		for (CSVRecord csvRecord : parsedCsv.records()) {
			processedRecords++;
			updateWorkerState(progress(processedRecords, parsedCsv.records().size()), "UI_PROGRESS_IMPORT_CREDITCARD_BOOKING_COUNT",
					processedRecords, parsedCsv.records().size());
			try {
				importAccount.getBookings().add(mapBooking(csvRecord, parsedCsv.format(), importAccount));
			} catch (GBankingException exception) {
				rejectedRows.add(new RejectedRow(csvRecord.getRecordNumber() + 1, exception.getMessage()));
			}
		}

		List<Booking> importedBookings = new ArrayList<>();
		Map<ImportBankAccount, BankAccount> accountMapping = new LinkedHashMap<>();
		accountMapping.put(importAccount, account);
		importBookings(List.of(importAccount), accountMapping, importedBookings);
		postProcessImportedBookings(importedBookings);
	}

	private ImportBooking mapBooking(CSVRecord csvRecord, CreditcardCsvFormat format, ImportBankAccount account) {
		return switch (format) {
		case DETAILED -> mapDetailedBooking(csvRecord, account);
		case SIMPLE -> mapSimpleBooking(csvRecord, account);
		};
	}

	private ImportBooking mapDetailedBooking(CSVRecord csvRecord, ImportBankAccount account) {
		ImportBooking booking = createBooking(csvRecord, account, CreditcardCsvFormat.BOOK_DATE, CreditcardCsvFormat.VALUE_DATE,
				CreditcardCsvFormat.TEXT, requireDecimal(csvRecord, CreditcardCsvFormat.AMOUNT));
		booking.setCreditcardTransactionDate(requireDate(csvRecord, CreditcardCsvFormat.TRANSACTION_DATE));
		booking.setCreditcardType(text(csvRecord, CreditcardCsvFormat.TYPE));
		booking.setCreditcardCurrencyAmount(requireDecimal(csvRecord, CreditcardCsvFormat.CURRENCY_AMOUNT));
		booking.setCreditcardCurrencyRate(requireDecimal(csvRecord, CreditcardCsvFormat.CURRENCY_RATE));
		booking.setCreditcardCurrency(requireText(csvRecord, CreditcardCsvFormat.CURRENCY));
		booking.setCreditcardMerchantArea(text(csvRecord, CreditcardCsvFormat.MERCHANT_AREA));
		booking.setCreditcardMerchantCategory(text(csvRecord, CreditcardCsvFormat.MERCHANT_CATEGORY));
		return booking;
	}

	private ImportBooking mapSimpleBooking(CSVRecord csvRecord, ImportBankAccount account) {
		return createBooking(csvRecord, account, CreditcardCsvFormat.DATE, CreditcardCsvFormat.SIMPLE_VALUE_DATE,
				CreditcardCsvFormat.DESCRIPTION, resolveSimpleAmount(csvRecord));
	}

	private ImportBooking createBooking(CSVRecord csvRecord, ImportBankAccount account, String bookingDateHeader, String valueDateHeader,
			String purposeHeader, BigDecimal amount) {
		ImportBooking booking = new ImportBooking();
		booking.setAccountName(account.getNamePP());
		booking.setSource(Source.IMPORT);
		booking.setDateBooking(requireDate(csvRecord, bookingDateHeader));
		booking.setDateValue(requireDate(csvRecord, valueDateHeader));
		booking.setPurpose(requireText(csvRecord, purposeHeader));
		booking.setAmount(amount);
		booking.setCurrency(account.getCurrency());
		booking.setBookingType(amount.signum() >= 0 ? BookingType.DEPOSIT : BookingType.REMOVAL);
		mapExistingRecipient(booking);
		return booking;
	}

	private void mapExistingRecipient(ImportBooking booking) {
		Matcher matcher = PAYMENT_RECIPIENT_PATTERN.matcher(booking.getPurpose());
		if (!matcher.find()) {
			return;
		}

		Recipient recipient = dbController.findPreferredRecipientByIban(matcher.group(1));
		if (recipient != null) {
			booking.setCounterpart(recipient);
			booking.setRecipientId(recipient.getId());
		}
	}

	private BigDecimal resolveSimpleAmount(CSVRecord csvRecord) {
		String fromAccount = text(csvRecord, CreditcardCsvFormat.FROM_ACCOUNT);
		String toAccount = text(csvRecord, CreditcardCsvFormat.TO_ACCOUNT);
		if (fromAccount == null && toAccount == null) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_AMOUNT_MISSING"));
		}
		if (fromAccount != null && toAccount != null) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_AMOUNT_AMBIGUOUS"));
		}

		BigDecimal amount = parseDecimal(fromAccount != null ? fromAccount : toAccount,
				fromAccount != null ? CreditcardCsvFormat.FROM_ACCOUNT : CreditcardCsvFormat.TO_ACCOUNT);
		return fromAccount != null ? amount.abs().negate() : amount.abs();
	}

	private LocalDate requireDate(CSVRecord csvRecord, String header) {
		LocalDate date = BookingCsvFormat.parseDate(text(csvRecord, header));
		if (date == null) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_DATE_INVALID", header));
		}
		return date;
	}

	private BigDecimal requireDecimal(CSVRecord csvRecord, String header) {
		String value = text(csvRecord, header);
		if (value == null) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_VALUE_MISSING", header));
		}
		return parseDecimal(value, header);
	}

	private BigDecimal parseDecimal(String value, String header) {
		try {
			return new BigDecimal(normalizeDecimal(value));
		} catch (NumberFormatException exception) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_DECIMAL_INVALID", header), exception);
		}
	}

	private String requireText(CSVRecord csvRecord, String header) {
		String value = text(csvRecord, header);
		if (value == null) {
			throw new GBankingException(getText("ERROR_CREDITCARD_IMPORT_VALUE_MISSING", header));
		}
		return value;
	}

	private String text(CSVRecord csvRecord, String header) {
		return trimToNull(csvRecord.get(header));
	}

	private String normalizeDecimal(String value) {
		String trimmedValue = value.trim();
		if (trimmedValue.contains(",") && trimmedValue.contains(".")) {
			return trimmedValue.lastIndexOf(',') > trimmedValue.lastIndexOf('.')
					? trimmedValue.replace(".", "").replace(',', '.')
					: trimmedValue.replace(",", "");
		}
		return trimmedValue.contains(",") ? trimmedValue.replace(',', '.') : trimmedValue;
	}

	public record RejectedRow(long lineNumber, String reason) {
	}

	private record ParsedCsv(CreditcardCsvFormat format, List<CSVRecord> records) {
	}
}
