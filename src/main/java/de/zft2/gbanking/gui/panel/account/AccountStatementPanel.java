package de.zft2.gbanking.gui.panel.account;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.service.account.AccountStatement;
import de.zft2.gbanking.service.account.AccountStatementAcknowledgementResult;
import de.zft2.gbanking.service.account.AccountStatementRetrievalResult;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AccountStatementPanel extends DetailListPane implements BaseMessagesBean {

	private static final Logger log = LogManager.getLogger(AccountStatementPanel.class);

	private final AccountStatementDetailPanel detailPanel;
	private final AccountStatementListPanel listPanel;

	private BankAccount currentAccount;

	public AccountStatementPanel() {
		detailPanel = new AccountStatementDetailPanel(() -> retrieveStatements(), () -> acknowledgeStatements());
		listPanel = new AccountStatementListPanel(statement -> handleStatementSelection(statement), statement -> openStatement(statement));
		setDetailAndList(detailPanel, listPanel);
	}

	public void updateAccount(BankAccount account) {
		currentAccount = account;
		boolean supported = account != null && bean.supportsAccountStatements(account);
		detailPanel.updateAccount(account, supported);
		reloadStatements();
	}

	public void clearAccount() {
		updateAccount(null);
	}

	private void reloadStatements() {
		List<AccountStatement> statements = currentAccount != null ? bean.getAccountStatements(currentAccount) : List.of();
		listPanel.updateModelStatements(statements);
		detailPanel.updateStatement(null);
	}

	private void handleStatementSelection(AccountStatement statement) {
		detailPanel.updateStatement(statement);
	}

	private void openStatement(AccountStatement statement) {
		if (statement == null || statement.file() == null || !Files.isRegularFile(statement.file())) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING,
					getText("ALERT_ACCOUNT_STATEMENT_FILE_MISSING", statement != null ? statement.fileName() : ""));
			return;
		}
		if (!Desktop.isDesktopSupported()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_OPEN_UNSUPPORTED"));
			return;
		}

		Desktop desktop = Desktop.getDesktop();
		if (!desktop.isSupported(Desktop.Action.OPEN)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_OPEN_UNSUPPORTED"));
			return;
		}
		try {
			Path fileToOpen = bean.prepareAccountStatementForOpening(statement);
			desktop.open(fileToOpen.toFile());
		} catch (IOException | RuntimeException e) {
			log.warn("Could not open account statement file {}", statement.fileName(), e);
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING,
					getText("ALERT_ACCOUNT_STATEMENT_OPEN_FAILED", statement.fileName()));
		}
	}

	private void retrieveStatements() {
		if (currentAccount == null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}
		if (!bean.supportsAccountStatements(currentAccount)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING,
					getText("ALERT_ACCOUNT_STATEMENTS_UNSUPPORTED", currentAccount.getAccountName()));
			return;
		}

		char[] pin = requestPin();
		if (pin == null || pin.length == 0) {
			return;
		}

		startRetrievalTask(currentAccount, pin);
	}

	private void acknowledgeStatements() {
		if (currentAccount == null) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}
		if (!bean.supportsAccountStatements(currentAccount)) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING,
					getText("ALERT_ACCOUNT_STATEMENTS_UNSUPPORTED", currentAccount.getAccountName()));
			return;
		}

		char[] pin = requestPin();
		if (pin == null || pin.length == 0) {
			return;
		}

		startAcknowledgementTask(currentAccount, pin);
	}

	private char[] requestPin() {
		PinAskDialog pinWindow = new PinAskDialog(getOwnerWindow());
		pinWindow.setBankInfo(currentAccount.getBlz(), currentAccount.getBankName());
		Stage pinDialog = pinWindow.createNewPinAskDialog();
		pinDialog.showAndWait();
		return pinWindow.getPin();
	}

	private void startRetrievalTask(BankAccount account, char[] pin) {
		detailPanel.setRetrievalRunning(true);
		Task<AccountStatementRetrievalResult> task = new Task<>() {
			@Override
			protected AccountStatementRetrievalResult call() {
				return bean.retrieveAccountStatementsWithResult(account, pin);
			}
		};
		task.setOnSucceeded(event -> handleRetrievalSuccess(task.getValue()));
		task.setOnFailed(event -> handleRetrievalFailure(task.getException(), pin));
		task.setOnCancelled(event -> {
			Arrays.fill(pin, '\0');
			detailPanel.setRetrievalRunning(false);
		});

		BackgroundActionCoordinator.getInstance().start(task, "gbanking-hbci-account-statements");
	}

	private void startAcknowledgementTask(BankAccount account, char[] pin) {
		detailPanel.setRetrievalRunning(true);
		Task<AccountStatementAcknowledgementResult> task = new Task<>() {
			@Override
			protected AccountStatementAcknowledgementResult call() {
				return bean.acknowledgeAccountStatementsWithResult(account, pin);
			}
		};
		task.setOnSucceeded(event -> handleAcknowledgementSuccess(task.getValue()));
		task.setOnFailed(event -> handleAcknowledgementFailure(task.getException(), pin));
		task.setOnCancelled(event -> {
			Arrays.fill(pin, '\0');
			detailPanel.setRetrievalRunning(false);
		});

		BackgroundActionCoordinator.getInstance().start(task, "gbanking-hbci-account-statement-receipts");
	}

	private void handleRetrievalSuccess(AccountStatementRetrievalResult result) {
		detailPanel.setRetrievalRunning(false);
		reloadStatements();
		if (result == null || result.wrongPin()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_WRONG_PIN"));
			return;
		}
		if (!result.successful()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_RETRIEVAL_FAILED"));
			return;
		}
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.INFORMATION,
				getText("UI_INFO_ACCOUNT_STATEMENTS_RETRIEVED", Integer.toString(result.statements().size())));
	}

	private void handleAcknowledgementSuccess(AccountStatementAcknowledgementResult result) {
		detailPanel.setRetrievalRunning(false);
		reloadStatements();
		if (result == null || result.wrongPin()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_WRONG_PIN"));
			return;
		}
		if (!result.successful()) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_ACKNOWLEDGE_FAILED"));
			return;
		}
		if (result.acknowledgedCount() == 0) {
			DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.INFORMATION,
					getText("UI_INFO_ACCOUNT_STATEMENTS_NO_PENDING_ACKNOWLEDGEMENTS"));
			return;
		}
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.INFORMATION,
				getText("UI_INFO_ACCOUNT_STATEMENTS_ACKNOWLEDGED", Integer.toString(result.acknowledgedCount())));
	}

	private void handleRetrievalFailure(Throwable exception, char[] pin) {
		Arrays.fill(pin, '\0');
		detailPanel.setRetrievalRunning(false);
		log.error("Error retrieving account statements", exception);
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_RETRIEVAL_FAILED"));
	}

	private void handleAcknowledgementFailure(Throwable exception, char[] pin) {
		Arrays.fill(pin, '\0');
		detailPanel.setRetrievalRunning(false);
		log.error("Error acknowledging account statement receipts", exception);
		DialogWindowSupport.showAlert(getOwnerWindow(), AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_ACKNOWLEDGE_FAILED"));
	}

	private Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
