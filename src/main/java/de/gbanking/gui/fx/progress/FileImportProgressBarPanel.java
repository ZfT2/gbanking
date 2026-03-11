package de.gbanking.gui.fx.progress;

import de.gbanking.fileimport.fx.FileImportTask;
import de.gbanking.gui.fx.enu.FileType;
import de.gbanking.gui.fx.panel.account.AccountListPanel;
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
	}

	@Override
	public void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception {
		task = new FileImportTask(fileName);
		super.startTask(accountListPanel);
	}
}