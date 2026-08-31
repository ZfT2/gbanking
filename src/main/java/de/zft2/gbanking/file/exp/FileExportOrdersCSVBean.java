package de.zft2.gbanking.file.exp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.enu.LocalizedEnumValue;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.gui.BaseWorker;

public class FileExportOrdersCSVBean extends FileExportBean {

	private static Logger log = LogManager.getLogger(FileExportOrdersCSVBean.class);

	public FileExportOrdersCSVBean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) throws ExportException {

		boolean result = true;

		try {
			List<Object[]> csvLines = createCSVContent(accountList);
			saveToExportFile(csvLines, fileName);
			if (log.isInfoEnabled()) {
				log.info("Finished money transfer CSV export. file={}, accounts={}, records={}", fileNameOnly(fileName), accountList.size(),
						csvLines.size());
			}

		} catch (Exception e) {
			result = false;
			if (log.isErrorEnabled()) {
				log.error("Error exporting money transfer CSV file {}", fileNameOnly(fileName), e);
			}
			log.debug("Money transfer CSV export path: {}", fileName);
		}

		return result;
	}

	private List<Object[]> createCSVContent(List<BankAccount> accountList) {

		List<Object[]> csvLines = new ArrayList<>();

		for (BankAccount account : accountList) {
			for (MoneyTransfer moneytransfer : dbController.getAllByParent(MoneyTransfer.class, account.getId())) {
				Recipient recipient = moneytransfer.getRecipient();
				List<MoneyTransferProtocol> protocols = dbController.getAllByParent(MoneyTransferProtocol.class, moneytransfer.getId());
				if (protocols.isEmpty()) {
					csvLines.add(createRecordArray(account, moneytransfer, recipient, null));
				} else {
					protocols.forEach(protocol -> csvLines.add(createRecordArray(account, moneytransfer, recipient, protocol)));
				}
			}
		}

		return csvLines;
	}

	private Object[] createRecordArray(BankAccount account, MoneyTransfer moneytransfer, Recipient recipient, MoneyTransferProtocol protocol) {
		Recipient safeRecipient = recipient != null ? recipient : new Recipient();

		// @formatter:off
		return new Object[] {
				account.getAccountName(),
				account.getIban(),
				account.getNumber(),

				getTechnicalName(moneytransfer.getSource()),
				getTechnicalName(moneytransfer.getOrderType()),
				moneytransfer.getPurpose(),
				moneytransfer.getPurposeCode(),
				moneytransfer.getEndToEndId(),
				moneytransfer.getAmount(),
				moneytransfer.getExecutionDate(),
				getTechnicalName(moneytransfer.getMoneytransferStatus()),

				safeRecipient.getName(),
				safeRecipient.getIban(),
				safeRecipient.getBic(),
				safeRecipient.getAccountNumber(),
				safeRecipient.getBlz(),
				safeRecipient.getNote(),
				getTechnicalName(safeRecipient.getSource()),

				protocol != null ? getTechnicalName(protocol.getMoneytransferStatus()) : null,
				protocol != null ? protocol.getTimeStart() : null,
				protocol != null ? protocol.getTimeFinish() : null,
				protocol != null ? protocol.getBankOrderId() : null,
				protocol != null && protocol.getSepaOrderStatus() != null ? protocol.getSepaOrderStatus().name() : null,
				protocol != null && protocol.getSepaCancellationCode() != null ? protocol.getSepaCancellationCode().name() : null,
				protocol != null ? protocol.getProtocolText() : null
				};
		// @formatter:on
	}

	private String getTechnicalName(LocalizedEnumValue value) {
		return value != null ? value.getGermanName() : null;
	}

	private void saveToExportFile(List<Object[]> csvLines, String fileName) throws IOException {

		Builder builder = buildHeader();
		Path exportPath = prepareExportPath(fileName);
		try (BufferedWriter csvWriter = Files.newBufferedWriter(exportPath);
			CSVPrinter csvPrinter = new CSVPrinter(csvWriter, builder.get())) {
			for (Object[] recordArray : csvLines) {
				csvPrinter.printRecord(recordArray);
			}
			csvPrinter.flush();
		}
	}

	private static Builder buildHeader() {
		Builder builder = Builder.create(CSVFormat.DEFAULT).setDelimiter(';');

		builder.setHeader(ExportConstants.ACCOUNT.toString(), ExportConstants.IBAN.toString(), ExportConstants.ACCOUNT_NUMBER.toString(),
				ExportConstants.SOURCE_MONEYTRANSFER.toString(), ExportConstants.TYP.toString(), ExportConstants.PURPOSE.toString(),
				ExportConstants.PURPOSE_CODE.toString(), ExportConstants.END_TO_END_ID.toString(), ExportConstants.AMOUNT.toString(),
				ExportConstants.EXECUTION_DATE.toString(),
				ExportConstants.STATE.toString(), ExportConstants.RECIPIENT_NAME.toString(), ExportConstants.RECIPIENT_IBAN.toString(),
				ExportConstants.RECIPIENT_BIC.toString(), ExportConstants.RECIPIENT_ACCOUNT_NUMBER.toString(), ExportConstants.BLZ.toString(),
				ExportConstants.NOTICE.toString(), ExportConstants.RECIPIENT_SOURCE.toString(), ExportConstants.PROTOCOL_STATUS.toString(),
				ExportConstants.PROTOCOL_TIME_START.toString(), ExportConstants.PROTOCOL_TIME_FINISH.toString(),
				ExportConstants.PROTOCOL_BANK_ORDER_ID.toString(), ExportConstants.PROTOCOL_SEPA_ORDER_STATUS.toString(),
				ExportConstants.PROTOCOL_SEPA_CANCELLATION_CODE.toString(), ExportConstants.PROTOCOL_TEXT.toString());
		return builder;
	}

}
