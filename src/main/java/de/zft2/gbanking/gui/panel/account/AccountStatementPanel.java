package de.zft2.gbanking.gui.panel.account;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

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
import de.zft2.gbanking.service.account.AccountStatementService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.concurrent.Task;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AccountStatementPanel extends DetailListPane implements BaseMessagesBean {

	private static final Logger log = LogManager.getLogger(AccountStatementPanel.class);

	private final AccountStatementDetailPanel detailPanel;
	private final AccountStatementListPanel listPanel;
	private final BankingCapabilityService bankingCapabilityService;
	private final AccountStatementService accountStatementService;

	private BankAccount currentAccount;

	public AccountStatementPanel() {
		this(ServiceRegistry.getService(AccountStatementService.class), ServiceRegistry.getService(BankingCapabilityService.class));
	}

	AccountStatementPanel(AccountStatementService accountStatementService, BankingCapabilityService bankingCapabilityService) {
		this.accountStatementService = accountStatementService;
		this.bankingCapabilityService = bankingCapabilityService;
		detailPanel = new AccountStatementDetailPanel(() -> retrieveStatements(), () -> acknowledgeStatements());
		listPanel = new AccountStatementListPanel(statement -> handleStatementSelection(statement), statement -> openStatement(statement));
		setDetailAndList(detailPanel, listPanel);
	}

	public void updateAccount(BankAccount account) {
		currentAccount = account;
		boolean supported = account != null && bankingCapabilityService.supportsAccountStatements(account);
		detailPanel.updateAccount(account, supported);
		reloadStatements();
	}

	public void clearAccount() {
		updateAccount(null);
	}

	private void reloadStatements() {
		List<AccountStatement> statements = currentAccount != null ? accountStatementService.listAccountStatements(currentAccount) : List.of();
		listPanel.updateModelStatements(statements);
		detailPanel.updateStatement(null);
	}

	private void handleStatementSelection(AccountStatement statement) {
		detailPanel.updateStatement(statement);
	}

	private void openStatement(AccountStatement statement) {
		if (statement == null || statement.file() == null || !Files.isRegularFile(statement.file())) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_FILE_MISSING", statement != null ? statement.fileName() : ""));
			return;
		}
		if (!Desktop.isDesktopSupported()) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_OPEN_UNSUPPORTED"));
			return;
		}

		Desktop desktop = Desktop.getDesktop();
		if (!desktop.isSupported(Desktop.Action.OPEN)) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_OPEN_UNSUPPORTED"));
			return;
		}
		try {
			Path fileToOpen = accountStatementService.prepareForOpening(statement);
			desktop.open(fileToOpen.toFile());
		} catch (IOException | RuntimeException e) {
			log.warn("Could not open account statement file {}", statement.fileName(), e);
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENT_OPEN_FAILED", statement.fileName()));
		}
	}

	private void retrieveStatements() {
		startStatementTask((account, pin) -> accountStatementService.retrieveAccountStatementsWithResult(account, pin),
				result -> handleRetrievalSuccess(result), "ALERT_ACCOUNT_STATEMENTS_RETRIEVAL_FAILED", "gbanking-hbci-account-statements");
	}

	private void acknowledgeStatements() {
		startStatementTask((account, pin) -> accountStatementService.acknowledgeAccountStatementsWithResult(account, pin),
				result -> handleAcknowledgementSuccess(result), "ALERT_ACCOUNT_STATEMENTS_ACKNOWLEDGE_FAILED",
				"gbanking-hbci-account-statement-receipts");
	}

	private <T> void startStatementTask(BiFunction<BankAccount, char[], T> work, Consumer<T> onSuccess, String failureKey, String actionName) {
		if (currentAccount == null) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}
		if (!bankingCapabilityService.supportsAccountStatements(currentAccount)) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_UNSUPPORTED", currentAccount.getAccountName()));
			return;
		}
		char[] pin = requestPin();
		if (pin == null || pin.length == 0) {
			return;
		}

		BankAccount account = currentAccount;
		detailPanel.setRetrievalRunning(true);
		Task<T> task = BackgroundActionCoordinator.newTask(() -> work.apply(account, pin));
		task.setOnSucceeded(event -> {
			detailPanel.setRetrievalRunning(false);
			reloadStatements();
			onSuccess.accept(task.getValue());
		});
		task.setOnFailed(event -> {
			finishWithClearedPin(pin);
			log.error("Account statement action {} failed", actionName, task.getException());
			showAlert(AlertType.WARNING, getText(failureKey));
		});
		task.setOnCancelled(event -> finishWithClearedPin(pin));
		BackgroundActionCoordinator.getInstance().start(task, actionName);
	}

	private void finishWithClearedPin(char[] pin) {
		Arrays.fill(pin, '\0');
		detailPanel.setRetrievalRunning(false);
	}

	private char[] requestPin() {
		PinAskDialog pinWindow = new PinAskDialog(getOwnerWindow());
		pinWindow.setBankInfo(currentAccount.getBlz(), currentAccount.getBankName());
		Stage pinDialog = pinWindow.createNewPinAskDialog();
		pinDialog.showAndWait();
		return pinWindow.getPin();
	}

	private void handleRetrievalSuccess(AccountStatementRetrievalResult result) {
		if (result == null || result.wrongPin()) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_WRONG_PIN"));
		} else if (!result.successful()) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_RETRIEVAL_FAILED"));
		} else {
			showAlert(AlertType.INFORMATION, getText("UI_INFO_ACCOUNT_STATEMENTS_RETRIEVED", Integer.toString(result.statements().size())));
		}
	}

	private void handleAcknowledgementSuccess(AccountStatementAcknowledgementResult result) {
		if (result == null || result.wrongPin()) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_WRONG_PIN"));
		} else if (!result.successful()) {
			showAlert(AlertType.WARNING, getText("ALERT_ACCOUNT_STATEMENTS_ACKNOWLEDGE_FAILED"));
		} else if (result.acknowledgedCount() == 0) {
			showAlert(AlertType.INFORMATION, getText("UI_INFO_ACCOUNT_STATEMENTS_NO_PENDING_ACKNOWLEDGEMENTS"));
		} else {
			showAlert(AlertType.INFORMATION, getText("UI_INFO_ACCOUNT_STATEMENTS_ACKNOWLEDGED", Integer.toString(result.acknowledgedCount())));
		}
	}

	private void showAlert(AlertType type, String text) {
		DialogWindowSupport.showAlert(getOwnerWindow(), type, text);
	}

	private Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
