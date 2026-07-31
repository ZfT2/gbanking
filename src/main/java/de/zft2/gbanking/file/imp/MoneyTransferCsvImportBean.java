package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.exp.FileExportBean.ExportConstants;
import de.zft2.gbanking.gui.BaseWorker;

public class MoneyTransferCsvImportBean implements BaseMessagesDb {

	private static final Logger log = LogManager.getLogger(MoneyTransferCsvImportBean.class);

	private static final String HEADER_SENDER_IBAN = "AUFTRAGGEBER_IBAN";
	private static final String HEADER_RECIPIENT_NAME = "NAME";
	private static final String HEADER_RECIPIENT_IBAN = "IBAN";
	private static final String HEADER_RECIPIENT_BIC = "BIC";
	private static final String HEADER_AMOUNT = "BETRAG";
	private static final String HEADER_PURPOSE = "ZWECK";
	private static final String HEADER_PURPOSE_CODE = "PURPOSECODE";

	private static final List<String> REQUIRED_HEADERS = List.of(HEADER_SENDER_IBAN, HEADER_RECIPIENT_NAME, HEADER_RECIPIENT_IBAN,
			HEADER_RECIPIENT_BIC, HEADER_AMOUNT, HEADER_PURPOSE, HEADER_PURPOSE_CODE);

	private final BaseWorker worker;

	public MoneyTransferCsvImportBean() {
		this(null);
	}

	public MoneyTransferCsvImportBean(BaseWorker worker) {
		this.worker = worker;
	}

	public ImportResult importFile(Path importFile) throws IOException {
		return importFile(importFile, null);
	}

	public ImportResult importFile(Path importFile, BankAccount contextAccount) throws IOException {
		if (!Files.isRegularFile(importFile)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_FILE_NOT_FOUND", importFile.toString()));
		}

		log.info("Starting money transfer CSV import. file={}", () -> fileName(importFile));
		log.debug("Money transfer CSV import path: {}", importFile);
		List<ImportCandidate> importCandidates = readImportCandidates(importFile, contextAccount);
		if (importCandidates.isEmpty()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_FILE_EMPTY"));
		}

		ImportResult result = persistCandidates(importCandidates);
		updateWorker(100, "UI_MONEYTRANSFER_IMPORT_PROGRESS_DONE", Integer.toString(result.importedCount()),
				Integer.toString(result.skippedDuplicateCount()));
		if (log.isInfoEnabled()) {
			log.info("Finished money transfer CSV import. file={}, imported={}, skippedDuplicates={}", fileName(importFile), result.importedCount(),
					result.skippedDuplicateCount());
		}
		return result;
	}

	private String fileName(Path path) {
		if (path == null)
			return null;
		Path fileName = path.getFileName();
		return fileName != null ? fileName.toString() : null;
	}

