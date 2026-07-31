package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.enu.ExportType;

public class MoneyTransferCsvImportTask extends BaseFileTask {

	private static final Logger log = LogManager.getLogger(MoneyTransferCsvImportTask.class);

	private final BankAccount contextAccount;
	private MoneyTransferCsvImportBean.ImportResult importResult;

	public MoneyTransferCsvImportTask(String fileName, BankAccount contextAccount) {
		super(fileName);
		this.exportType = ExportType.MONEYTRANSFERS_CSV;
		this.contextAccount = contextAccount;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting money transfer CSV import task. file={}", () -> fileNameOnly());
		log.debug("Money transfer CSV import task path: {}", fileName);
		setWorkerProgress(0);
		importResult = new MoneyTransferCsvImportBean(this).importFile(Path.of(fileName), contextAccount);
		return null;
	}

	public MoneyTransferCsvImportBean.ImportResult getImportResult() {
		return importResult;
	}
}
