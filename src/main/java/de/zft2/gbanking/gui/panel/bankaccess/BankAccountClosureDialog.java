package de.zft2.gbanking.gui.panel.bankaccess;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccountClosureService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public final class BankAccountClosureDialog implements BaseMessages {

	private BankAccountClosureDialog() {
	}

	public static boolean review(BankAccess access) {
		List<BankAccount> missing = ServiceRegistry.getService(BankAccountClosureService.class).findMissingAccounts(access);
		if (missing.isEmpty()) {
			return true;
		}
		if (Platform.isFxApplicationThread()) {
			return showDialog(access, missing);
		}
		FutureTask<Boolean> task = new FutureTask<>(() -> showDialog(access, missing));
		Platform.runLater(task);
		try {
			return task.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GBankingException("Account closure review interrupted.", exception);
		} catch (ExecutionException exception) {
			throw new GBankingException("Account closure review failed.", exception.getCause());
		}
	}

	private static boolean showDialog(BankAccess access, List<BankAccount> missingAccounts) {
		MissingBankAccountsPane missing = new MissingBankAccountsPane(missingAccounts);
		Stage dialog = DialogWindowSupport.createModalStage(null, "UI_BANK_ACCESS_DIALOG_ACCOUNTS_TITLE");
		List<BankAccount> returned = access.getReturnedAccounts() != null ? access.getReturnedAccounts() : List.of();
		TableView<BankAccount> found = new TableView<>(FXCollections.observableArrayList(returned));
		found.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		found.getColumns().setAll(List.of(
				TableColumnFactory.createTextColumn(BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_NAME"), account -> account.getAccountName(), 200, 240),
				TableColumnFactory.createTextColumn(BaseMessages.getTextStatic("UI_TABLE_IBAN"), account -> account.getIban(), 200, 240)));
		AtomicBoolean approved = new AtomicBoolean();
		Button save = new Button(BaseMessages.getTextStatic("UI_BUTTON_SAVE"));
		save.setOnAction(event -> {
			try {
				ServiceRegistry.getService(BankAccountClosureService.class).saveClosureDates(missing.getClosureDates());
				approved.set(true);
				dialog.close();
			} catch (GBankingException exception) {
				DialogWindowSupport.showAlert(dialog, AlertType.WARNING, exception.getMessage());
			}
		});
		Button cancel = new Button(BaseMessages.getTextStatic("UI_BUTTON_CANCEL"));
		cancel.setCancelButton(true);
		cancel.setOnAction(event -> dialog.close());
		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(
				new Label(BaseMessages.getTextStatic("UI_BANK_ACCESS_DIALOG_ACCOUNTS_LABEL")), found, missing,
				DialogWindowSupport.createButtonBar(save, cancel)), 900, 600));
		dialog.showAndWait();
		return approved.get();
	}
}
