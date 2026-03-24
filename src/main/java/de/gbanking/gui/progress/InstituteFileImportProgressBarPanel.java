package de.gbanking.gui.progress;

import java.nio.charset.StandardCharsets;

import de.gbanking.file.imp.institute.InstituteFileImportTask;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.panel.account.AccountListPanel;
import javafx.stage.Window;

public class InstituteFileImportProgressBarPanel extends BaseFileProgressBarPanel {

	public InstituteFileImportProgressBarPanel(Window parent) {
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
		task = new InstituteFileImportTask(".", fileName, StandardCharsets.ISO_8859_1);
		super.startTask(accountListPanel);
	}
}
