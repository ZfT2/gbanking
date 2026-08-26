package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.core.dto.Counterpart;
import de.zft2.core.dto.DefaultCounterpart;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Analysis;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Match;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Problem;
import de.zft2.gbanking.file.imp.csv.CsvImportData;
import de.zft2.gbanking.file.imp.csv.CsvImportDefinition;
import de.zft2.gbanking.file.imp.csv.CsvImportTarget;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.mapper.ImportDaoMapper;

public class FileImportCSVBean extends AbstractBookingImportBean {

	private static final Logger log = LogManager.getLogger(FileImportCSVBean.class);
	private static final Pattern PAYMENT_RECIPIENT_PATTERN = Pattern.compile(
			"^Bezahlung von\\s+([A-Z]{2}\\d{2}[A-Z0-9]{11,30})\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private final CsvImportAnalyzer analyzer;
	private final String definitionName;
	private final List<RejectedRow> rejectedRows = new ArrayList<>();

	public FileImportCSVBean(BaseWorker worker) {
		this(worker, null, null);
	}

	public FileImportCSVBean(BaseWorker worker, BankAccount contextAccount) {
		this(worker, contextAccount, null);
	}

	public FileImportCSVBean(BaseWorker worker, BankAccount contextAccount, String definitionName) {
		this(worker, contextAccount, definitionName, new CsvImportAnalyzer());
	}

	FileImportCSVBean(BaseWorker worker, BankAccount contextAccount, String definitionName, CsvImportAnalyzer analyzer) {
		super(worker, contextAccount);
		this.definitionName = definitionName;
		this.analyzer = analyzer;
	}

	public boolean importFile(String importFile) {
		try {
			importFileToDatabase(importFile);
			return true;
		} catch (IOException exception) {
			log.error("Error importing CSV file: {}", fileName(importFile), exception);
			log.debug("CSV import path: {}", importFile);
			return false;
		}
	}

	public void importFileToDatabase(String importFile) throws IOException {
		Path importPath = prepareImportFile(importFile, "UI_PROGRESS_READ_CSV_FILE");
		Match match = resolveMatch(importPath);
		CsvImportData data = analyzer.read(importPath, match.definition());
		if (data.rows().isEmpty()) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_NO_BOOKINGS"));
		}

		rejectedRows.clear();
		dbController.executeInTransaction(() -> importRecords(data.rows(), match.definition()));
		finishImport();
	}

	public List<RejectedRow> getRejectedRows() {
		return List.copyOf(rejectedRows);
	}

