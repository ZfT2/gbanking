package de.gbanking.gui.swing.progress;

import java.util.List;

import javax.swing.JFrame;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.fileexport.swing.FileExportTask;
import de.gbanking.gui.swing.enu.FileType;
import de.gbanking.gui.swing.panel.account.AccountListPanel;

public class FileExportProgressBarPanel extends BaseFileProgressBarPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -607262295683346394L;
	
	private List<BankAccount> exportAccountList;

	public FileExportProgressBarPanel(JFrame parent, List<BankAccount> checkedAccounts) {
		super(parent);
		this.exportAccountList = checkedAccounts;
	}

	@Override
	public void startTask(String fileName, FileType fileType, AccountListPanel accountListPanel) throws Exception {
		task = new FileExportTask(fileName, fileType, exportAccountList);
		super.startTask(accountListPanel);
	}

}