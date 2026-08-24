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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.exp.FileExportBean.ExportConstants;
import de.zft2.gbanking.gui.BaseWorker;

public class MoneyTransferCsvImportBean extends MoneyTransferImportBean {

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

	public MoneyTransferCsvImportBean() {
		this(null, MoneyTransferStatus.IMPORTED);
	}

	public MoneyTransferCsvImportBean(BaseWorker worker) {
		this(worker, MoneyTransferStatus.IMPORTED);
	}

	public MoneyTransferCsvImportBean(BaseWorker worker, MoneyTransferStatus importStatus) {
		super(worker, importStatus);
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
		ImportResult result = importTransfers(readTransfers(importFile), contextAccount);
		updateWorker(100, "UI_MONEYTRANSFER_IMPORT_PROGRESS_DONE", Integer.toString(result.importedCount()),
				Integer.toString(result.skippedDuplicateCount()));
		if (log.isInfoEnabled()) {
			log.info("Finished money transfer CSV import. file={}, imported={}, skippedDuplicates={}", fileName(importFile), result.importedCount(),
					result.skippedDuplicateCount());
		}
		return result;
	}

	private List<ParsedTransfer> readTransfers(Path importFile) throws IOException {
		updateWorker(0, "UI_MONEYTRANSFER_IMPORT_PROGRESS_READING");
		String csvContent = stripBom(Files.readString(importFile, StandardCharsets.UTF_8));
		try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).setTrim(true).get()
				.parse(new StringReader(csvContent))) {
			CsvHeaders headers = resolveHeaders(parser);
			List<CSVRecord> records = parser.getRecords();
			return records.stream().map(csvRecord -> {
				updateWorker(progress(10, 50, csvRecord.getRecordNumber(), records.size()), "UI_MONEYTRANSFER_IMPORT_PROGRESS_VALIDATING",
						Long.toString(csvRecord.getRecordNumber()), Integer.toString(records.size()));
				return parseRecord(csvRecord, headers);
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
				null, null, protocolHeaders);
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
				header(headersByNormalizedName, ExportConstants.PURPOSE_CODE), header(headersByNormalizedName, ExportConstants.END_TO_END_ID),
				header(headersByNormalizedName, ExportConstants.ACCOUNT_NUMBER), protocolHeaders);
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

	private ParsedTransfer parseRecord(CSVRecord csvRecord, CsvHeaders headers) {
		String senderIban = readField(csvRecord, headers.senderIban());
		String senderAccountNumber = readField(csvRecord, headers.senderAccountNumber());
		requireSenderIdentifier(csvRecord, senderIban, senderAccountNumber);
		return new ParsedTransfer(senderIban, senderAccountNumber, requireField(csvRecord, headers.recipientName(), HEADER_RECIPIENT_NAME),
				requireField(csvRecord, headers.recipientIban(), HEADER_RECIPIENT_IBAN), readField(csvRecord, headers.recipientBic()), null,
				parseAmount(csvRecord, headers.amount()), "EUR", readField(csvRecord, headers.purpose()), readField(csvRecord, headers.purposeCode()),
				readField(csvRecord, headers.endToEndId()), OrderType.TRANSFER, LocalDate.now(ZoneId.systemDefault()),
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
		if (header == null || !csvRecord.isSet(header)) {
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

	private record CsvHeaders(String senderIban, String recipientName, String recipientIban, String recipientBic, String amount, String purpose,
			String purposeCode, String endToEndId, String senderAccountNumber, ProtocolHeaders protocolHeaders) {
	}

	private record ProtocolHeaders(String status, String timeStart, String timeFinish, String protocolText) {
	}

}
