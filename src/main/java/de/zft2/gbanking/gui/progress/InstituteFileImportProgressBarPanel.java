package de.zft2.gbanking.gui.progress;

import de.zft2.gbanking.file.imp.institute.InstituteFileImport;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportTask;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.stage.Window;

public class InstituteFileImportProgressBarPanel extends BaseFileProgressBarPanel {

	private final Class<? extends InstituteFileImport> instituteFileImportType;

	public InstituteFileImportProgressBarPanel(Class<? extends InstituteFileImport> instituteFileImportType, Window parent) {
		super(parent);
		this.instituteFileImportType = instituteFileImportType;
	}

	@Override
	protected void onTaskSucceeded() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
		}
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = new InstituteFileImportTask(this.instituteFileImportType, ".", fileName, null);
		super.startTask(accountListPanel);
	}
}
