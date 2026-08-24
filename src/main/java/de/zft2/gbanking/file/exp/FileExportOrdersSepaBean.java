package de.zft2.gbanking.file.exp;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.gui.BaseWorker;

public class FileExportOrdersSepaBean extends FileExportBean {

	static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
	private static final Logger log = LogManager.getLogger(FileExportOrdersSepaBean.class);
	private static final Set<OrderType> SUPPORTED_ORDER_TYPES = EnumSet.of(OrderType.TRANSFER, OrderType.SCHEDULED_TRANSFER,
			OrderType.REALTIME_TRANSFER);
	private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	public FileExportOrdersSepaBean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) {
		try {
			ExportData exportData = collectExportData(accountList);
			if (exportData.groups().isEmpty()) {
				throw new ExportException(getText("ERROR_MONEYTRANSFER_SEPA_EXPORT_EMPTY"));
			}
			writeDocument(createDocument(exportData.groups()), prepareExportPath(fileName));
			String finalMessage = exportData.skippedCount() > 0 ? "UI_MONEYTRANSFER_SEPA_EXPORT_SKIPPED" : "UI_PROGRESS_FINISH";
			updateWorkerState(99, false, finalMessage, exportData.skippedCount());
			log.info("Finished SEPA PAIN export. file={}, records={}, skippedUnsupported={}", fileNameOnly(fileName), exportData.exportedCount(),
					exportData.skippedCount());
			return true;
		} catch (ExportException exception) {
			throw exception;
		} catch (Exception exception) {
			log.error("Error exporting SEPA PAIN file {}", fileNameOnly(fileName), exception);
			throw new ExportException(getText("ERROR_MONEYTRANSFER_SEPA_EXPORT_FAILED", exception.getMessage()));
		}
	}

	private ExportData collectExportData(List<BankAccount> accounts) {
		Map<PaymentGroupKey, PaymentGroup> groups = new LinkedHashMap<>();
		int skippedCount = 0;
		int exportedCount = 0;
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		for (BankAccount account : accounts) {
			for (MoneyTransfer transfer : dbController.getAllByParent(MoneyTransfer.class, account.getId())) {
				if (!SUPPORTED_ORDER_TYPES.contains(transfer.getOrderType())) {
					skippedCount++;
					continue;
				}
				LocalDate executionDate = transfer.getExecutionDate() != null ? transfer.getExecutionDate() : today;
				PaymentGroupKey key = new PaymentGroupKey(account.getId(), executionDate,
						transfer.getOrderType() == OrderType.REALTIME_TRANSFER);
				groups.computeIfAbsent(key, unused -> new PaymentGroup(account, executionDate, key.instant(), new ArrayList<>())).transfers()
						.add(transfer);
				exportedCount++;
			}
		}
		return new ExportData(List.copyOf(groups.values()), exportedCount, skippedCount);
	}

	private Document createDocument(List<PaymentGroup> groups) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document document = factory.newDocumentBuilder().newDocument();
		Element root = document.createElementNS(NAMESPACE, "Document");
		document.appendChild(root);
		Element initiation = append(root, "CstmrCdtTrfInitn");
		String runId = ID_TIME_FORMAT.format(OffsetDateTime.now());
		appendGroupHeader(initiation, groups, runId);
		for (int i = 0; i < groups.size(); i++) {
			appendPaymentInformation(initiation, groups.get(i), runId, i + 1);
		}
		return document;
	}

	private void appendGroupHeader(Element initiation, List<PaymentGroup> groups, String runId) {
		List<MoneyTransfer> transfers = groups.stream().flatMap(group -> group.transfers().stream()).toList();
		Element header = append(initiation, "GrpHdr");
		append(header, "MsgId", valueWithin("GBanking-" + runId, "MsgId", 35));
		append(header, "CreDtTm", OffsetDateTime.now().toString());
		append(header, "NbOfTxs", Integer.toString(transfers.size()));
		append(header, "CtrlSum", sum(transfers).toPlainString());
		append(append(header, "InitgPty"), "Nm", "GBanking");
	}

	private void appendPaymentInformation(Element initiation, PaymentGroup group, String runId, int groupIndex) {
		BankAccount account = group.account();
		Element payment = append(initiation, "PmtInf");
		append(payment, "PmtInfId", valueWithin("GBanking-" + runId + "-" + groupIndex, "PmtInfId", 35));
		append(payment, "PmtMtd", "TRF");
		append(payment, "NbOfTxs", Integer.toString(group.transfers().size()));
		append(payment, "CtrlSum", sum(group.transfers()).toPlainString());
		Element paymentType = append(payment, "PmtTpInf");
		append(append(paymentType, "SvcLvl"), "Cd", "SEPA");
		if (group.instant()) {
			append(append(paymentType, "LclInstrm"), "Cd", "INST");
		}
		append(append(payment, "ReqdExctnDt"), "Dt", group.executionDate().toString());
		append(append(payment, "Dbtr"), "Nm", requiredWithin(debtorName(account), "Dbtr/Nm", 140));
		append(append(append(payment, "DbtrAcct"), "Id"), "IBAN", requiredWithin(account.getIban(), "DbtrAcct/IBAN", 34));
		appendDebtorAgent(payment, account.getBic());
		append(payment, "ChrgBr", "SLEV");
		for (MoneyTransfer transfer : group.transfers()) {
			appendTransaction(payment, transfer);
		}
	}

	private void appendDebtorAgent(Element payment, String bic) {
		Element financialInstitution = append(append(payment, "DbtrAgt"), "FinInstnId");
		String normalizedBic = trimToNull(bic);
		if (normalizedBic != null) {
			append(financialInstitution, "BICFI", valueWithin(normalizedBic, "DbtrAgt/BICFI", 11));
		} else {
			append(append(financialInstitution, "Othr"), "Id", "NOTPROVIDED");
		}
	}

	private void appendTransaction(Element payment, MoneyTransfer transfer) {
		Recipient recipient = transfer.getRecipient();
		if (recipient == null) {
			throw new ExportException(getText("ERROR_MONEYTRANSFER_SEPA_EXPORT_MISSING_FIELD", "Cdtr"));
		}
		Element transaction = append(payment, "CdtTrfTxInf");
		append(append(transaction, "PmtId"), "EndToEndId",
				valueWithin(firstNonBlank(transfer.getEndToEndId(), "NOTPROVIDED"), "EndToEndId", 35));
		Element amount = append(append(transaction, "Amt"), "InstdAmt", transfer.getAmount().toPlainString());
		amount.setAttribute("Ccy", "EUR");
		String recipientBic = trimToNull(recipient.getBic());
		if (recipientBic != null) {
			append(append(append(transaction, "CdtrAgt"), "FinInstnId"), "BICFI", valueWithin(recipientBic, "CdtrAgt/BICFI", 11));
		}
		append(append(transaction, "Cdtr"), "Nm", requiredWithin(recipient.getName(), "Cdtr/Nm", 140));
		append(append(append(transaction, "CdtrAcct"), "Id"), "IBAN", requiredWithin(recipient.getIban(), "CdtrAcct/IBAN", 34));
		String purposeCode = trimToNull(transfer.getPurposeCode());
		if (purposeCode != null) {
			append(append(transaction, "Purp"), "Cd", valueWithin(purposeCode, "Purp/Cd", 4));
		}
		String purpose = trimToNull(transfer.getPurpose());
		if (purpose != null) {
			append(append(transaction, "RmtInf"), "Ustrd", valueWithin(purpose, "RmtInf/Ustrd", 140));
		}
	}

	private void writeDocument(Document document, Path exportPath) throws Exception {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		try (OutputStream output = Files.newOutputStream(exportPath)) {
			transformer.transform(new DOMSource(document), new StreamResult(output));
		}
	}

	private Element append(Element parent, String name) {
		Element child = parent.getOwnerDocument().createElementNS(NAMESPACE, name);
		parent.appendChild(child);
		return child;
	}

	private Element append(Element parent, String name, String value) {
		Element child = append(parent, name);
		child.setTextContent(value);
		return child;
	}

	private BigDecimal sum(List<MoneyTransfer> transfers) {
		BigDecimal sum = BigDecimal.ZERO;
		for (MoneyTransfer transfer : transfers) {
			sum = sum.add(transfer.getAmount());
		}
		return sum;
	}

	private String debtorName(BankAccount account) {
		return firstNonBlank(account.getOwnerName(), account.getAccountName());
	}

	private String requiredWithin(String value, String field, int maxLength) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			throw new ExportException(getText("ERROR_MONEYTRANSFER_SEPA_EXPORT_MISSING_FIELD", field));
		}
		return valueWithin(normalized, field, maxLength);
	}

	private String valueWithin(String value, String field, int maxLength) {
		if (value.length() > maxLength) {
			throw new ExportException(getText("ERROR_MONEYTRANSFER_SEPA_EXPORT_VALUE_TOO_LONG", field, Integer.toString(maxLength)));
		}
		return value;
	}

	private record PaymentGroupKey(int accountId, LocalDate executionDate, boolean instant) {
	}

	private record PaymentGroup(BankAccount account, LocalDate executionDate, boolean instant, List<MoneyTransfer> transfers) {
	}

	private record ExportData(List<PaymentGroup> groups, int exportedCount, int skippedCount) {
	}
}
