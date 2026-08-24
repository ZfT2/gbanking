package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.enu.ExportType;

public class MoneyTransferImportTask extends BaseFileTask {

	private static final Logger log = LogManager.getLogger(MoneyTransferImportTask.class);

	private final BankAccount contextAccount;
	private final MoneyTransferStatus importStatus;
	private MoneyTransferImportBean.ImportResult importResult;

	public MoneyTransferImportTask(String fileName, ExportType importType, BankAccount contextAccount, MoneyTransferStatus importStatus) {
		super(fileName);
		this.exportType = importType;
		this.contextAccount = contextAccount;
		this.importStatus = importStatus;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting money transfer import task. type={}, file={}", () -> exportType, () -> fileNameOnly());
		log.debug("Money transfer import task path: {}", fileName);
		setWorkerProgress(0);
		importResult = switch (exportType) {
		case MONEYTRANSFERS_CSV -> new MoneyTransferCsvImportBean(this, importStatus).importFile(Path.of(fileName), contextAccount);
		case MONEYTRANSFERS_SEPA_XML -> new MoneyTransferSepaImportBean(this, importStatus).importFile(Path.of(fileName), contextAccount);
		default -> throw new GBankingException("Unsupported money transfer import type: " + exportType);
		};
		return null;
	}

	public MoneyTransferImportBean.ImportResult getImportResult() {
		return importResult;
	}
}
