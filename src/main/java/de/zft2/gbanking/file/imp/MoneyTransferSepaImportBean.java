package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.BaseWorker;

public class MoneyTransferSepaImportBean extends MoneyTransferImportBean {

	private static final Logger log = LogManager.getLogger(MoneyTransferSepaImportBean.class);
	private static final String PAIN_001_NAMESPACE_MARKER = ":pain.001.";

	public MoneyTransferSepaImportBean() {
		this(null, MoneyTransferStatus.IMPORTED);
	}

	public MoneyTransferSepaImportBean(BaseWorker worker, MoneyTransferStatus importStatus) {
		super(worker, importStatus);
	}

	public ImportResult importFile(Path importFile) throws IOException, ParserConfigurationException, SAXException {
		return importFile(importFile, null);
	}

	public ImportResult importFile(Path importFile, BankAccount contextAccount)
			throws IOException, ParserConfigurationException, SAXException {
		if (!Files.isRegularFile(importFile)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_IMPORT_FILE_NOT_FOUND", importFile.toString()));
		}

		log.info("Starting SEPA PAIN import. file={}", () -> fileName(importFile));
		log.debug("SEPA PAIN import path: {}", importFile);
		ImportResult result = importTransfers(readTransfers(importFile), contextAccount);
		updateWorker(100, "UI_MONEYTRANSFER_SEPA_IMPORT_PROGRESS_DONE", Integer.toString(result.importedCount()),
				Integer.toString(result.skippedDuplicateCount()));
		if (log.isInfoEnabled()) {
			log.info("Finished SEPA PAIN import. file={}, imported={}, skippedDuplicates={}", fileName(importFile), result.importedCount(),
					result.skippedDuplicateCount());
		}
		return result;
	}

	private List<ParsedTransfer> readTransfers(Path importFile) throws ParserConfigurationException, SAXException, IOException {
		updateWorker(0, "UI_MONEYTRANSFER_SEPA_IMPORT_PROGRESS_READING");
		Document document = createDocumentBuilderFactory().newDocumentBuilder().parse(importFile.toFile());
		Element root = document.getDocumentElement();
		validateRoot(root);
		Element initiation = requiredChild(root, "CstmrCdtTrfInitn");
		List<Element> paymentInformation = children(initiation, "PmtInf");
		List<ParsedTransfer> transfers = new ArrayList<>();
		for (int i = 0; i < paymentInformation.size(); i++) {
			parsePaymentInformation(paymentInformation.get(i), transfers, i, paymentInformation.size());
		}
		return transfers;
	}

	private DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

	private void validateRoot(Element root) {
		String namespace = root.getNamespaceURI();
		if (!"Document".equals(localName(root)) || namespace == null || !namespace.contains(PAIN_001_NAMESPACE_MARKER)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_INVALID_DOCUMENT"));
		}
	}

	private void parsePaymentInformation(Element payment, List<ParsedTransfer> transfers, int paymentIndex, int paymentCount) {
		String paymentMethod = text(payment, "PmtMtd");
		if (paymentMethod != null && !"TRF".equals(paymentMethod)) {
			throw invalidValue("PmtMtd", paymentMethod);
		}
		String senderIban = requiredText(payment, "DbtrAcct", "Id", "IBAN");
		LocalDate executionDate = parseExecutionDate(requiredChild(payment, "ReqdExctnDt"));
		String paymentLocalInstrument = text(payment, "PmtTpInf", "LclInstrm", "Cd");
		String paymentPurposeCode = text(payment, "PmtTpInf", "CtgyPurp", "Cd");
		List<Element> transactions = children(payment, "CdtTrfTxInf");
		for (Element transaction : transactions) {
			transfers.add(parseTransaction(transaction, senderIban, executionDate, paymentLocalInstrument, paymentPurposeCode));
		}
		updateWorker(progress(10, 50, paymentIndex + 1L, paymentCount), "UI_MONEYTRANSFER_SEPA_IMPORT_PROGRESS_VALIDATING",
				Integer.toString(paymentIndex + 1), Integer.toString(paymentCount));
	}

