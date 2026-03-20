package de.gbanking.gui.progress;

import java.util.List;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.file.exp.FileExportTask;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.panel.account.AccountListPanel;
import javafx.stage.Window;

public class FileExportProgressBarPanel extends BaseFileProgressBarPanel {

	private final List<BankAccount> exportAccountList;

	public FileExportProgressBarPanel(Window parent, List<BankAccount> checkedAccounts) {
		super(parent);
		this.exportAccountList = checkedAccounts;
	}

	@Override
	public void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception {
		task = new FileExportTask(fileName, fileType, exportAccountList);
		super.startTask(accountListPanel);
	}
}