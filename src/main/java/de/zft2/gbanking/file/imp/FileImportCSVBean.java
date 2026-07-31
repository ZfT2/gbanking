package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;

import java.io.IOException;
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

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.core.dto.DefaultCounterpart;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.mapper.ImportDaoMapper;

public class FileImportCSVBean extends AbstractBookingImportBean {

	private static final Logger log = LogManager.getLogger(FileImportCSVBean.class);

	public FileImportCSVBean(BaseWorker worker) {
		this(worker, null);
	}

	public FileImportCSVBean(BaseWorker worker, BankAccount contextAccount) {
		super(worker, contextAccount);
	}

	public boolean importFile(String importFile) {
		try {
			importFileToDatabase(importFile);
			return true;
		} catch (IOException e) {
			log.error("Error importing CSV file: {}", fileName(importFile), e);
			log.debug("CSV import path: {}", importFile);
			return false;
		}
	}

	public void importFileToDatabase(String importFile) throws IOException {
		Path importPath = prepareImportFile(importFile, "UI_PROGRESS_READ_CSV_FILE");
		List<CSVRecord> records = readRecords(importPath);
		validateHeaders(records);
		importRecords(records);
		finishImport();
	}

	private List<CSVRecord> readRecords(Path importPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(importPath, StandardCharsets.UTF_8);
				CSVParser parser = BookingCsvFormat.importFormat().parse(reader)) {
			return parser.getRecords();
		}
	}

	private void validateHeaders(List<CSVRecord> records) {
		if (records.isEmpty()) {
			throw new GBankingException("CSV file does not contain bookings.");
		}
		CSVRecord firstRecord = records.get(0);
		requireHeader(firstRecord, BookingCsvFormat.DATE_BOOKING);
		requireHeader(firstRecord, BookingCsvFormat.AMOUNT);
		requireHeader(firstRecord, BookingCsvFormat.PURPOSE);
		if (!hasContextAccount() && !hasAccountHeader(firstRecord)) {
			throw new GBankingException("CSV file must contain " + BookingCsvFormat.ACCOUNT_IBAN + ", " + BookingCsvFormat.ACCOUNT_NUMBER + " or "
					+ BookingCsvFormat.ACCOUNT_NAME + ".");
		}
	}

	private void requireHeader(CSVRecord csvRecord, String header) {
		if (!csvRecord.isMapped(header)) {
			throw new GBankingException("Missing CSV column: " + header);
		}
	}

	private boolean hasAccountHeader(CSVRecord csvRecord) {
		return csvRecord.isMapped(BookingCsvFormat.ACCOUNT_IBAN) || csvRecord.isMapped(BookingCsvFormat.ACCOUNT_NUMBER) || csvRecord.isMapped(BookingCsvFormat.ACCOUNT_NAME);
	}

	private void importRecords(List<CSVRecord> records) {
		List<Booking> importedBookings = new ArrayList<>();
		Map<Integer, ImportBankAccount> importAccountsByAccountId = new LinkedHashMap<>();
		Map<ImportBankAccount, BankAccount> accountsByImportAccount = new LinkedHashMap<>();
		int importedCount = 0;

		for (CSVRecord csvRecord : records) {
			updateWorkerState(progress(importedCount++, records.size()), "UI_PROGRESS_IMPORT_BOOKING_COUNT", importedCount, records.size());
			BankAccount account = resolveAccount(csvRecord);
			ImportBankAccount importAccount = importAccountsByAccountId.computeIfAbsent(account.getId(), ignored -> {
				ImportBankAccount newImportAccount = toImportAccount(account);
				accountsByImportAccount.put(newImportAccount, account);
				return newImportAccount;
			});
			importAccount.getBookings().add(mapBooking(csvRecord, importAccount));
		}

		importBookings(importAccountsByAccountId.values(), accountsByImportAccount, importedBookings);
		postProcessImportedBookings(importedBookings);
	}

	private BankAccount resolveAccount(CSVRecord csvRecord) {
		if (hasContextAccount()) {
			validateContextAccount(csvRecord);
			return getContextAccount();
		}

		BankAccount account = findExistingAccount(csvRecord);
		return account != null ? account : createAccount(csvRecord);
	}

	private void validateContextAccount(CSVRecord csvRecord) {
		if (!matchesContextAccount(csvRecord)) {
			throw new GBankingException("CSV row " + csvRecord.getRecordNumber() + " does not match selected account " + getContextAccount().getAccountName() + ".");
		}
	}

	private BankAccount findExistingAccount(CSVRecord csvRecord) {
		String iban = BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_IBAN);
		BankAccount account = lookupByIban(iban);
		if (account == null) {
			account = lookupByIbanSuffix(iban);
		}
		if (account != null) {
			return account;
		}
		account = lookupByNumber(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NUMBER));
		return account != null ? account : lookupByName(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NAME));
	}

	private boolean matchesContextAccount(CSVRecord csvRecord) {
		String iban = BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_IBAN);
		String number = BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NUMBER);
		String name = BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NAME);

		if (iban != null) {
			return matchesIdentifier(iban, getContextAccount().getIban()) || matchesIbanSuffix(iban, getContextAccount().getNumber());
		}
		if (number != null) {
			return matchesIdentifier(number, getContextAccount().getNumber());
		}
		return name == null || matchesIdentifier(name, getContextAccount().getAccountName());
	}

	private BankAccount createAccount(CSVRecord csvRecord) {
		ImportBankAccount importAccount = mapAccount(csvRecord);
		if (importAccount.getAccountName() == null) {
			throw new GBankingException("CSV row " + csvRecord.getRecordNumber() + " has no account identifier.");
		}

		BankAccount account = ImportDaoMapper.maptoBankAccountDao(importAccount, Source.IMPORT_INITIAL);
		BankAccount persistedAccount = dbController.insertOrUpdate(account);
		if (persistedAccount == null) {
			throw new GBankingException("Could not create account for CSV row " + csvRecord.getRecordNumber() + ".");
		}
		registerAccount(persistedAccount);
		return persistedAccount;
	}

	private ImportBankAccount mapAccount(CSVRecord csvRecord) {
		ImportBankAccount importAccount = new ImportBankAccount();
		importAccount.setAccountName(firstNonBlank(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NAME),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_IBAN), BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NUMBER)));
		importAccount.setNamePP(importAccount.getAccountName());
		importAccount.setIban(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_IBAN));
		importAccount.setNumber(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NUMBER));
		importAccount.setBankName(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_BANK));
		importAccount.setBic(BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_BIC));
		importAccount.setCurrency(defaultCurrency(csvRecord));
		importAccount.setAccountType(AccountType.CURRENT_ACCOUNT);
		importAccount.setAccountState(AccountState.ACTIVE);
		importAccount.setSource(Source.IMPORT_INITIAL);
		importAccount.setOfflineAccount(true);
		return importAccount;
	}

	private ImportBooking mapBooking(CSVRecord csvRecord, ImportBankAccount account) {
		BigDecimal amount = requireAmount(csvRecord);
		LocalDate dateBooking = requireDate(csvRecord, BookingCsvFormat.DATE_BOOKING);

		ImportBooking booking = new ImportBooking();
		booking.setAccountName(account.getNamePP());
		booking.setSource(Source.IMPORT);
		booking.setDateBooking(dateBooking);
		booking.setDateValue(BookingCsvFormat.parseDate(BookingCsvFormat.text(csvRecord, BookingCsvFormat.DATE_VALUE)));
		booking.setPurpose(requireText(csvRecord, BookingCsvFormat.PURPOSE));
		booking.setAmount(amount);
		booking.setCurrency(defaultCurrency(csvRecord));
		booking.setBookingType(resolveBookingType(csvRecord, amount));
		booking.setCategory(resolveCategory(csvRecord));
		mapRecipient(csvRecord, booking);
		booking.setSepaCustomerRef(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_CUSTOMER_REF));
		booking.setSepaCreditorId(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_CREDITOR_ID));
		booking.setSepaEndToEnd(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_END_TO_END));
		booking.setSepaMandate(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_MANDATE));
		booking.setSepaPersonId(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_PERSON_ID));
		booking.setSepaPurpose(BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_PURPOSE));
		booking.setSepaType(resolveSepaType(csvRecord));
		return booking;
	}

	private BigDecimal requireAmount(CSVRecord csvRecord) {
		try {
			BigDecimal amount = BookingCsvFormat.parseAmount(BookingCsvFormat.text(csvRecord, BookingCsvFormat.AMOUNT));
			if (amount != null) {
				return amount;
			}
		} catch (NumberFormatException e) {
			throw new GBankingException("Invalid amount in CSV row " + csvRecord.getRecordNumber() + ".");
		}
		throw new GBankingException("Missing amount in CSV row " + csvRecord.getRecordNumber() + ".");
	}

	private LocalDate requireDate(CSVRecord csvRecord, String header) {
		LocalDate date = BookingCsvFormat.parseDate(BookingCsvFormat.text(csvRecord, header));
		if (date == null) {
			throw new GBankingException("Missing or invalid date in CSV row " + csvRecord.getRecordNumber() + ".");
		}
		return date;
	}

	private String requireText(CSVRecord csvRecord, String header) {
		String text = BookingCsvFormat.text(csvRecord, header);
		if (text == null) {
			throw new GBankingException("Missing " + header + " in CSV row " + csvRecord.getRecordNumber() + ".");
		}
		return text;
	}

	private String defaultCurrency(CSVRecord csvRecord) {
		return firstNonBlank(BookingCsvFormat.text(csvRecord, BookingCsvFormat.CURRENCY), "EUR");
	}

	private BookingType resolveBookingType(CSVRecord csvRecord, BigDecimal amount) {
		String value = BookingCsvFormat.text(csvRecord, BookingCsvFormat.BOOKING_TYPE);
		if (value != null) {
			for (BookingType type : BookingType.values()) {
				if (type.matches(value)) {
					return type;
				}
			}
		}
		return amount.signum() >= 0 ? BookingType.DEPOSIT : BookingType.REMOVAL;
	}

	private SepaType resolveSepaType(CSVRecord csvRecord) {
		String value = BookingCsvFormat.text(csvRecord, BookingCsvFormat.SEPA_TYPE);
		if (value == null) {
			return null;
		}
		for (SepaType type : SepaType.values()) {
			if (type.matches(value)) {
				return type;
			}
		}
		return null;
	}

	private String resolveCategory(CSVRecord csvRecord) {
		return BookingCsvFormat.text(csvRecord, BookingCsvFormat.CATEGORY);
	}

	private void mapRecipient(CSVRecord csvRecord, ImportBooking booking) {
		booking.setCounterpart(DefaultCounterpart.ofNullable(BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_NAME),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_IBAN),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_BIC),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_ACCOUNT_NUMBER),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_BLZ),
				BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_BANK)));
	}
}
