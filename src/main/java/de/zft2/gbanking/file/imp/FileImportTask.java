package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.file.imp.FileImportCSVBean.RejectedRow;
import de.zft2.gbanking.gui.enu.ExportType;

public class FileImportTask extends BaseFileTask {

	private static Logger log = LogManager.getLogger(FileImportTask.class);
	private String importSummaryText;
	private List<FileImportBean.ImportAccountStatistics> importStatistics;
	private List<RejectedRow> rejectedRows = List.of();
	private final BankAccount contextAccount;
	private final String csvDefinitionName;
	private final ImportedBankNameCorrectionHandler bankNameCorrectionHandler;

	public FileImportTask(String fileName) {
		this(fileName, ExportType.BOOKINGS_XML, null, null);
	}

	public FileImportTask(String fileName, ExportType exportType, BankAccount contextAccount) {
		this(fileName, exportType, contextAccount, null);
	}

	public FileImportTask(String fileName, ExportType exportType, BankAccount contextAccount, String csvDefinitionName) {
		this(fileName, exportType, contextAccount, csvDefinitionName, null);
	}

	public FileImportTask(String fileName, ExportType exportType, BankAccount contextAccount, String csvDefinitionName,
			ImportedBankNameCorrectionHandler bankNameCorrectionHandler) {
		super(fileName);
		this.exportType = exportType;
		this.contextAccount = contextAccount;
		this.csvDefinitionName = csvDefinitionName;
		this.bankNameCorrectionHandler = bankNameCorrectionHandler;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting booking file import. type={}, file={}", () -> exportType, this::fileNameOnly);
		log.debug("Booking file import path: {}", fileName);
		setWorkerProgress(0);
		try {
			switch (exportType) {
			case BOOKINGS_XML -> importBookingsXml();
			case BOOKINGS_CSV -> importBookingsCsv();
			case BOOKINGS_FP3 -> importBookingsFp3();
			case BOOKINGS_MT940 -> importBookingsMt940();
			default -> throw new GBankingException("Unknown import type: " + exportType);
			}
		} catch (CancellationException ignored) {
			cancel();
		}
		return null;
	}

	private void importBookingsXml() {
		FileImportBean fileImportBean = new FileImportBean(this, null, false, bankNameCorrectionHandler);
		fileImportBean.importFile(fileName);
		importSummaryText = fileImportBean.getImportSummaryText();
		importStatistics = fileImportBean.getImportStatistics();
	}

	private void importBookingsCsv() throws IOException {
		FileImportCSVBean fileImportBean = new FileImportCSVBean(this, contextAccount, csvDefinitionName, bankNameCorrectionHandler);
		fileImportBean.importFileToDatabase(fileName);
		importStatistics = fileImportBean.getImportStatistics();
		rejectedRows = fileImportBean.getRejectedRows();
	}

	private void importBookingsFp3() {
		FileImportBean fileImportBean = new FileImportBean(this, contextAccount, true, bankNameCorrectionHandler);
		fileImportBean.importFile(fileName);
		importSummaryText = fileImportBean.getImportSummaryText();
		importStatistics = fileImportBean.getImportStatistics();
	}

	private void importBookingsMt940() throws IOException {
		FileImportMT940Bean fileImportBean = new FileImportMT940Bean(this, contextAccount, bankNameCorrectionHandler);
		fileImportBean.importFileToDatabase(fileName);
		importStatistics = fileImportBean.getImportStatistics();
	}

	public String getImportSummaryText() {
		return importSummaryText;
	}

	public List<FileImportBean.ImportAccountStatistics> getImportStatistics() {
		return importStatistics != null ? List.copyOf(importStatistics) : null;
	}

	public List<RejectedRow> getRejectedRows() {
		return List.copyOf(rejectedRows);
	}

}