	private ParsedTransfer parseTransaction(Element transaction, String senderIban, LocalDate executionDate, String paymentLocalInstrument,
			String paymentPurposeCode) {
		Element instructedAmount = requiredChild(requiredChild(transaction, "Amt"), "InstdAmt");
		BigDecimal amount = parseAmount(requiredText(instructedAmount));
		String currency = trimToNull(instructedAmount.getAttribute("Ccy"));
		if (!"EUR".equals(currency)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_UNSUPPORTED_CURRENCY", currency));
		}

		String localInstrument = firstNonBlank(text(transaction, "PmtTpInf", "LclInstrm", "Cd"), paymentLocalInstrument);
		String purposeCode = firstNonBlank(text(transaction, "Purp", "Cd"), text(transaction, "PmtTpInf", "CtgyPurp", "Cd"),
				paymentPurposeCode);
		String purpose = firstNonBlank(joinedText(transaction, "RmtInf", "Ustrd"),
				text(transaction, "RmtInf", "Strd", "CdtrRefInf", "Ref"));
		String recipientBic = firstNonBlank(text(transaction, "CdtrAgt", "FinInstnId", "BICFI"),
				text(transaction, "CdtrAgt", "FinInstnId", "BIC"));
		return new ParsedTransfer(senderIban, null, requiredText(transaction, "Cdtr", "Nm"),
				requiredText(transaction, "CdtrAcct", "Id", "IBAN"), recipientBic,
				text(transaction, "CdtrAgt", "FinInstnId", "Nm"), amount, currency, purpose, purposeCode,
				text(transaction, "PmtId", "EndToEndId"), resolveOrderType(localInstrument, executionDate), executionDate, List.of());
	}

	private OrderType resolveOrderType(String localInstrument, LocalDate executionDate) {
		if ("INST".equals(localInstrument)) {
			return OrderType.REALTIME_TRANSFER;
		}
		return executionDate.isAfter(LocalDate.now(ZoneId.systemDefault())) ? OrderType.SCHEDULED_TRANSFER : OrderType.TRANSFER;
	}

	private LocalDate parseExecutionDate(Element requestedExecutionDate) {
		String value = firstNonBlank(text(requestedExecutionDate, "Dt"), text(requestedExecutionDate, "DtTm"), directText(requestedExecutionDate));
		if (value == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_MISSING_FIELD", "ReqdExctnDt"));
		}
		try {
			return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
		} catch (DateTimeParseException | IndexOutOfBoundsException exception) {
			throw invalidValue("ReqdExctnDt", value, exception);
		}
	}

	private BigDecimal parseAmount(String value) {
		try {
			BigDecimal amount = new BigDecimal(value);
			if (amount.signum() <= 0) {
				throw new NumberFormatException("Amount must be positive");
			}
			return amount;
		} catch (NumberFormatException exception) {
			throw invalidValue("InstdAmt", value, exception);
		}
	}

	private Element requiredChild(Element parent, String childName) {
		Element child = child(parent, childName);
		if (child == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_MISSING_FIELD", childName));
		}
		return child;
	}

	private String requiredText(Element parent, String... path) {
		String value = text(parent, path);
		if (value == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_MISSING_FIELD", path[path.length - 1]));
		}
		return value;
	}

	private String requiredText(Element element) {
		String value = directText(element);
		if (value == null) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_SEPA_MISSING_FIELD", localName(element)));
		}
		return value;
	}

	private String text(Element parent, String... path) {
		Element current = parent;
		for (String pathElement : path) {
			current = child(current, pathElement);
			if (current == null) {
				return null;
			}
		}
		return directText(current);
	}

	private String joinedText(Element parent, String containerName, String valueName) {
		Element container = child(parent, containerName);
		if (container == null) {
			return null;
		}
		List<String> values = children(container, valueName).stream().map(element -> directText(element)).filter(value -> value != null).toList();
		return values.isEmpty() ? null : String.join(System.lineSeparator(), values);
	}

	private Element child(Element parent, String childName) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && childName.equals(localName(element))) {
				return element;
			}
		}
		return null;
	}

	private List<Element> children(Element parent, String childName) {
		List<Element> elements = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && childName.equals(localName(element))) {
				elements.add(element);
			}
		}
		return elements;
	}

	private String directText(Element element) {
		StringBuilder value = new StringBuilder();
		for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
				value.append(node.getNodeValue());
			}
		}
		return trimToNull(value.toString());
	}

	private String localName(Node node) {
		return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
	}

	private GBankingException invalidValue(String field, String value) {
		return invalidValue(field, value, null);
	}

	private GBankingException invalidValue(String field, String value, Exception cause) {
		String message = getText("ERROR_MONEYTRANSFER_SEPA_INVALID_VALUE", field, value);
		return cause == null ? new GBankingException(message) : new GBankingException(message, cause);
	}

}