	private Match resolveMatch(Path importPath) throws IOException {
		if (definitionName != null) {
			Match selectedMatch = analyzer.match(importPath, definitionName);
			if (selectedMatch == null) {
				throw new GBankingException(getText("ERROR_CSV_IMPORT_DEFINITION_NOT_FOUND", definitionName));
			}
			requireMandatoryHeaders(selectedMatch);
			return selectedMatch;
		}

		Analysis analysis = analyzer.analyze(importPath);
		if (analysis.problem() == Problem.UNKNOWN_DEFINITION) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_UNKNOWN_DEFINITION", analysis.definitionFile().toString()));
		}
		if (analysis.problem() == Problem.MISSING_REQUIRED_FIELDS) {
			throw missingFieldsException(analysis.matches());
		}
		if (analysis.matches().size() != 1) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_AMBIGUOUS_DEFINITION"));
		}
		return analysis.matches().get(0);
	}

	private void requireMandatoryHeaders(Match match) {
		if (!match.hasRequiredHeaders()) {
			throw missingFieldsException(match);
		}
	}

	private GBankingException missingFieldsException(Match match) {
		return new GBankingException(getText("ERROR_CSV_IMPORT_MISSING_REQUIRED_FIELDS", match.definition().getName(),
				joinHeaders(match.missingRequiredHeaders())));
	}

	private GBankingException missingFieldsException(List<Match> matches) {
		if (matches.size() == 1) {
			return missingFieldsException(matches.get(0));
		}
		String details = matches.stream().map(match -> match.definition().getName() + ": " + joinHeaders(match.missingRequiredHeaders()))
				.collect(java.util.stream.Collectors.joining(System.lineSeparator()));
		return new GBankingException(getText("ERROR_CSV_IMPORT_MISSING_REQUIRED_FIELDS_MULTIPLE", details));
	}

	private void importRecords(List<CsvImportData.Row> rows, CsvImportDefinition definition) {
		List<Booking> importedBookings = new ArrayList<>();
		Map<Integer, ImportBankAccount> importAccountsByAccountId = new LinkedHashMap<>();
		Map<ImportBankAccount, BankAccount> accountsByImportAccount = new LinkedHashMap<>();
		int processedRecords = 0;

		for (CsvImportData.Row row : rows) {
			updateWorkerState(progress(++processedRecords, rows.size()), "UI_PROGRESS_IMPORT_BOOKING_COUNT", processedRecords, rows.size());
			BankAccount account = resolveAccount(row, definition);
			ImportBankAccount importAccount = importAccountsByAccountId.computeIfAbsent(account.getId(), ignored -> {
				ImportBankAccount newImportAccount = toImportAccount(account);
				accountsByImportAccount.put(newImportAccount, account);
				return newImportAccount;
			});
			try {
				importAccount.getBookings().add(mapBooking(row, definition, importAccount));
			} catch (CsvRowException exception) {
				rejectedRows.add(new RejectedRow(row.lineNumber(), exception.getMessage()));
			}
		}

		importBookings(importAccountsByAccountId.values(), accountsByImportAccount, importedBookings);
		postProcessImportedBookings(importedBookings);
	}

	private BankAccount resolveAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		if (hasContextAccount()) {
			validateContextAccount(row, definition);
			return getContextAccount();
		}

		BankAccount account = findExistingAccount(row, definition);
		return account != null ? account : createAccount(row, definition);
	}

	private void validateContextAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		if (!matchesContextAccount(row, definition)) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_ACCOUNT_MISMATCH", row.lineNumber(), getContextAccount().getAccountName()));
		}
	}

	private BankAccount findExistingAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		String iban = text(row, definition, CsvImportTarget.ACCOUNT_IBAN);
		BankAccount account = lookupByIban(iban);
		if (account == null) {
			account = lookupByIbanSuffix(iban);
		}
		if (account != null) {
			return account;
		}
		account = lookupByNumber(text(row, definition, CsvImportTarget.ACCOUNT_NUMBER));
		return account != null ? account : lookupByName(text(row, definition, CsvImportTarget.ACCOUNT_NAME));
	}

	private boolean matchesContextAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		String iban = text(row, definition, CsvImportTarget.ACCOUNT_IBAN);
		String number = text(row, definition, CsvImportTarget.ACCOUNT_NUMBER);
		String name = text(row, definition, CsvImportTarget.ACCOUNT_NAME);
		if (iban != null) {
			return matchesIdentifier(iban, getContextAccount().getIban()) || matchesIbanSuffix(iban, getContextAccount().getNumber());
		}
		if (number != null) {
			return matchesIdentifier(number, getContextAccount().getNumber());
		}
		return name == null || matchesIdentifier(name, getContextAccount().getAccountName());
	}

	private BankAccount createAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		ImportBankAccount importAccount = mapAccount(row, definition);
		if (importAccount.getAccountName() == null) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_ACCOUNT_REQUIRED", row.lineNumber()));
		}

		BankAccount persistedAccount = dbController.insertOrUpdate(ImportDaoMapper.maptoBankAccountDao(importAccount, Source.IMPORT_INITIAL));
		if (persistedAccount == null) {
			throw new GBankingException(getText("ERROR_CSV_IMPORT_ACCOUNT_CREATE_FAILED", row.lineNumber()));
		}
		registerAccount(persistedAccount);
		return persistedAccount;
	}

	private ImportBankAccount mapAccount(CsvImportData.Row row, CsvImportDefinition definition) {
		ImportBankAccount importAccount = new ImportBankAccount();
		importAccount.setAccountName(firstNonBlank(text(row, definition, CsvImportTarget.ACCOUNT_NAME),
				text(row, definition, CsvImportTarget.ACCOUNT_IBAN), text(row, definition, CsvImportTarget.ACCOUNT_NUMBER)));
		importAccount.setNamePP(importAccount.getAccountName());
		importAccount.setIban(text(row, definition, CsvImportTarget.ACCOUNT_IBAN));
		importAccount.setNumber(text(row, definition, CsvImportTarget.ACCOUNT_NUMBER));
		importAccount.setBankName(text(row, definition, CsvImportTarget.ACCOUNT_BANK));
		importAccount.setBic(text(row, definition, CsvImportTarget.ACCOUNT_BIC));
		importAccount.setCurrency(defaultCurrency(row, definition));
		importAccount.setAccountType(AccountType.CURRENT_ACCOUNT);
		importAccount.setAccountState(AccountState.ACTIVE);
		importAccount.setSource(Source.IMPORT_INITIAL);
		importAccount.setOfflineAccount(true);
		return importAccount;
	}

	private ImportBooking mapBooking(CsvImportData.Row row, CsvImportDefinition definition, ImportBankAccount account) {
		BigDecimal amount = requireAmount(row, definition);
		ImportBooking booking = new ImportBooking();
		booking.setAccountName(account.getNamePP());
		booking.setSource(Source.IMPORT);
		booking.setDateBooking(parseDate(row, definition, CsvImportTarget.DATE, definition.getDateOrder()));
		booking.setDateValue(parseDate(row, definition, CsvImportTarget.VALUE_DATE, definition.getValueDateOrder()));
		booking.setPurpose(purpose(row, definition));
		booking.setAmount(amount);
		booking.setCurrency(defaultCurrency(row, definition));
		booking.setBookingType(resolveBookingType(row, definition, amount));
		booking.setCategory(resolveCategory(row, definition));
		mapRecipient(row, definition, booking);
		mapSepa(row, definition, booking);
		mapCreditcardDetails(row, definition, booking);
		mapExistingRecipient(booking);
		return booking;
	}

	private BigDecimal requireAmount(CsvImportData.Row row, CsvImportDefinition definition) {
		List<String> amountFields = definition.getSourceFields(CsvImportTarget.AMOUNT);
		try {
			BigDecimal amount;
			if (amountFields.size() == 1) {
				amount = parseDecimal(row.text(amountFields.get(0)), definition);
			} else if (amountFields.size() == 2) {
				amount = splitAmount(row, definition, amountFields);
			} else {
				throw new GBankingException(getText("ERROR_CSV_IMPORT_AMOUNT_MAPPING", definition.getName()));
			}
			if (amount == null) {
				if (hasForeignCurrencyData(row, definition)) {
					throw new GBankingException("Eine Fremdwährungsbuchung in Zeile " + row.lineNumber()
							+ " enthält keinen Betrag in der Kontowährung. Der Import wurde abgebrochen.");
				}
				throw new CsvRowException(getText("ERROR_CSV_IMPORT_AMOUNT_MISSING", row.lineNumber()));
			}
			return definition.isSwapAmount() ? amount.negate() : amount;
		} catch (NumberFormatException exception) {
			throw new CsvRowException(getText("ERROR_CSV_IMPORT_AMOUNT_INVALID", row.lineNumber()), exception);
		}
	}

	private boolean hasForeignCurrencyData(CsvImportData.Row row, CsvImportDefinition definition) {
		return text(row, definition, CsvImportTarget.CREDITCARD_CURRENCY) != null
				|| text(row, definition, CsvImportTarget.CREDITCARD_CURRENCY_AMOUNT) != null;
	}

	private BigDecimal splitAmount(CsvImportData.Row row, CsvImportDefinition definition, List<String> amountFields) {
		String creditValue = row.text(amountFields.get(0));
		String debitValue = row.text(amountFields.get(1));
		if (creditValue != null && debitValue != null) {
			throw new CsvRowException(getText("ERROR_CSV_IMPORT_AMOUNT_AMBIGUOUS", row.lineNumber()));
		}
		BigDecimal value = parseDecimal(creditValue != null ? creditValue : debitValue, definition);
		return value == null ? null : creditValue != null ? value.abs() : value.abs().negate();
	}

	private BigDecimal parseDecimal(String value, CsvImportDefinition definition) {
		if (value == null) {
			return null;
		}
		if ("auto".equalsIgnoreCase(definition.getDecimalSeparator())) {
			return new BigDecimal(normalizeDecimal(value));
		}
		String normalized = value.trim().replace("\u00A0", "").replace(" ", "");
		String thousandSeparator = definition.getThousandSeparator();
		String decimalSeparator = definition.getDecimalSeparator();
		if (thousandSeparator != null && !thousandSeparator.isEmpty() && !thousandSeparator.equals(decimalSeparator)) {
			normalized = normalized.replace(thousandSeparator, "");
		}
		return new BigDecimal(".".equals(decimalSeparator) ? normalized : normalized.replace(decimalSeparator, "."));
	}

	private String normalizeDecimal(String value) {
		String normalized = value.trim().replace("\u00A0", "").replace(" ", "");
		if (normalized.contains(",") && normalized.contains(".")) {
			return normalized.lastIndexOf(',') > normalized.lastIndexOf('.')
					? normalized.replace(".", "").replace(',', '.')
					: normalized.replace(",", "");
		}
		return normalized.replace(',', '.');
	}

	private LocalDate parseDate(CsvImportData.Row row, CsvImportDefinition definition, CsvImportTarget target, String dateOrder) {
		String value = text(row, definition, target);
		if (value == null) {
			return null;
		}
		try {
			LocalDate result = dateOrder == null || dateOrder.isBlank() ? BookingCsvFormat.parseDate(value) : parseOrderedDate(value, dateOrder);
			if (result == null) {
				throw new DateTimeException("Unsupported date format");
			}
			return result;
		} catch (DateTimeException | NumberFormatException exception) {
			throw new CsvRowException(getText("ERROR_CSV_IMPORT_DATE_INVALID", row.lineNumber(), value), exception);
		}
	}

	private LocalDate parseOrderedDate(String value, String dateOrder) {
		String order = dateOrder.trim().toUpperCase(Locale.ROOT);
		String[] parts = value.trim().split("\\D+");
		if (parts.length != 3 || order.length() != 3 || !toCharacters(order).equals(Set.of('T', 'M', 'J'))) {
			throw new DateTimeException("Unsupported date format");
		}
		int day = Integer.parseInt(parts[order.indexOf('T')]);
		int month = Integer.parseInt(parts[order.indexOf('M')]);
		int year = Integer.parseInt(parts[order.indexOf('J')]);
		if (year < 100) {
			year += year >= 70 ? 1900 : 2000;
		}
		return LocalDate.of(year, month, day);
	}

	private Set<Character> toCharacters(String value) {
		Set<Character> characters = new HashSet<>();
		for (int index = 0; index < value.length(); index++) {
			characters.add(value.charAt(index));
		}
		return characters;
	}

	private String purpose(CsvImportData.Row row, CsvImportDefinition definition) {
		List<String> sourceFields = definition.getSourceFields(CsvImportTarget.PURPOSE);
		List<String> values = new ArrayList<>();
		for (String sourceField : sourceFields) {
			String value = row.text(sourceField);
			if (value != null) {
				values.add(sourceFields.size() == 1 || definition.isPurposeWithoutColumnNames() ? value : sourceField + ": " + value);
			}
		}
		return values.isEmpty() ? null : String.join(System.lineSeparator(), values);
	}

	private String defaultCurrency(CsvImportData.Row row, CsvImportDefinition definition) {
		return firstNonBlank(text(row, definition, CsvImportTarget.CURRENCY), definition.getDefaultCurrency());
	}

	private BookingType resolveBookingType(CsvImportData.Row row, CsvImportDefinition definition, BigDecimal amount) {
		String value = text(row, definition, CsvImportTarget.BOOKING_TYPE);
		if (value != null) {
			for (BookingType type : BookingType.values()) {
				if (type.matches(value)) {
					return type;
				}
			}
		}
		return amount.signum() >= 0 ? BookingType.DEPOSIT : BookingType.REMOVAL;
	}

	private String resolveCategory(CsvImportData.Row row, CsvImportDefinition definition) {
		String mainCategory = text(row, definition, CsvImportTarget.MAIN_CATEGORY);
		String subCategory = text(row, definition, CsvImportTarget.SUB_CATEGORY);
		return mainCategory != null && subCategory != null ? mainCategory + ":" + subCategory : firstNonBlank(mainCategory, subCategory);
	}

	private void mapRecipient(CsvImportData.Row row, CsvImportDefinition definition, ImportBooking booking) {
		booking.setCounterpart(DefaultCounterpart.ofNullable(text(row, definition, CsvImportTarget.RECIPIENT_NAME),
				text(row, definition, CsvImportTarget.RECIPIENT_IBAN), text(row, definition, CsvImportTarget.RECIPIENT_BIC),
				text(row, definition, CsvImportTarget.RECIPIENT_ACCOUNT_NUMBER), text(row, definition, CsvImportTarget.RECIPIENT_BANK_CODE),
				text(row, definition, CsvImportTarget.RECIPIENT_BANK)));
	}

	private void mapSepa(CsvImportData.Row row, CsvImportDefinition definition, ImportBooking booking) {
		booking.setSepaCustomerRef(text(row, definition, CsvImportTarget.SEPA_CUSTOMER_REFERENCE));
		booking.setSepaCreditorId(text(row, definition, CsvImportTarget.SEPA_CREDITOR_ID));
		booking.setSepaEndToEnd(text(row, definition, CsvImportTarget.SEPA_END_TO_END));
		booking.setSepaMandate(text(row, definition, CsvImportTarget.SEPA_MANDATE));
		booking.setSepaPersonId(text(row, definition, CsvImportTarget.SEPA_PERSON_ID));
		booking.setSepaPurpose(text(row, definition, CsvImportTarget.SEPA_PURPOSE));
		booking.setSepaType(resolveSepaType(text(row, definition, CsvImportTarget.SEPA_TYPE)));
	}

	private SepaType resolveSepaType(String value) {
		if (value != null) {
			for (SepaType type : SepaType.values()) {
				if (type.matches(value)) {
					return type;
				}
			}
		}
		return null;
	}

	private void mapCreditcardDetails(CsvImportData.Row row, CsvImportDefinition definition, ImportBooking booking) {
		booking.setCreditcardTransactionDate(parseDate(row, definition, CsvImportTarget.CREDITCARD_TRANSACTION_DATE, definition.getDateOrder()));
		booking.setCreditcardType(text(row, definition, CsvImportTarget.CREDITCARD_TYPE));
		booking.setForeignAmount(optionalDecimal(row, definition, CsvImportTarget.CREDITCARD_CURRENCY_AMOUNT));
		booking.setExchangeRateToBaseCurrency(optionalDecimal(row, definition, CsvImportTarget.CREDITCARD_CURRENCY_RATE));
		booking.setForeignCurrency(text(row, definition, CsvImportTarget.CREDITCARD_CURRENCY));
		booking.setFeeAmount(optionalDecimal(row, definition, CsvImportTarget.FEE_AMOUNT));
		booking.setFeeCurrency(text(row, definition, CsvImportTarget.FEE_CURRENCY));
		booking.setCreditcardMerchantArea(text(row, definition, CsvImportTarget.CREDITCARD_MERCHANT_AREA));
		booking.setCreditcardMerchantCategory(text(row, definition, CsvImportTarget.CREDITCARD_MERCHANT_CATEGORY));
	}

	private BigDecimal optionalDecimal(CsvImportData.Row row, CsvImportDefinition definition, CsvImportTarget target) {
		String value = text(row, definition, target);
		try {
			return parseDecimal(value, definition);
		} catch (NumberFormatException exception) {
			throw new CsvRowException(getText("ERROR_CSV_IMPORT_DECIMAL_INVALID", row.lineNumber(), value), exception);
		}
	}

	private void mapExistingRecipient(ImportBooking booking) {
		if (booking.getPurpose() == null || Counterpart.hasData(booking.getCounterpart())) {
			return;
		}
		Matcher matcher = PAYMENT_RECIPIENT_PATTERN.matcher(booking.getPurpose());
		if (matcher.find()) {
			Recipient recipient = dbController.findPreferredRecipientByIban(matcher.group(1));
			if (recipient != null) {
				booking.setCounterpart(recipient);
				booking.setRecipientId(recipient.getId());
			}
		}
	}

	private String text(CsvImportData.Row row, CsvImportDefinition definition, CsvImportTarget target) {
		for (String sourceField : definition.getSourceFields(target)) {
			String value = row.text(sourceField);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private String joinHeaders(Set<String> headers) {
		return String.join(", ", headers.stream().sorted().toList());
	}

	public record RejectedRow(long lineNumber, String reason) {
	}

	private static class CsvRowException extends GBankingException {

		private static final long serialVersionUID = 1L;

		CsvRowException(String message) {
			super(message);
		}

		CsvRowException(String message, Exception cause) {
			super(message, cause);
		}
	}
}