	private List<ImportCandidate> readImportCandidates(Path importFile, BankAccount contextAccount) throws IOException {
		updateWorker(0, "UI_MONEYTRANSFER_IMPORT_PROGRESS_READING");
		String csvContent = stripBom(Files.readString(importFile, StandardCharsets.UTF_8));
		try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).setTrim(true).get()
				.parse(new StringReader(csvContent))) {
			CsvHeaders headers = resolveHeaders(parser);
			List<CSVRecord> records = parser.getRecords();
			return records.stream().map(csvRecord -> {
				updateWorker(progress(10, 50, csvRecord.getRecordNumber(), records.size()), "UI_MONEYTRANSFER_IMPORT_PROGRESS_VALIDATING",
						Long.toString(csvRecord.getRecordNumber()), Integer.toString(records.size()));
				return toImportCandidate(parseRecord(csvRecord, headers), contextAccount);
			}).toList();
		}
	}

	private String stripBom(String csvContent) {
		if (csvContent != null && !csvContent.isEmpty() && csvContent.charAt(0) == '\uFEFF') {
			return csvContent.substring(1);
		}
		return csvContent;
	}

	private CsvHeaders resolveHeaders(CSVParser parser) {
		Map<String, String> headersByNormalizedName = new HashMap<>();
		parser.getHeaderMap().keySet().forEach(header -> headersByNormalizedName.put(normalizeHeader(header), header));
		ProtocolHeaders protocolHeaders = resolveProtocolHeaders(headersByNormalizedName);

		if (isGBankingExportFormat(headersByNormalizedName)) {
			return resolveGBankingExportHeaders(headersByNormalizedName, protocolHeaders);
		}
		if (!headersByNormalizedName.keySet().containsAll(REQUIRED_HEADERS)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_INVALID_HEADER"));
		}
		return new CsvHeaders(headersByNormalizedName.get(HEADER_SENDER_IBAN), headersByNormalizedName.get(HEADER_RECIPIENT_NAME),
				headersByNormalizedName.get(HEADER_RECIPIENT_IBAN), headersByNormalizedName.get(HEADER_RECIPIENT_BIC),
				headersByNormalizedName.get(HEADER_AMOUNT), headersByNormalizedName.get(HEADER_PURPOSE), headersByNormalizedName.get(HEADER_PURPOSE_CODE),
				null, protocolHeaders);
	}

	private boolean isGBankingExportFormat(Map<String, String> headersByNormalizedName) {
		return headerExists(headersByNormalizedName, ExportConstants.RECIPIENT_NAME)
				&& headerExists(headersByNormalizedName, ExportConstants.RECIPIENT_IBAN)
				&& headerExists(headersByNormalizedName, ExportConstants.AMOUNT);
	}

	private CsvHeaders resolveGBankingExportHeaders(Map<String, String> headersByNormalizedName, ProtocolHeaders protocolHeaders) {
		return new CsvHeaders(header(headersByNormalizedName, ExportConstants.IBAN), header(headersByNormalizedName, ExportConstants.RECIPIENT_NAME),
				header(headersByNormalizedName, ExportConstants.RECIPIENT_IBAN), header(headersByNormalizedName, ExportConstants.RECIPIENT_BIC),
				header(headersByNormalizedName, ExportConstants.AMOUNT), header(headersByNormalizedName, ExportConstants.PURPOSE),
				header(headersByNormalizedName, ExportConstants.PURPOSE_CODE), header(headersByNormalizedName, ExportConstants.ACCOUNT_NUMBER),
				protocolHeaders);
	}

	private ProtocolHeaders resolveProtocolHeaders(Map<String, String> headersByNormalizedName) {
		String status = header(headersByNormalizedName, ExportConstants.PROTOCOL_STATUS);
		String timeStart = header(headersByNormalizedName, ExportConstants.PROTOCOL_TIME_START);
		String timeFinish = header(headersByNormalizedName, ExportConstants.PROTOCOL_TIME_FINISH);
		String protocolText = header(headersByNormalizedName, ExportConstants.PROTOCOL_TEXT);
		boolean anyProtocolHeader = status != null || timeStart != null || timeFinish != null || protocolText != null;
		if (!anyProtocolHeader) {
			return null;
		}
		if (status == null || timeStart == null || timeFinish == null || protocolText == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_INVALID_HEADER"));
		}
		return new ProtocolHeaders(status, timeStart, timeFinish, protocolText);
	}

	private boolean headerExists(Map<String, String> headersByNormalizedName, ExportConstants header) {
		return headersByNormalizedName.containsKey(normalizeHeader(header.toString()));
	}

	private String header(Map<String, String> headersByNormalizedName, ExportConstants header) {
		return headersByNormalizedName.get(normalizeHeader(header.toString()));
	}

	private String normalizeHeader(String value) {
		return trimToNull(value) == null ? "" : value.replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
	}

	private ParsedCsvTransfer parseRecord(CSVRecord csvRecord, CsvHeaders headers) {
		String senderIban = readField(csvRecord, headers.senderIban());
		String senderAccountNumber = readField(csvRecord, headers.senderAccountNumber());
		requireSenderIdentifier(csvRecord, senderIban, senderAccountNumber);
		return new ParsedCsvTransfer(senderIban, senderAccountNumber, requireField(csvRecord, headers.recipientName(), HEADER_RECIPIENT_NAME),
				requireField(csvRecord, headers.recipientIban(), HEADER_RECIPIENT_IBAN), readField(csvRecord, headers.recipientBic()),
				parseAmount(csvRecord, headers.amount()), readField(csvRecord, headers.purpose()), readField(csvRecord, headers.purposeCode()),
				parseProtocols(csvRecord, headers.protocolHeaders()));
	}

	private void requireSenderIdentifier(CSVRecord csvRecord, String senderIban, String senderAccountNumber) {
		if (senderIban == null && senderAccountNumber == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_MISSING_FIELD", Long.toString(csvRecord.getRecordNumber()),
					HEADER_SENDER_IBAN));
		}
	}

	private String requireField(CSVRecord csvRecord, String header, String fieldName) {
		String value = readField(csvRecord, header);
		if (value == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_MISSING_FIELD", Long.toString(csvRecord.getRecordNumber()), fieldName));
		}
		return value;
	}

	private String readField(CSVRecord csvRecord, String header) {
		if (header == null) {
			return null;
		}
		if (!csvRecord.isSet(header)) {
			return null;
		}
		return trimToNull(csvRecord.get(header));
	}

	private BigDecimal parseAmount(CSVRecord csvRecord, String amountHeader) {
		String amount = requireField(csvRecord, amountHeader, HEADER_AMOUNT);
		try {
			return new BigDecimal(normalizeAmount(amount));
		} catch (NumberFormatException e) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_INVALID_AMOUNT", Long.toString(csvRecord.getRecordNumber()), amount));
		}
	}

	private MoneyTransferStatus parseProtocolStatus(String value, CSVRecord csvRecord) {
		for (MoneyTransferStatus status : MoneyTransferStatus.values()) {
			if (status.matches(value)) {
				return status;
			}
		}
		throw invalidFieldValue(csvRecord, ExportConstants.PROTOCOL_STATUS.toString(), value, null);
	}

	private List<ParsedProtocol> parseProtocols(CSVRecord csvRecord, ProtocolHeaders protocolHeaders) {
		if (protocolHeaders == null) {
			return List.of();
		}
		String status = readField(csvRecord, protocolHeaders.status());
		String timeStart = readField(csvRecord, protocolHeaders.timeStart());
		String timeFinish = readField(csvRecord, protocolHeaders.timeFinish());
		String protocolText = readField(csvRecord, protocolHeaders.protocolText());
		if (status == null && timeStart == null && timeFinish == null && protocolText == null) {
			return List.of();
		}
		return List.of(new ParsedProtocol(parseProtocolStatus(requireField(csvRecord, protocolHeaders.status(),
				ExportConstants.PROTOCOL_STATUS.toString()), csvRecord),
				parseLocalDateTime(requireField(csvRecord, protocolHeaders.timeStart(), ExportConstants.PROTOCOL_TIME_START.toString()), csvRecord,
						ExportConstants.PROTOCOL_TIME_START.toString()),
				parseOptionalLocalDateTime(timeFinish, csvRecord, ExportConstants.PROTOCOL_TIME_FINISH.toString()), protocolText));
	}

	private LocalDateTime parseOptionalLocalDateTime(String value, CSVRecord csvRecord, String fieldName) {
		return value == null ? null : parseLocalDateTime(value, csvRecord, fieldName);
	}

	private LocalDateTime parseLocalDateTime(String value, CSVRecord csvRecord, String fieldName) {
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException e) {
			throw invalidFieldValue(csvRecord, fieldName, value, e);
		}
	}

	private GBankingException invalidFieldValue(CSVRecord csvRecord, String fieldName, String value, Exception cause) {
		String message = getText("ERROR_MONEYTRANSFER_IMPORT_INVALID_FIELD_VALUE", Long.toString(csvRecord.getRecordNumber()), fieldName, value);
		return cause != null ? new GBankingException(message, cause) : new GBankingException(message);
	}

	private String normalizeAmount(String value) {
		String normalized = value.replace("\u00A0", "").replace(" ", "");
		int commaIndex = normalized.lastIndexOf(',');
		int dotIndex = normalized.lastIndexOf('.');
		if (commaIndex >= 0 && dotIndex >= 0) {
			return commaIndex > dotIndex ? normalized.replace(".", "").replace(',', '.') : normalized.replace(",", "");
		}
		return commaIndex >= 0 ? normalized.replace(',', '.') : normalized;
	}

	private ImportCandidate toImportCandidate(ParsedCsvTransfer row, BankAccount contextAccount) {
		BankAccount account = contextAccount != null ? validateContextAccount(row, contextAccount) : resolveAccount(row);
		return new ImportCandidate(account, row);
	}

	private BankAccount validateContextAccount(ParsedCsvTransfer row, BankAccount contextAccount) {
		if (contextAccount == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_NO_ACCOUNT_SELECTED"));
		}
		if (!matchesSenderAccount(contextAccount, row)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_MISMATCH", senderIdentifier(row)));
		}
		return contextAccount;
	}

	private BankAccount resolveAccount(ParsedCsvTransfer row) {
		List<BankAccount> matchingAccounts = dbController.getAll(BankAccount.class).stream().filter(account -> matchesSenderAccount(account, row))
				.toList();
		if (matchingAccounts.isEmpty()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_NOT_FOUND", senderIdentifier(row)));
		}
		if (matchingAccounts.size() > 1) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_ACCOUNT_AMBIGUOUS", senderIdentifier(row)));
		}
		return matchingAccounts.get(0);
	}

	private boolean matchesSenderAccount(BankAccount account, ParsedCsvTransfer row) {
		String normalizedSenderIban = normalizeAccountIdentifier(row.senderIban());
		String accountIban = normalizeAccountIdentifier(account.getIban());
		if (!normalizedSenderIban.isBlank() && !accountIban.isBlank()) {
			return accountIban.equals(normalizedSenderIban);
		}
		String accountNumber = normalizeAccountIdentifier(account.getNumber());
		if (!normalizedSenderIban.isBlank()) {
			return !accountNumber.isBlank() && normalizedSenderIban.endsWith(accountNumber);
		}
		String senderAccountNumber = normalizeAccountIdentifier(row.senderAccountNumber());
		return !accountNumber.isBlank() && accountNumber.equals(senderAccountNumber);
	}

	private String senderIdentifier(ParsedCsvTransfer row) {
		return row.senderIban() != null ? row.senderIban() : row.senderAccountNumber();
	}

	private String normalizeAccountIdentifier(String value) {
		return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
	}

	private ImportResult persistCandidates(List<ImportCandidate> candidates) {
		return dbController.executeInTransaction(() -> persistCandidatesInTransaction(candidates));
	}

	private ImportResult persistCandidatesInTransaction(List<ImportCandidate> candidates) {
		Map<TransferKey, MoneyTransfer> knownTransfers = loadKnownTransfers(candidates);

		int importedCount = 0;
		int skippedDuplicateCount = 0;
		for (int i = 0; i < candidates.size(); i++) {
			ImportCandidate candidate = candidates.get(i);
			updateWorker(progress(50, 99, i + 1l, candidates.size()), "UI_MONEYTRANSFER_IMPORT_PROGRESS_IMPORTING", Integer.toString(i + 1),
					Integer.toString(candidates.size()));
			TransferKey transferKey = toTransferKey(candidate);
			MoneyTransfer knownTransfer = knownTransfers.get(transferKey);
			if (knownTransfer != null) {
				if (!persistMissingProtocols(knownTransfer, candidate.row().protocols())) {
					skippedDuplicateCount++;
				}
				continue;
			}
			MoneyTransfer persistedTransfer = persistCandidate(candidate);
			knownTransfers.put(transferKey, persistedTransfer);
			importedCount++;
		}
		return new ImportResult(importedCount, skippedDuplicateCount);
	}

	private Map<TransferKey, MoneyTransfer> loadKnownTransfers(List<ImportCandidate> candidates) {
		Map<TransferKey, MoneyTransfer> knownTransfers = new HashMap<>();
		candidates.stream().map(candidate -> candidate.account().getId()).distinct()
				.flatMap(accountId -> dbController.getAllByParent(MoneyTransfer.class, accountId).stream())
				.forEach(transfer -> knownTransfers.putIfAbsent(toTransferKey(transfer), transfer));
		return knownTransfers;
	}

	private boolean persistMissingProtocols(MoneyTransfer moneyTransfer, List<ParsedProtocol> protocols) {
		if (protocols.isEmpty()) {
			return false;
		}
		Set<ProtocolKey> existingProtocols = dbController.getAllByParent(MoneyTransferProtocol.class, moneyTransfer.getId()).stream()
				.map(this::toProtocolKey)
				.collect(Collectors.toSet());
		boolean protocolPersisted = false;
		for (ParsedProtocol protocol : protocols) {
			if (existingProtocols.add(toProtocolKey(protocol))) {
				persistProtocol(moneyTransfer, protocol);
				protocolPersisted = true;
			}
		}
		return protocolPersisted;
	}

	private MoneyTransfer persistCandidate(ImportCandidate candidate) {
		Recipient recipient = persistRecipient(candidate.row());
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(candidate.account().getId());
		moneyTransfer.setOrderType(OrderType.TRANSFER);
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose(candidate.row().purpose());
		moneyTransfer.setPurposeCode(candidate.row().purposeCode());
		moneyTransfer.setAmount(candidate.row().amount());
		moneyTransfer.setExecutionDate(LocalDate.now(ZoneId.systemDefault()));
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.IMPORTED);
		MoneyTransfer persistedTransfer = dbController.insertOrUpdate(moneyTransfer);
		persistMissingProtocols(persistedTransfer, candidate.row().protocols());
		return persistedTransfer;
	}

	private void persistProtocol(MoneyTransfer moneyTransfer, ParsedProtocol parsedProtocol) {
		MoneyTransferProtocol protocol = new MoneyTransferProtocol();
		protocol.setMoneyTransferId(moneyTransfer.getId());
		protocol.setMoneytransferStatus(parsedProtocol.moneytransferStatus());
		protocol.setTimeStart(parsedProtocol.timeStart());
		protocol.setTimeFinish(parsedProtocol.timeFinish());
		protocol.setProtocolText(parsedProtocol.protocolText());
		dbController.insertOrUpdate(protocol);
	}

	private ProtocolKey toProtocolKey(MoneyTransferProtocol protocol) {
		return new ProtocolKey(protocol.getMoneytransferStatus(), protocol.getTimeStart(), protocol.getTimeFinish(),
				normalizeText(protocol.getProtocolText()));
	}

	private ProtocolKey toProtocolKey(ParsedProtocol protocol) {
		return new ProtocolKey(protocol.moneytransferStatus(), protocol.timeStart(), protocol.timeFinish(), normalizeText(protocol.protocolText()));
	}

	private Recipient persistRecipient(ParsedCsvTransfer row) {
		Recipient recipient = new Recipient(row.recipientName(), row.recipientIban(), row.recipientBic(), null, null, null, Source.MONEYTRANSFER);
		return dbController.resolveRecipient(recipient);
	}

	private TransferKey toTransferKey(ImportCandidate candidate) {
		ParsedCsvTransfer row = candidate.row();
		return new TransferKey(candidate.account().getId(), OrderType.TRANSFER, normalizeText(row.recipientName()),
				normalizeAccountIdentifier(row.recipientIban()), normalizeAccountIdentifier(row.recipientBic()), row.amount().stripTrailingZeros(),
				normalizeText(row.purpose()), normalizeText(row.purposeCode()));
	}

	private TransferKey toTransferKey(MoneyTransfer moneyTransfer) {
		Recipient recipient = moneyTransfer.getRecipient();
		return new TransferKey(moneyTransfer.getAccountId(), moneyTransfer.getOrderType(), normalizeText(recipient != null ? recipient.getName() : null),
				normalizeAccountIdentifier(recipient != null ? recipient.getIban() : null),
				normalizeAccountIdentifier(recipient != null ? recipient.getBic() : null), moneyTransfer.getAmount().stripTrailingZeros(),
				normalizeText(moneyTransfer.getPurpose()), normalizeText(moneyTransfer.getPurposeCode()));
	}

	private String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}

	private int progress(int start, int stop, long current, long total) {
		if (total <= 0) {
			return start;
		}
		return Math.min(stop, start + (int) Math.round((stop - start) * (current / (double) total)));
	}

	private void updateWorker(int progress, String messageKey, String... values) {
		if (worker == null) {
			return;
		}
		worker.setProcessingState(getText(messageKey, values));
		worker.setWorkerProgress(progress);
	}

	public record ImportResult(int importedCount, int skippedDuplicateCount) {
	}

	private record CsvHeaders(String senderIban, String recipientName, String recipientIban, String recipientBic, String amount, String purpose,
			String purposeCode, String senderAccountNumber, ProtocolHeaders protocolHeaders) {
	}

	private record ProtocolHeaders(String status, String timeStart, String timeFinish, String protocolText) {
	}

	private record ParsedCsvTransfer(String senderIban, String senderAccountNumber, String recipientName, String recipientIban, String recipientBic,
			BigDecimal amount, String purpose, String purposeCode, List<ParsedProtocol> protocols) {
	}

	private record ParsedProtocol(MoneyTransferStatus moneytransferStatus, LocalDateTime timeStart, LocalDateTime timeFinish, String protocolText) {
	}

	private record ImportCandidate(BankAccount account, ParsedCsvTransfer row) {
	}

	private record TransferKey(int accountId, OrderType orderType, String recipientName, String recipientIban, String recipientBic, BigDecimal amount,
			String purpose, String purposeCode) {
	}

	private record ProtocolKey(MoneyTransferStatus moneytransferStatus, LocalDateTime timeStart, LocalDateTime timeFinish, String protocolText) {
	}
}
