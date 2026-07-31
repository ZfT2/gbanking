package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.enu.ExportType;

public class FileImportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileImportTask.class);
	private String importSummaryText;
	private List<FileImportBean.ImportAccountStatistics> importStatistics;
	private List<CreditcardCsvImportBean.RejectedRow> rejectedRows = List.of();
	private final BankAccount contextAccount;

	public FileImportTask(String fileName) {
		this(fileName, ExportType.BOOKINGS_XML, null);
	}

	public FileImportTask(String fileName, ExportType exportType, BankAccount contextAccount) {
		super(fileName);
		this.exportType = exportType;
		this.contextAccount = contextAccount;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting booking file import. type={}, file={}", () -> exportType, this::fileNameOnly);
		log.debug("Booking file import path: {}", fileName);
		setWorkerProgress(0);
		switch (exportType) {
		case BOOKINGS_XML -> importBookingsXml();
		case BOOKINGS_CSV -> importBookingsCsv();
		case BOOKINGS_CREDITCARD_CSV -> importCreditcardBookingsCsv();
		case BOOKINGS_FP3 -> importBookingsFp3();
		case BOOKINGS_MT940 -> importBookingsMt940();
		default -> throw new GBankingException("Unknown import type: " + exportType);
		}
		return null;
	}

	private void importBookingsXml() {
		FileImportBean fileImportBean = new FileImportBean(this);
		fileImportBean.importFile(fileName);
		importSummaryText = fileImportBean.getImportSummaryText();
		importStatistics = fileImportBean.getImportStatistics();
	}

	private void importBookingsCsv() throws IOException {
		FileImportCSVBean fileImportBean = new FileImportCSVBean(this, contextAccount);
		fileImportBean.importFileToDatabase(fileName);
		importStatistics = fileImportBean.getImportStatistics();
	}

	private void importCreditcardBookingsCsv() throws IOException {
		CreditcardCsvImportBean importBean = new CreditcardCsvImportBean(this, contextAccount);
		importBean.importFileToDatabase(fileName);
		importStatistics = importBean.getImportStatistics();
		rejectedRows = importBean.getRejectedRows();
	}

	private void importBookingsFp3() {
		FileImportBean fileImportBean = new FileImportBean(this, contextAccount, true);
		fileImportBean.importFile(fileName);
		importSummaryText = fileImportBean.getImportSummaryText();
		importStatistics = fileImportBean.getImportStatistics();
	}

	private void importBookingsMt940() throws IOException {
		FileImportMT940Bean fileImportBean = new FileImportMT940Bean(this, contextAccount);
		fileImportBean.importFileToDatabase(fileName);
		importStatistics = fileImportBean.getImportStatistics();
	}

	public String getImportSummaryText() {
		return importSummaryText;
	}

	public List<FileImportBean.ImportAccountStatistics> getImportStatistics() {
		return importStatistics != null ? List.copyOf(importStatistics) : null;
	}

	public List<CreditcardCsvImportBean.RejectedRow> getRejectedRows() {
		return List.copyOf(rejectedRows);
	}

}
