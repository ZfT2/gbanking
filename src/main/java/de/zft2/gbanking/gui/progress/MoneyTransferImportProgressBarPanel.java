package de.zft2.gbanking.gui.progress;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.file.imp.MoneyTransferImportBean;
import de.zft2.gbanking.file.imp.MoneyTransferImportTask;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Window;

public class MoneyTransferImportProgressBarPanel extends BaseFileProgressBarPanel {

	private final BankAccount contextAccount;
	private final Runnable refreshAction;
	private final ExportType importType;
	private final MoneyTransferStatus importStatus;

	public MoneyTransferImportProgressBarPanel(Window parent, BankAccount contextAccount, Runnable refreshAction, ExportType importType,
			MoneyTransferStatus importStatus) {
		super(parent);
		this.contextAccount = contextAccount;
		this.refreshAction = refreshAction;
		this.importType = importType;
		this.importStatus = importStatus;
	}

	@Override
	protected String getWindowTitle() {
		return getText(importType == ExportType.MONEYTRANSFERS_SEPA_XML ? "UI_MENU_FILE_XML_SEPA" : "UI_MENU_FILE_CSV_MONEYPLEX");
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = new MoneyTransferImportTask(fileName, importType, contextAccount, importStatus);
		super.startTask(accountListPanel);
	}

	@Override
	protected void onTaskSucceeded() {
		if (refreshAction != null) {
			refreshAction.run();
		}
		if (task instanceof MoneyTransferImportTask importTask) {
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

	private void addImportSummary(MoneyTransferImportBean.ImportResult result) {
		if (result != null) {
			contentBox.getChildren()
					.add(new Label(getText("UI_MONEYTRANSFER_IMPORT_SUMMARY", result.importedCount(), result.skippedDuplicateCount())));
		}
	}
}
