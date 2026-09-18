package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.concurrent.ProgressReporter;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.BaseFileTask;
import de.zft2.gbanking.gui.enu.ExportType;

public class MoneyTransferImportTask extends BaseFileTask implements BaseMessages {

	private static final Logger log = LogManager.getLogger(MoneyTransferImportTask.class);

	private final List<Path> importFiles;
	private final MoneyTransferStatus importStatus;
	private MoneyTransferImportBean.ImportResult importResult;

	public MoneyTransferImportTask(List<Path> importFiles, ExportType importType, MoneyTransferStatus importStatus) {
		super(firstFile(importFiles).toString());
		this.importFiles = List.copyOf(importFiles);
		this.exportType = importType;
		this.importStatus = importStatus;
	}

	@Override
	public Void call() throws ParserConfigurationException, SAXException, IOException {
		log.info("Starting money transfer import task. type={}, files={}", exportType, importFiles.size());
		log.debug("Money transfer import task paths: {}", importFiles);
		setWorkerProgress(0);
		int importedCount = 0;
		int skippedDuplicateCount = 0;
		for (int index = 0; index < importFiles.size(); index++) {
			Path importFile = importFiles.get(index);
			ProgressReporter fileProgress = new FileProgressReporter(this, index, importFiles.size(), fileName(importFile));
			MoneyTransferImportBean.ImportResult result = importFile(importFile, fileProgress);
			importedCount += result.importedCount();
			skippedDuplicateCount += result.skippedDuplicateCount();
		}
		importResult = new MoneyTransferImportBean.ImportResult(importedCount, skippedDuplicateCount);
		return null;
	}

	private MoneyTransferImportBean.ImportResult importFile(Path importFile, ProgressReporter progressReporter)
			throws ParserConfigurationException, SAXException, IOException {
		return switch (exportType) {
		case MONEYTRANSFERS_CSV -> new MoneyTransferCsvImportBean(progressReporter, importStatus).importFile(importFile);
		case MONEYTRANSFERS_SEPA_XML -> new MoneyTransferSepaImportBean(progressReporter, importStatus).importFile(importFile);
		default -> throw new GBankingException("Unsupported money transfer import type: " + exportType);
		};
	}

	public MoneyTransferImportBean.ImportResult getImportResult() {
		return importResult;
	}

	private static Path firstFile(List<Path> importFiles) {
		if (importFiles == null || importFiles.isEmpty()) {
			throw new IllegalArgumentException("At least one money transfer import file is required");
		}
		return importFiles.get(0);
	}

	private static String fileName(Path file) {
		Path name = file.getFileName();
		return name != null ? name.toString() : file.toString();
	}

	private static final class FileProgressReporter implements ProgressReporter {

		private final MoneyTransferImportTask task;
		private final int fileIndex;
		private final int fileCount;
		private final String fileName;

		private FileProgressReporter(MoneyTransferImportTask task, int fileIndex, int fileCount, String fileName) {
			this.task = task;
			this.fileIndex = fileIndex;
			this.fileCount = fileCount;
			this.fileName = fileName;
		}

		@Override
		public void reportState(String state) {
			task.reportState(task.getText("UI_MONEYTRANSFER_IMPORT_PROGRESS_FILE", Integer.toString(fileIndex + 1),
					Integer.toString(fileCount), fileName, state));
		}

		@Override
		public void reportProgress(double progress) {
			double boundedProgress = Math.max(0, Math.min(100, progress));
			task.reportProgress((fileIndex + boundedProgress / 100) * 100 / fileCount);
		}

		@Override
		public void checkCancelled() {
			task.checkCancelled();
		}
	}
}
