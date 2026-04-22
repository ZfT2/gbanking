package de.zft2.gbanking.gui.progress;

import de.zft2.gbanking.file.imp.FileImportTask;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.stage.Window;

public class FileImportProgressBarPanel extends BaseFileProgressBarPanel {

	public FileImportProgressBarPanel(Window parent) {
		super(parent);
	}

	@Override
	protected void onTaskSucceeded() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
		}
		if (task instanceof FileImportTask importTask) {
			String summaryText = importTask.getImportSummaryText();
			if (summaryText != null && !summaryText.isBlank()) {
				taskOutput.appendText(System.lineSeparator() + System.lineSeparator() + summaryText);
			}
		}
	}

	@Override
	protected boolean keepDialogOpenOnSuccess() {
		return true;
	}

	@Override
	public void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception {
		task = new FileImportTask(fileName);
		super.startTask(accountListPanel);
	}
}
