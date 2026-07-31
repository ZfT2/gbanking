package de.zft2.gbanking.gui.progress;

import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.file.exp.FileExportTask;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.stage.Window;

public class FileExportProgressBarPanel extends BaseFileProgressBarPanel {

	private final List<BankAccount> exportAccountList;

	public FileExportProgressBarPanel(Window parent, List<BankAccount> checkedAccounts) {
		super(parent);
		this.exportAccountList = checkedAccounts;
	}

	@Override
	protected boolean keepDialogOpenOnSuccess() {
		return true;
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = new FileExportTask(fileName, exportType, exportAccountList);
		super.startTask(accountListPanel);
	}
}
