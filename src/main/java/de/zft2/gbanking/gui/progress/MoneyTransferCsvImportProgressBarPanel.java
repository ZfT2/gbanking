package de.zft2.gbanking.gui.progress;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.file.imp.MoneyTransferCsvImportBean;
import de.zft2.gbanking.file.imp.MoneyTransferCsvImportTask;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Window;

public class MoneyTransferCsvImportProgressBarPanel extends BaseFileProgressBarPanel {

	private final BankAccount contextAccount;
	private final Runnable refreshAction;

	public MoneyTransferCsvImportProgressBarPanel(Window parent, BankAccount contextAccount, Runnable refreshAction) {
		super(parent);
		this.contextAccount = contextAccount;
		this.refreshAction = refreshAction;
	}

	@Override
	protected String getWindowTitle() {
		return getText("UI_MENU_FILE_IMPORT_MONEYTRANSFERS_CSV");
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = new MoneyTransferCsvImportTask(fileName, contextAccount);
		super.startTask(accountListPanel);
	}

	@Override
	protected void onTaskSucceeded() {
		if (refreshAction != null) {
			refreshAction.run();
		}
		if (task instanceof MoneyTransferCsvImportTask importTask) {
			addImportSummary(importTask.getImportResult());
		}
	}

	@Override
	protected void onTaskFailed(Throwable ex) {
		DialogWindowSupport.showAlert(parentWindow, Alert.AlertType.WARNING,
				ex != null && ex.getMessage() != null ? ex.getMessage() : getText("ERROR_GENERAL"));
	}

	@Override
	protected boolean keepDialogOpenOnSuccess() {
		return true;
	}

	private void addImportSummary(MoneyTransferCsvImportBean.ImportResult result) {
		if (result == null) {
			return;
		}
		contentBox.getChildren()
				.add(new Label(getText("UI_MONEYTRANSFER_IMPORT_SUMMARY", result.importedCount(), result.skippedDuplicateCount())));
	}
}
