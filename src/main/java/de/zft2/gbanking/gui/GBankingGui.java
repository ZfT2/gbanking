package de.zft2.gbanking.gui;

import static de.zft2.gbanking.gui.PinRequestCoordinator.clearPins;
import static de.zft2.gbanking.gui.PinRequestCoordinator.copyPin;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceMode;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceResult;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.tenant.TenantLoginDialog;
import de.zft2.gbanking.gui.dialog.tenant.TenantLoginDialog.BackupOperationResult;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.model.AccountTableModel;
import de.zft2.gbanking.gui.panel.about.AboutPanel;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.OpenActionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.OverviewBasePanel;
import de.zft2.gbanking.gui.panel.setting.SettingsDialog;
import de.zft2.gbanking.gui.progress.FileExportProgressBarPanel;
import de.zft2.gbanking.gui.progress.FileImportProgressBarPanel;
import de.zft2.gbanking.gui.progress.MoneyTransferCsvImportProgressBarPanel;
import de.zft2.gbanking.gui.util.FileChooserDirectorySupport;
import de.zft2.gbanking.logging.DiagnosticPackageService;
import de.zft2.gbanking.logging.LoggingSettings;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.GBankingBean;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.action.OpenActionsExecutionService;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ActionExecution;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ActionStatus;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ActionType;
import de.zft2.gbanking.service.action.OpenActionsExecutionService.ExecutionSummary;
import de.zft2.gbanking.service.action.OpenActionsSelection;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.update.PreparedUpdate;
import de.zft2.gbanking.update.UpdateProgressListener;
import de.zft2.gbanking.update.UpdateRelease;
import de.zft2.gbanking.update.UpdateService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class GBankingGui extends Application implements BaseGui {

	private static final Logger log = LogManager.getLogger(GBankingGui.class);

	static final List<String> HELP_DOCUMENTS = List.of("manual_de.html", "manual_en.html", "csv-booking-format.html",
			"csv-booking-format_en.html", "gbanking-doc.css");
	private static final double BYTES_PER_MEGABYTE = 1024d * 1024d;
	private static final double MAX_PROGRESS = 1d;
	private static final int UPDATE_PROGRESS_BAR_HEIGHT = 10;
	private static final int UPDATE_PROGRESS_BAR_WIDTH = 160;
	private static final int OPEN_ACTION_RESULT_COLUMNS = 90;
	private static final int OPEN_ACTION_RESULT_MIN_ROWS = 4;
	private static final int OPEN_ACTION_RESULT_MAX_ROWS = 18;
	private static final Duration BACKGROUND_ACTION_TIMEOUT = Duration.ofSeconds(30);
	static final PageContext START_PAGE = PageContext.ACCOUNTS_TRANSACTIONS;

	private final FileChooser fileChooser = new FileChooser();
	private final UpdateService updateService = new UpdateService();
	private final DiagnosticPackageService diagnosticPackageService = new DiagnosticPackageService();

	private final Map<String, String> optionsMap = new HashMap<>();

	private GBankingBean bean;
	private BankAccessService bankAccessService;

	private BorderPane root;
	private Label statusLabel;
	private ProgressBar updateProgressBar;
	private Label updateProgressLabel;
	private Label versionLabel;

	private TenantLoginDialog tenantLoginDialog;

	private Stage primaryStage;
	private boolean lifecycleTransitionInProgress;
	private String statusBeforeLifecycleTransition;

	@Override
	public void start(Stage stage) {
		this.primaryStage = stage;
		GBankingContext.setMoneyTransferTemplateHandler((booking, orderType) -> openMoneyTransferTemplate(booking, orderType));
		log.info("Starting GBanking application.");

		restoreOptions();

		tenantLoginDialog = new TenantLoginDialog(this);

		if (!tenantLoginDialog.loginTenant()) {
			Platform.exit();
			return;
		}

		initializeMainWindow(stage);
		log.info("GBanking started.");
	}

	@Override
	public void stop() {
		tenantLoginDialog.releaseTenantLock();
	}

	private void restoreOptions() {
		try {
			RestoreHandler.restoreOptions(getOptionsMap());
			EnvironmentOptions.applyRuntimeOptions(getOptionsMap());
			FileChooserDirectorySupport.initialize(key -> getOptionsMap().get(key),
					(key, value) -> getOptionsMap().put(key, value), () -> storeOptionsQuietly());
			GBankingContext.setOnlyOnlineAccountsVisible(Boolean.parseBoolean(getOptionsMap().get(RestoreHandler.ONLY_ONLINE_ACCOUNTS)));
			log.debug("GUI options restored. language={}, dataDirectory={}", () -> getOptionsMap().get(LANGUAGE),
					() -> getOptionsMap().get(EnvironmentOptions.DATA_DIRECTORY));
		} catch (IOException ioe) {
			log.error("IOException in restore: {}", ioe.getMessage());
		}
		GuiLayoutState.initialize(getOptionsMap());
	}

	private void initializeMainWindow(Stage stage) {
		root = new BorderPane();
		root.setPadding(new Insets(4));

		root.setTop(new GBankingMenuBar(this));

		statusLabel = new Label("Info");
		updateProgressBar = new ProgressBar(0d);
		updateProgressBar.setPrefWidth(UPDATE_PROGRESS_BAR_WIDTH);
		updateProgressBar.setMaxWidth(UPDATE_PROGRESS_BAR_WIDTH);
		updateProgressBar.setMaxHeight(UPDATE_PROGRESS_BAR_HEIGHT);
		updateProgressLabel = new Label();
		hideUpdateDownloadProgress();

		HBox statusBox = new HBox(10, statusLabel, updateProgressBar, updateProgressLabel);
		statusBox.setAlignment(Pos.CENTER_LEFT);

		BorderPane bottom = new BorderPane();
		bottom.setPadding(new Insets(4));
		bottom.setLeft(statusBox);

		versionLabel = new Label(getText("UI_DIALOG_ABOUT_VERSION", BuildInfo.getProgramVersion()) + " (JavaFX " + BuildInfo.getJavaFxVersion()
				+ "), running on Java " + BuildInfo.getJavaVersion() + ".");
		bottom.setRight(versionLabel);
		root.setBottom(bottom);

		Scene scene = new Scene(root, 1440, 900);
		scene.getAccelerators().put(KeyboardShortcuts.FIND, () -> handleShortcut(KeyboardShortcutDispatcher.Action.FIND));
		scene.getAccelerators().put(KeyboardShortcuts.SAVE, () -> handleShortcut(KeyboardShortcutDispatcher.Action.SAVE));
		scene.getAccelerators().put(KeyboardShortcuts.CANCEL, () -> handleShortcut(KeyboardShortcutDispatcher.Action.CANCEL));
		stage.setTitle("GBanking");
		stage.setScene(scene);
		GuiLayoutState.restoreWindow(stage);
		stage.setOnCloseRequest(event -> {
			event.consume();
			shutdownApplication();
		});

		scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
		DialogWindowSupport.applyApplicationIcons(stage);
		stage.show();

		initializeApplicationData();
	}

	private void initializeApplicationData() {
		finishMainWindowInitialization();
	}

	private void finishMainWindowInitialization() {
		LoggingSettings.ensureSettingsExist();
		LoggingSettings.applyLogLevels();
		activateOverview(START_PAGE);
		bean = GBankingContext.getBean();
		bean.setup();
		bankAccessService = GBankingContext.getBankAccessService();
		log.info("Main window initialization completed.");
	}

	OverviewBasePanel activateOverview(PageContext pageContext) {
		statusLabel.setText(pageContext.toString());
		log.info("Activating overview {}", pageContext.name());

		OverviewBasePanel panelToActivate = OverviewPanelFactory.retrievePanel(pageContext.name());

		panelToActivate.setDisable(false);
		root.setCenter(panelToActivate);
		panelToActivate.refreshOnShow();
		return panelToActivate;
	}

	boolean handleShortcut(KeyboardShortcutDispatcher.Action action) {
		OverviewBasePanel panel = getActiveOverview();
		boolean handled = panel != null && KeyboardShortcutDispatcher.dispatch(panel, action);
		if (!handled && action == KeyboardShortcutDispatcher.Action.REFRESH && statusLabel != null) {
			statusLabel.setText(getText("UI_STATUS_REFRESH_DURING_EDIT"));
		}
		return handled;
	}

	private OverviewBasePanel getActiveOverview() {
		return root != null && root.getCenter() instanceof OverviewBasePanel panel ? panel : null;
	}

	private void openMoneyTransferTemplate(Booking booking, OrderType orderType) {
		if (booking == null || orderType == null) {
			return;
		}

		BankAccount account = GBankingContext.getDbController().getById(BankAccount.class, booking.getAccountId());
		if (account == null) {
			showWarning(primaryStage, getText("ALERT_MONEYTRANSFER_TEMPLATE_ACCOUNT_MISSING"));
			return;
		}

		activateOverview(PageContext.ACCOUNTS_MONEYTRANSFERS);
		MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
		moneyTransferPanel.useBookingAsTemplate(account, booking, orderType);
	}

	List<BankAccount> getSelectedAccountsForAccountUpdate() {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		if (overviewPanel == null || overviewPanel.getAccountListPanel() == null) {
			return List.of();
		}
		return overviewPanel.getAccountListPanel().getModelAccount().getCheckedAccounts();
	}

	void refreshTransactionOverviews() {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		if (overviewPanel != null) {
			overviewPanel.refreshOnShow();
		}
		AllTransactionsOverviewPanel allTransactionsOverviewPanel = (AllTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ALL_TRANSACTIONS.name());
		if (allTransactionsOverviewPanel != null) {
			allTransactionsOverviewPanel.refreshOnShow();
		}
		refreshExistingOverview(PageContext.ANALYSIS);
		refreshExistingOverview(PageContext.CATEGORY_ANALYSIS);
	}

	void updateAccounts(PinAskDialog pinWindow) {
		log.info("Starting account update from bank.");

		List<BankAccount> checkedAccounts = getSelectedAccountsForAccountUpdate();
		AccountsTransactionsOverviewPanel overviewPanel = getAccountsTransactionsOverviewPanel();
		if (!validateAccountUpdateSelection(checkedAccounts)) {
			return;
		}

		Map<Integer, char[]> pinMap = new PinRequestCoordinator(pinWindow).requestPinsByBankAccess(checkedAccounts);
		if (pinMap.isEmpty()) {
			return;
		}

		Task<List<String>> updateTask = createAccountUpdateTask(checkedAccounts, pinMap);
		updateTask.setOnSucceeded(event -> handleAccountUpdateSuccess(updateTask, checkedAccounts, overviewPanel));
		updateTask.setOnFailed(event -> log.error("Error updating accounts", updateTask.getException()));
		updateTask.setOnCancelled(event -> clearPins(pinMap));
		startBackgroundTask(updateTask, "gbanking-hbci-update-accounts");
	}

	private AccountsTransactionsOverviewPanel getAccountsTransactionsOverviewPanel() {
		return (AccountsTransactionsOverviewPanel) OverviewPanelFactory.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
	}

	private boolean validateAccountUpdateSelection(List<BankAccount> checkedAccounts) {
		if (checkedAccounts.isEmpty()) {
			log.info("No accounts selected for account update.");
			showWarning(primaryStage, getText(ALERT_ACCOUNT_NO_SELECTION));
			return false;
		}

		log.info("Selected {} accounts for account update.", checkedAccounts.size());
		return validateConfiguredBankAccess(checkedAccounts) && validateSupportedAccountTransactions(checkedAccounts);
	}

	private boolean validateConfiguredBankAccess(List<BankAccount> checkedAccounts) {
		List<BankAccount> accountsWithoutBankAccess = checkedAccounts.stream()
				.filter(account -> !bankAccessService.hasConfiguredBankAccess(account)).toList();
		if (accountsWithoutBankAccess.isEmpty()) {
			return true;
		}

		showWarning(primaryStage, getText("ALERT_ACCOUNT_BANK_ACCESS_MISSING", formatAccountNames(accountsWithoutBankAccess)));
		return false;
	}

	private boolean validateSupportedAccountTransactions(List<BankAccount> checkedAccounts) {
		List<BankAccount> unsupportedAccounts = checkedAccounts.stream().filter(account -> !bean.supportsAccountTransactions(account)).toList();
		if (unsupportedAccounts.isEmpty()) {
			return true;
		}

		showWarning(primaryStage, getText("ALERT_ACCOUNT_TRANSACTIONS_UNSUPPORTED", formatAccountNames(unsupportedAccounts)));
		return false;
	}

	private Task<List<String>> createAccountUpdateTask(List<BankAccount> checkedAccounts, Map<Integer, char[]> pinMap) {
		return new Task<>() {
			@Override
			protected List<String> call() {
				return updateSelectedAccounts(checkedAccounts, pinMap);
			}
		};
	}

	private List<String> updateSelectedAccounts(List<BankAccount> checkedAccounts, Map<Integer, char[]> pinMap) {
		List<String> skippedBanks = new ArrayList<>();
		Set<Integer> blockedBankKeys = new HashSet<>();
		try {
			for (BankAccount bankAccount : checkedAccounts) {
				CancellationSupport.throwIfCancellationRequested();
				updateSelectedAccount(bankAccount, pinMap, skippedBanks, blockedBankKeys);
			}
			CancellationSupport.throwIfCancellationRequested();
			bean.postRetriveActions(checkedAccounts);
			return skippedBanks;
		} finally {
			clearPins(pinMap);
		}
	}

	private void updateSelectedAccount(BankAccount bankAccount, Map<Integer, char[]> pinMap, List<String> skippedBanks, Set<Integer> blockedBankKeys) {
		Integer bankKey = bankAccount.getBankAccessId();
		if (blockedBankKeys.contains(bankKey)) {
			log.info("Skipping account update for account id {} because a previous account of bank {} reported invalid PIN credentials.",
					bankAccount.getId(), bankKey);
			return;
		}

		log.info("Updating account from bank. accountId={}", bankAccount.getId());
		AccountTransactionRetrievalResult result = bean.retrieveAccountTransactionsWithResult(bankAccount, copyPin(pinMap.get(bankKey)));
		if (result.wrongPin()) {
			blockedBankKeys.add(bankKey);
			skippedBanks.add(formatBankLabel(bankAccount));
		}
	}

	private void handleAccountUpdateSuccess(Task<List<String>> updateTask, List<BankAccount> checkedAccounts,
			AccountsTransactionsOverviewPanel overviewPanel) {

		log.info("Finished account update from bank for {} accounts.", checkedAccounts.size());
		if (overviewPanel != null) {
			overviewPanel.refreshOnShow();
		}
		List<String> skippedBanks = updateTask.getValue();
		if (skippedBanks != null && !skippedBanks.isEmpty()) {
			showWarning(primaryStage, getText("ALERT_ACCOUNT_UPDATE_WRONG_PIN_SKIPPED", String.join(", ", skippedBanks)));
		}
	}

	void executeTransfers(PinAskDialog pinWindow) {
		log.info("Starting execution of open money transfer orders.");

		List<MoneyTransfer> moneytransferList = bean.retrieveOpenTransfers();
		log.info("Found {} executable transfer orders.", moneytransferList.size());
		if (moneytransferList.isEmpty()) {
			showWarning(primaryStage, getText("ALERT_MONEYTRANSFER_NO_EXECUTABLE_TRANSFERS"));
			return;
		}

		Map<Integer, BankAccount> accountMap = new LinkedHashMap<>();
		for (MoneyTransfer moneytransfer : moneytransferList) {
			int accountId = moneytransfer.getAccountId();
			if (!accountMap.containsKey(accountId)) {
				accountMap.put(accountId, bean.getAccountForOpenMoneytransfers(accountId));
			}
		}
		Map<Integer, char[]> pinMap = new PinRequestCoordinator(pinWindow).requestPinsByAccountId(accountMap);
		if (pinMap.isEmpty()) {
			return;
		}

		Task<Void> transferTask = new Task<>() {
			@Override
			protected Void call() {
				try {
					for (MoneyTransfer moneytransfer : moneytransferList) {
						CancellationSupport.throwIfCancellationRequested();
						int accountId = moneytransfer.getAccountId();
						log.debug("Executing money transfer id {} for account id {}", moneytransfer.getId(), accountId);
						bean.executeTransfer(moneytransfer, accountMap.get(accountId), copyPin(pinMap.get(accountId)));
					}
					return null;
				} finally {
					clearPins(pinMap);
				}
			}
		};
		transferTask.setOnSucceeded(event -> {
			log.info("Finished execution of {} money transfer orders.", moneytransferList.size());
			MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
					.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
			if (moneyTransferPanel != null) {
				moneyTransferPanel.refreshOnShow();
			}
		});
		transferTask.setOnFailed(event -> log.error("Error executing transfers", transferTask.getException()));
		transferTask.setOnCancelled(event -> clearPins(pinMap));
		startBackgroundTask(transferTask, "gbanking-hbci-execute-transfers");
	}

	void retrieveOrderInventory(PinAskDialog pinWindow, OrderType orderType) {
		log.info("Starting order inventory retrieval for {}.", orderType);

		List<BankAccount> selectedAccounts = getSelectedAccountsForOrderInventory();
		if (selectedAccounts.isEmpty()) {
			showWarning(primaryStage, getText(ALERT_ACCOUNT_NO_SELECTION));
			return;
		}

		List<BankAccount> accountsWithoutBankAccess = selectedAccounts.stream().filter(account -> !bankAccessService.hasConfiguredBankAccess(account)).toList();
		if (!accountsWithoutBankAccess.isEmpty()) {
			showWarning(primaryStage, getText("ALERT_ACCOUNT_BANK_ACCESS_MISSING_ORDERS", formatAccountNames(accountsWithoutBankAccess)));
			return;
		}

		List<BankAccount> unsupportedAccounts = selectedAccounts.stream().filter(account -> !bean.supportsOrderInventory(account, orderType)).toList();
		if (!unsupportedAccounts.isEmpty()) {
			showWarning(primaryStage, getText("ALERT_ACCOUNT_ORDER_INVENTORY_UNSUPPORTED", orderType.getPlural(), formatAccountNames(unsupportedAccounts)));
			return;
		}

		Map<BankAccount, char[]> pinMap = new PinRequestCoordinator(pinWindow).requestPinsByAccount(selectedAccounts);
		if (pinMap.isEmpty()) {
			return;
		}

		Task<Void> inventoryTask = new Task<>() {
			@Override
			protected Void call() {
				try {
					for (Entry<BankAccount, char[]> entry : pinMap.entrySet()) {
						CancellationSupport.throwIfCancellationRequested();
						log.info("Retrieving order inventory. accountId={}, type={}", entry.getKey().getId(), orderType);
						bean.retrieveMoneyTransferInventory(entry.getKey(), orderType, copyPin(entry.getValue()));
					}
					return null;
				} finally {
					clearPins(pinMap);
				}
			}
		};
		inventoryTask.setOnSucceeded(event -> {
			log.info("Finished order inventory retrieval for {} accounts, type={}.", selectedAccounts.size(), orderType);
			MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
					.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
			if (moneyTransferPanel != null) {
				moneyTransferPanel.showOrderType(orderType);
			}
		});
		inventoryTask.setOnFailed(event -> log.error("Error retrieving money transfer inventory", inventoryTask.getException()));
		inventoryTask.setOnCancelled(event -> clearPins(pinMap));
		startBackgroundTask(inventoryTask, "gbanking-hbci-retrieve-order-inventory");
	}

	List<BankAccount> getSelectedAccountsForOrderInventory() {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		if (overviewPanel != null && overviewPanel.getAccountListPanel() != null) {
			List<BankAccount> checkedAccounts = overviewPanel.getAccountListPanel().getModelAccount().getCheckedAccounts();
			if (!checkedAccounts.isEmpty()) {
				return checkedAccounts;
			}
		}
		MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
		if (moneyTransferPanel != null && moneyTransferPanel.getAccountListPanel() != null) {
			List<BankAccount> checkedAccounts = moneyTransferPanel.getAccountListPanel().getModelAccount().getCheckedAccounts();
			if (!checkedAccounts.isEmpty()) {
				return checkedAccounts;
			}
			if (moneyTransferPanel.getSelectedAccount() != null) {
				return List.of(moneyTransferPanel.getSelectedAccount());
			}
		}
		return List.of();
	}

	boolean isOpenActionsOverviewActive() {
		OverviewBasePanel activeOverview = getActiveOverview();
		return activeOverview != null && activeOverview.getPageContext() == PageContext.OPEN_ACTIONS;
	}

	boolean hasSelectedOpenActions() {
		OpenActionsOverviewPanel panel = getActiveOpenActionsOverview();
		return panel != null && !panel.getSelectedActions().isEmpty();
	}

	void executeSelectedOpenActions(PinAskDialog pinWindow) {
		OpenActionsOverviewPanel panel = getActiveOpenActionsOverview();
		if (panel == null) {
			return;
		}

		OpenActionsSelection selection = panel.getSelectedActions();
		if (selection.isEmpty()) {
			showWarning(primaryStage, getText("ALERT_OPEN_ACTIONS_NO_SELECTION"));
			return;
		}
		if (!validateSupportedAccountTransactions(selection.accountUpdates())) {
			return;
		}

		List<BankAccount> accounts = selection.accountsRequiringAuthentication();
		if (!validateConfiguredBankAccess(accounts)) {
			return;
		}

		Map<Integer, char[]> pinMap = new PinRequestCoordinator(pinWindow).requestPinsByBankAccess(accounts);
		if (pinMap.isEmpty()) {
			return;
		}

		AtomicReference<ExecutionSummary> latestSummary = new AtomicReference<>();
		Task<ExecutionSummary> executionTask = new Task<>() {
			@Override
			protected ExecutionSummary call() {
				try {
					return OpenActionsExecutionService.execute(bean, selection, account -> pinMap.get(account.getBankAccessId()),
							summary -> latestSummary.set(summary));
				} finally {
					clearPins(pinMap);
				}
			}
		};
		executionTask.setOnSucceeded(event -> handleOpenActionsExecutionResult(panel, selection, executionTask.getValue()));
		executionTask.setOnFailed(event -> {
			log.error("Error executing selected open actions", executionTask.getException());
			handleOpenActionsExecutionResult(panel, selection, latestSummary.get());
		});
		executionTask.setOnCancelled(event -> {
			clearPins(pinMap);
			if (!lifecycleTransitionInProgress) {
				handleOpenActionsExecutionResult(panel, selection, latestSummary.get());
			}
		});
		startBackgroundTask(executionTask, "gbanking-hbci-execute-selected-actions");
	}

	private OpenActionsOverviewPanel getActiveOpenActionsOverview() {
		OverviewBasePanel activeOverview = getActiveOverview();
		return activeOverview instanceof OpenActionsOverviewPanel panel ? panel : null;
	}

	private void refreshAfterOpenActionsExecution(OpenActionsOverviewPanel panel) {
		panel.refreshOnShow();
		refreshExistingOverview(PageContext.ACCOUNTS_TRANSACTIONS);
		refreshExistingOverview(PageContext.ALL_TRANSACTIONS);
		refreshExistingOverview(PageContext.ANALYSIS);
		refreshExistingOverview(PageContext.CATEGORY_ANALYSIS);
		refreshExistingOverview(PageContext.ACCOUNTS_MONEYTRANSFERS);
	}

	private void handleOpenActionsExecutionResult(OpenActionsOverviewPanel panel, OpenActionsSelection selection, ExecutionSummary summary) {
		if (lifecycleTransitionInProgress) {
			return;
		}
		refreshAfterOpenActionsExecution(panel);
		if (summary == null) {
			return;
		}

		TextArea details = new TextArea(formatOpenActionsExecutionDetails(selection, summary));
		details.setEditable(false);
		details.setPrefColumnCount(OPEN_ACTION_RESULT_COLUMNS);
		details.setPrefRowCount(Math.min(Math.max(summary.actionResults().size() + 1, OPEN_ACTION_RESULT_MIN_ROWS),
				OPEN_ACTION_RESULT_MAX_ROWS));
		Alert.AlertType alertType = summary.hasProblems() ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION;
		DialogWindowSupport.showAlert(primaryStage, alertType, getText("UI_DIALOG_OPEN_ACTIONS_RESULT_TITLE"),
				getText("UI_DIALOG_OPEN_ACTIONS_RESULT_SUMMARY", summary.successfulActions(), summary.failedActions(),
						summary.skippedActions(), summary.cancelledActions()), details);
	}

	private String formatOpenActionsExecutionDetails(OpenActionsSelection selection, ExecutionSummary summary) {
		Map<Integer, BankAccount> accountsById = new LinkedHashMap<>();
		for (BankAccount account : selection.accountsRequiringAuthentication()) {
			accountsById.put(account.getId(), account);
		}
		List<String> rows = new ArrayList<>();
		rows.add(getText("UI_DIALOG_OPEN_ACTIONS_RESULT_COLUMNS"));
		for (ActionExecution result : summary.actionResults()) {
			BankAccount account = accountsById.get(result.accountId());
			rows.add(getText("UI_DIALOG_OPEN_ACTIONS_RESULT_ROW", account != null ? formatBankLabel(account) : "?",
					account != null ? formatAccountName(account) : String.valueOf(result.accountId()), openActionTypeText(result.actionType()),
					openActionStatusText(result.status())));
		}
		return String.join(System.lineSeparator(), rows);
	}

	private String openActionTypeText(ActionType actionType) {
		String key = switch (actionType) {
			case ACCOUNT_UPDATE -> "ENUM_OPENACTIONTYPE_ACCOUNT_UPDATE";
			case TRANSFER -> "ENUM_OPENACTIONTYPE_TRANSFER";
			case SCHEDULED_TRANSFER -> "ENUM_OPENACTIONTYPE_SCHEDULED_TRANSFER";
			case STANDING_ORDER -> "ENUM_OPENACTIONTYPE_STANDING_ORDER";
			case SCHEDULED_TRANSFER_INVENTORY -> "ENUM_OPENACTIONTYPE_SCHEDULED_TRANSFER_INVENTORY";
			case STANDING_ORDER_INVENTORY -> "ENUM_OPENACTIONTYPE_STANDING_ORDER_INVENTORY";
			case ACCOUNT_STATEMENT -> "ENUM_OPENACTIONTYPE_ACCOUNT_STATEMENT";
			case ACCOUNT_STATEMENT_RECEIPT -> "ENUM_OPENACTIONTYPE_ACCOUNT_STATEMENT_RECEIPT";
		};
		return getText(key);
	}

	private String openActionStatusText(ActionStatus status) {
		String key = switch (status) {
			case SUCCESSFUL -> "ENUM_OPENACTIONSTATUS_SUCCESSFUL";
			case FAILED -> "ENUM_OPENACTIONSTATUS_FAILED";
			case SKIPPED_WRONG_PIN -> "ENUM_OPENACTIONSTATUS_SKIPPED_WRONG_PIN";
			case CANCELLED -> "ENUM_OPENACTIONSTATUS_CANCELLED";
		};
		return getText(key);
	}

	void showSettingsWindow() {
		log.info("Opening settings dialog.");
		SettingsDialog settingsDialog = new SettingsDialog(primaryStage, getOptionsMap(), () -> storeOptionsQuietly());
		Stage settingsWindow = settingsDialog.createWindow();
		settingsWindow.showAndWait();
		log.info("Settings dialog closed.");
	}

	boolean isOnlyOnlineAccountsVisible() {
		return GBankingContext.isOnlyOnlineAccountsVisible();
	}

	void setOnlyOnlineAccountsVisible(boolean onlyOnlineAccountsVisible) {
		GBankingContext.setOnlyOnlineAccountsVisible(onlyOnlineAccountsVisible);
		getOptionsMap().put(RestoreHandler.ONLY_ONLINE_ACCOUNTS, Boolean.toString(onlyOnlineAccountsVisible));
		storeOptionsQuietly();
		refreshAccountListOverviews();
	}

	private void refreshAccountListOverviews() {
		refreshExistingOverview(PageContext.ACCOUNTS_TRANSACTIONS);
		refreshExistingOverview(PageContext.ACCOUNTS_MONEYTRANSFERS);
		refreshExistingOverview(PageContext.CATEGORIES);
		refreshExistingOverview(PageContext.ALL_ACCOUNTS);
	}

	private void refreshExistingOverview(PageContext pageContext) {
		OverviewBasePanel panel = OverviewPanelFactory.findPanel(pageContext.name());
		if (panel != null) {
			panel.refreshOnShow();
		}
	}

	void checkForApplicationUpdates() {
		log.info("Checking for application updates.");
		hideUpdateDownloadProgress();
		if (!updateService.canInstallUpdates()) {
			showWarning(primaryStage, getText("UI_UPDATE_UNSUPPORTED_LAYOUT"));
			return;
		}

		Task<Optional<UpdateRelease>> updateCheckTask = new Task<>() {
			@Override
			protected Optional<UpdateRelease> call() throws Exception {
				updateMessage(getText("UI_UPDATE_CHECKING"));
				return updateService.findUpdate();
			}
		};
		bindStatus(updateCheckTask);
		updateCheckTask.setOnSucceeded(event -> handleUpdateCheckResult(updateCheckTask.getValue()));
		updateCheckTask.setOnFailed(event -> showUpdateFailure(updateCheckTask.getException()));
		startBackgroundTask(updateCheckTask, "gbanking-update-check", ActionScope.INDEPENDENT);
	}

	private void handleUpdateCheckResult(Optional<UpdateRelease> updateRelease) {
		if (updateRelease.isEmpty()) {
			log.info("No application update available.");
			showInfo(primaryStage, getText("UI_UPDATE_NO_UPDATE", BuildInfo.getProgramVersion()));
			return;
		}

		UpdateRelease release = updateRelease.get();
		log.info("Application update available. currentVersion={}, latestVersion={}", BuildInfo.getProgramVersion(), release.version());
		if (DialogWindowSupport.showConfirmation(primaryStage,
				getText("UI_UPDATE_AVAILABLE", release.version(), BuildInfo.getProgramVersion()), ButtonType.OK, ButtonType.CANCEL)) {
			installApplicationUpdate(release);
		}
	}

	private void installApplicationUpdate(UpdateRelease release) {
		Task<PreparedUpdate> installTask = new Task<>() {
			@Override
			protected PreparedUpdate call() throws Exception {
				CancellationSupport.throwIfCancellationRequested();
				updateMessage(getText("UI_UPDATE_PREPARING"));
				updateDownloadProgress(0L, release.applicationAsset().size());
				PreparedUpdate preparedUpdate = updateService.downloadAndPrepare(release, new UpdateProgressListener() {
					@Override
					public void onProgress(String message) {
						CancellationSupport.throwIfCancellationRequested();
						updateMessage(getText("UI_UPDATE_PREPARING"));
					}

					@Override
					public void onDownloadProgress(long downloadedBytes, long totalBytes) {
						CancellationSupport.throwIfCancellationRequested();
						long effectiveTotalBytes = totalBytes > 0 ? totalBytes : release.applicationAsset().size();
						if (effectiveTotalBytes > 0) {
							updateProgress(downloadedBytes, effectiveTotalBytes);
						} else {
							updateProgress(-1, 1);
						}
						updateDownloadProgress(downloadedBytes, effectiveTotalBytes);
					}
				});
				CancellationSupport.throwIfCancellationRequested();
				updateMessage(getText("UI_UPDATE_EXECUTING"));
				return preparedUpdate;
			}
		};
		bindStatus(installTask);
		installTask.setOnSucceeded(event -> launchPreparedUpdate(installTask.getValue()));
		installTask.setOnFailed(event -> showUpdateFailure(installTask.getException()));
		startBackgroundTask(installTask, "gbanking-update-install", ActionScope.INDEPENDENT);
	}

	private void launchPreparedUpdate(PreparedUpdate preparedUpdate) {
		try {
			hideUpdateDownloadProgress();
			if (statusLabel != null) {
				statusLabel.setText(getText("UI_UPDATE_EXECUTING"));
			}
			updateService.launchInstaller(preparedUpdate);
			shutdownApplicationForUpdate();
		} catch (Exception e) {
			showUpdateFailure(e);
		}
	}

	private void shutdownApplicationForUpdate() {
		log.info("Shutting down GBanking for application update.");
		beginLifecycleTransition(this::completeShutdown, false);
	}

	private void bindStatus(Task<?> task) {
		task.messageProperty().addListener((obs, oldMessage, newMessage) -> {
			if (statusLabel != null && newMessage != null && !newMessage.isBlank()) {
				statusLabel.setText(newMessage);
			}
		});
	}

	private void updateDownloadProgress(long downloadedBytes, long totalBytes) {
		runOnFxThread(() -> {
			if (updateProgressBar == null || updateProgressLabel == null) {
				return;
			}
			updateProgressBar.setVisible(true);
			updateProgressBar.setManaged(true);
			updateProgressLabel.setVisible(true);
			updateProgressLabel.setManaged(true);

			if (totalBytes > 0) {
				double progress = Math.min(MAX_PROGRESS, Math.max(0d, downloadedBytes / (double) totalBytes));
				updateProgressBar.setProgress(progress);
				updateProgressLabel.setText(getText("UI_UPDATE_DOWNLOAD_PROGRESS", formatMegabytes(downloadedBytes), formatMegabytes(totalBytes)));
			} else {
				updateProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
				updateProgressLabel.setText(getText("UI_UPDATE_DOWNLOAD_PROGRESS_UNKNOWN", formatMegabytes(downloadedBytes)));
			}
		});
	}

	private void hideUpdateDownloadProgress() {
		runOnFxThread(() -> {
			if (updateProgressBar == null || updateProgressLabel == null) {
				return;
			}
			updateProgressBar.setProgress(0d);
			updateProgressBar.setVisible(false);
			updateProgressBar.setManaged(false);
			updateProgressLabel.setText("");
			updateProgressLabel.setVisible(false);
			updateProgressLabel.setManaged(false);
		});
	}

	private String formatMegabytes(long bytes) {
		double megabytes = Math.max(0L, bytes) / BYTES_PER_MEGABYTE;
		return String.format(Messages.getLocale(), "%.1f", megabytes);
	}

	private void runOnFxThread(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}

	private void showUpdateFailure(Throwable throwable) {
		hideUpdateDownloadProgress();
		log.error("Application update failed", throwable);
		String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown";
		showWarning(primaryStage, getText("UI_UPDATE_ERROR", message));
	}

	void showAboutWindow() {
		AboutPanel aboutPanel = new AboutPanel(primaryStage);
		Stage aboutWindow = aboutPanel.createNewAboutWindow();
		aboutWindow.showAndWait();
	}

	void showManual() {
		try {
			getHostServices().showDocument(prepareManualDocument().toUri().toString());
		} catch (IOException e) {
			log.error("Could not open manual", e);
			showWarning(primaryStage, getText("ERROR_MANUAL_OPEN"));
		}
	}

	void openLogDirectory() {
		Path logDirectory = diagnosticPackageService.getLogDirectory();
		try {
			Files.createDirectories(logDirectory);
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				showWarning(primaryStage, getText("UI_DIAGNOSTIC_LOG_DIRECTORY_OPEN_UNSUPPORTED"));
				return;
			}
			Desktop.getDesktop().open(logDirectory.toFile());
		} catch (IOException | SecurityException exception) {
			log.warn("Could not open log directory", exception);
			showWarning(primaryStage, getText("UI_DIAGNOSTIC_LOG_DIRECTORY_OPEN_FAILED"));
		}
	}

	void createDiagnosticPackage() {
		FileChooser diagnosticFileChooser = new FileChooser();
		diagnosticFileChooser.setTitle(getText("UI_DIAGNOSTIC_PACKAGE_SAVE_TITLE"));
		diagnosticFileChooser.setInitialFileName(diagnosticPackageService.defaultFileName());
		diagnosticFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(getText("UI_DIAGNOSTIC_PACKAGE_FILE_TYPE"), "*.zip"));
		FileChooserDirectorySupport.configure(diagnosticFileChooser, EnvironmentOptions.DEFAULT_DIR_EXPORT);
		Path selectedFile = FileChooserDirectorySupport.remember(diagnosticFileChooser.showSaveDialog(primaryStage),
				EnvironmentOptions.DEFAULT_DIR_EXPORT);
		if (selectedFile == null) {
			return;
		}
		try {
			Path diagnosticPackage = diagnosticPackageService.createDiagnosticPackage(selectedFile);
			log.info("Created diagnostic package. file={}", () -> fileName(diagnosticPackage));
			showInfo(primaryStage, getText("UI_DIAGNOSTIC_PACKAGE_CREATED", fileName(diagnosticPackage)));
		} catch (IOException exception) {
			log.error("Could not create diagnostic package", exception);
			showWarning(primaryStage, getText("UI_DIAGNOSTIC_PACKAGE_FAILED"));
		}
	}

	private Path prepareManualDocument() throws IOException {
		Path docDirectory = Path.of(System.getProperty("java.io.tmpdir"), "gbanking-doc");
		Files.createDirectories(docDirectory);
		for (String document : HELP_DOCUMENTS) {
			copyHelpDocument(document, docDirectory.resolve(document));
		}
		String manualFile = "de".equals(Messages.getLocale().getLanguage()) ? "manual_de.html" : "manual_en.html";
		return docDirectory.resolve(manualFile);
	}

	private void copyHelpDocument(String document, Path target) throws IOException {
		try (InputStream inputStream = GBankingGui.class.getResourceAsStream("/doc/" + document)) {
			if (inputStream == null) {
				throw new IOException("Missing help document: " + document);
			}
			Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	void processBookingImport(ExportType importType) {
		Path importFile = chooseImportFile(importType.getFileType());
		if (importFile == null) {
			log.debug("Booking import cancelled. type={}", importType);
			return;
		}
		try {
			log.info("Starting booking import. type={}, file={}", () -> importType, () -> fileName(importFile));
			log.debug("Booking import path: {}", importFile);
			importFile(importFile, importType);
		} catch (Exception e) {
			log.error("Import failed. type={}, file={}", importType, fileName(importFile), e);
		}
	}

	void processMoneyTransferCsvImport() {
		Path importFile = chooseImportFile(FileType.CSV);
		if (importFile == null) {
			log.debug("Money transfer CSV import cancelled.");
			return;
		}
		try {
			log.info("Starting money transfer CSV import. file={}", () -> fileName(importFile));
			log.debug("Money transfer CSV import path: {}", importFile);
			importMoneyTransferCsvFile(importFile);
		} catch (Exception e) {
			log.error("Money transfer CSV import failed. file={}", fileName(importFile), e);
			showWarning(primaryStage, e.getMessage());
		}
	}

	void processCreditcardImport() {
		Optional<BankAccount> account = selectCreditcardImportAccount();
		if (account.isEmpty()) {
			return;
		}

		Path importFile = chooseImportFile(FileType.CSV);
		if (importFile == null) {
			log.debug("Credit card CSV import cancelled.");
			return;
		}

		try {
			if (log.isInfoEnabled()) {
				log.info("Starting credit card CSV import. file={}, accountId={}", fileName(importFile), account.get().getId());
			}
			log.debug("Credit card CSV import path: {}", importFile);
			importCreditcardFile(importFile, account.get());
		} catch (Exception exception) {
			log.error("Credit card CSV import failed. file={}", fileName(importFile), exception);
			showWarning(primaryStage, exception.getMessage());
		}
	}

	private Optional<BankAccount> selectCreditcardImportAccount() {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		BankAccount selectedAccount = overviewPanel.getSelectedAccount();
		if (CreditcardImportAccountSelector.isEligible(selectedAccount)) {
			boolean confirmed = DialogWindowSupport.showConfirmation(primaryStage, javafx.scene.control.Alert.AlertType.CONFIRMATION,
					getText("UI_MENU_FILE_IMPORT_CREDITCARD"), getText("UI_CREDITCARD_IMPORT_CONFIRM_HEADER"),
					getText("UI_CREDITCARD_IMPORT_CONFIRM_ACCOUNT", selectedAccount.getAccountName()), ButtonType.YES, ButtonType.CANCEL);
			return confirmed ? Optional.of(selectedAccount) : Optional.empty();
		}

		List<BankAccount> eligibleAccounts = CreditcardImportAccountSelector
				.eligibleAccounts(GBankingContext.getDbController().getAll(BankAccount.class));
		if (eligibleAccounts.isEmpty()) {
			showWarning(primaryStage, getText("ERROR_CREDITCARD_IMPORT_NO_ELIGIBLE_ACCOUNT"));
			return Optional.empty();
		}

		ChoiceDialog<BankAccount> dialog = new ChoiceDialog<>(eligibleAccounts.get(0), eligibleAccounts);
		dialog.initOwner(primaryStage);
		dialog.setTitle(getText("UI_MENU_FILE_IMPORT_CREDITCARD"));
		dialog.setHeaderText(getText("UI_CREDITCARD_IMPORT_SELECT_HEADER"));
		dialog.setContentText(getText("UI_CREDITCARD_IMPORT_SELECT_ACCOUNT"));
		return dialog.showAndWait();
	}

	private void importCreditcardFile(Path importFile, BankAccount account) {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		FileImportProgressBarPanel progressPanel = new FileImportProgressBarPanel(primaryStage, account, () -> overviewPanel.refreshOnShow());
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		progressPanel.startTask(importFile.toString(), ExportType.BOOKINGS_CREDITCARD_CSV, overviewPanel.getAccountListPanel());
		progressWindow.show();
	}

	private void importMoneyTransferCsvFile(Path importFile) {
		MoneyTransferCsvImportProgressBarPanel progressPanel = new MoneyTransferCsvImportProgressBarPanel(primaryStage, null, () -> {
			MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
					.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
			if (moneyTransferPanel != null) {
				moneyTransferPanel.refreshOnShow();
			}
		});
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		progressPanel.startTask(importFile.toString(), ExportType.MONEYTRANSFERS_CSV, overviewPanel != null ? overviewPanel.getAccountListPanel() : null);
		progressWindow.show();
	}

	void processExport(ExportType exportType) {
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		if (overviewPanel == null || overviewPanel.getAccountListPanel() == null) {
			return;
		}

		AccountTableModel modelAccount = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = modelAccount.getCheckedAccounts();

		if (exportType == ExportType.BOOKINGS_FP3) {
			checkedAccounts = resolveSingleAccountExport(checkedAccounts);
			if (checkedAccounts.isEmpty()) {
				showWarning(primaryStage, getText("ALERT_BOOKINGS_FP3_EXPORT_ACCOUNT_REQUIRED"));
				return;
			}
		}

		if (checkedAccounts.isEmpty()) {
			log.info("no Accounts selected, so using all!");
			checkedAccounts = modelAccount.getAccounts();
		}

		Path exportFile = chooseExportFile(exportType.getFileType());
		if (exportFile == null) {
			log.debug("Export cancelled. type={}", exportType);
			return;
		}

		try {
			int accountCount = checkedAccounts.size();
			log.info("Starting export. type={}, file={}, accounts={}", () -> exportType, () -> fileName(exportFile), () -> accountCount);
			log.debug("Export path: {}", exportFile);
			exportFile(exportFile, checkedAccounts, exportType);
		} catch (Exception e) {
			log.error("Export failed. type={}, file={}", exportType, fileName(exportFile), e);
		}
	}

	private List<BankAccount> resolveSingleAccountExport(List<BankAccount> checkedAccounts) {
		if (checkedAccounts.size() == 1) {
			return checkedAccounts;
		}
		if (!checkedAccounts.isEmpty()) {
			return List.of();
		}
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		BankAccount selectedAccount = overviewPanel.getAccountListPanel().getSelectedAccount();
		return selectedAccount != null ? List.of(selectedAccount) : List.of();
	}

	private Path chooseImportFile(FileType fileType) {
		FileChooserDirectorySupport.configure(fileChooser, EnvironmentOptions.DEFAULT_DIR_IMPORT);
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(fileType.getDescription(), fileType.getExtensionPatterns()));
		return FileChooserDirectorySupport.remember(fileChooser.showOpenDialog(primaryStage), EnvironmentOptions.DEFAULT_DIR_IMPORT);
	}

	private Path chooseExportFile(FileType fileType) {
		FileChooserDirectorySupport.configure(fileChooser, EnvironmentOptions.DEFAULT_DIR_EXPORT);
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(fileType.getDescription(), fileType.getExtensionPatterns()));
		return FileChooserDirectorySupport.remember(fileChooser.showSaveDialog(primaryStage), EnvironmentOptions.DEFAULT_DIR_EXPORT);
	}

	private String fileName(Path path) {
		if (path == null)
			return null;
		Path fileName = path.getFileName();
		return fileName != null ? fileName.toString() : null;
	}

	private void importFile(Path chosenFile, ExportType exportType) {
		FileImportProgressBarPanel progressPanel = new FileImportProgressBarPanel(primaryStage);
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		progressPanel.startTask(chosenFile.toString(), exportType, overviewPanel.getAccountListPanel());
		progressWindow.show();
	}

	private void exportFile(Path chosenFile, List<BankAccount> checkedAccounts, ExportType exportType) {
		FileExportProgressBarPanel progressPanel = new FileExportProgressBarPanel(primaryStage, checkedAccounts);

		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		AccountsTransactionsOverviewPanel overviewPanel = (AccountsTransactionsOverviewPanel) OverviewPanelFactory
				.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
		progressPanel.startTask(chosenFile.toString(), exportType, overviewPanel.getAccountListPanel());
		progressWindow.show();
	}

	void shutdownApplication() {
		log.info("Shutting down GBanking application.");
		beginLifecycleTransition(this::completeShutdown, true);
	}

	private void completeShutdown() {
		storeOptionsQuietly();
		if (!tenantLoginDialog.closeTenantDatabase()) {
			log.error("Tenant database could not be encrypted during shutdown");
			showWarning(primaryStage, getText("UI_ERROR_TENANT_DB_CLOSE"));
		}
		tenantLoginDialog.releaseTenantLock();
		BackgroundActionCoordinator.getInstance().stopAcceptingActions();
		Platform.exit();
	}

	void switchTenant() {
		log.info("Switching tenant.");
		beginLifecycleTransition(this::completeTenantSwitch, true);
	}

	void createTenantBackup() {
		log.info("Creating manual tenant backup.");
		beginLifecycleTransition(() -> completeTenantBackup(), true);
	}

	private void completeTenantBackup() {
		BackupOperationResult result = tenantLoginDialog.runBackupOperation(null);
		BackgroundActionCoordinator.getInstance().resume();
		finishLifecycleTransition();
		if (result.succeeded()) {
			showInfo(primaryStage, getText("UI_INFO_TENANT_BACKUP_CREATED", fileName(result.backupFile())));
		} else {
			String messageKey = result.integrityCheckFailed() ? "UI_ERROR_TENANT_BACKUP_INTEGRITY" : "UI_ERROR_TENANT_MANUAL_BACKUP";
			showWarning(primaryStage, getText(messageKey));
		}
	}

	void restoreTenantBackup() {
		Path backupFile = chooseTenantBackupFile();
		if (backupFile == null || !confirmTenantRestore(backupFile)) {
			return;
		}
		if (log.isInfoEnabled()) {
			log.info("Restoring tenant backup {}.", fileName(backupFile));
		}
		beginLifecycleTransition(() -> completeTenantRestore(backupFile), true);
	}

	private Path chooseTenantBackupFile() {
		Optional<Path> backupDirectory = tenantLoginDialog.getActiveBackupDirectory();
		if (backupDirectory.isEmpty()) {
			showWarning(primaryStage, getText("UI_ERROR_TENANT_RESTORE_NO_SESSION"));
			return null;
		}
		FileChooser backupFileChooser = new FileChooser();
		backupFileChooser.setTitle(getText("UI_TENANT_RESTORE_SELECT_TITLE"));
		if (Files.isDirectory(backupDirectory.get())) {
			backupFileChooser.setInitialDirectory(backupDirectory.get().toFile());
		}
		backupFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(getText("UI_TENANT_BACKUP_FILE_TYPE"), "*.gbbackup"));
		var selectedFile = backupFileChooser.showOpenDialog(primaryStage);
		return selectedFile != null ? selectedFile.toPath() : null;
	}

	private boolean confirmTenantRestore(Path backupFile) {
		ButtonType restoreButton = new ButtonType(getText("UI_BUTTON_RESTORE"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButton = new ButtonType(getText("UI_BUTTON_CANCEL"), ButtonBar.ButtonData.CANCEL_CLOSE);
		return DialogWindowSupport.showConfirmation(primaryStage, Alert.AlertType.WARNING, getText("UI_TENANT_RESTORE_CONFIRM_TITLE"),
				getText("UI_TENANT_RESTORE_CONFIRM_HEADER"), getText("UI_TENANT_RESTORE_CONFIRM_TEXT", fileName(backupFile)),
				restoreButton, cancelButton);
	}

	private void completeTenantRestore(Path backupFile) {
		BackupOperationResult result = tenantLoginDialog.runBackupOperation(backupFile);
		BackgroundActionCoordinator.getInstance().resume();
		finishLifecycleTransition();
		if (!result.succeeded()) {
			String messageKey = result.integrityCheckFailed() ? "UI_ERROR_TENANT_RESTORE_INTEGRITY" : "UI_ERROR_TENANT_RESTORE";
			showWarning(primaryStage, getText(messageKey));
			return;
		}

		InstituteLookupCache.clear();
		GBankingContext.resetServices();
		resetMainWindowState();
		initializeMainWindow(primaryStage);
		showInfo(primaryStage, getText("UI_INFO_TENANT_RESTORE_FINISHED", fileName(result.backupFile())));
		if (!result.cleanupComplete()) {
			showWarning(primaryStage, getText("UI_WARNING_TENANT_RESTORE_CLEANUP"));
		}
	}

	private void completeTenantSwitch() {
		GuiLayoutState.captureWindow(primaryStage);
		if (!tenantLoginDialog.closeTenantDatabase()) {
			log.error("Tenant database could not be encrypted before tenant switch");
			if (!tenantLoginDialog.reopenActiveTenant()) {
				throw new IllegalStateException(getText("UI_ERROR_TENANT_DB_REOPEN"));
			}
			throw new IllegalStateException(getText("UI_ERROR_TENANT_DB_CLOSE"));
		}
		InstituteLookupCache.clear();
		GBankingContext.resetServices();
		primaryStage.hide();
		BackgroundActionCoordinator.getInstance().resume();

		if (!tenantLoginDialog.loginTenant()) {
			if (tenantLoginDialog.reopenActiveTenant()) {
				primaryStage.show();
				finishLifecycleTransition();
				log.info("Tenant switch cancelled, previous tenant restored.");
			} else {
				BackgroundActionCoordinator.getInstance().stopAcceptingActions();
				Platform.exit();
			}
			return;
		}

		resetMainWindowState();
		GBankingContext.resetServices();
		initializeMainWindow(primaryStage);
		finishLifecycleTransition();
		log.info("Tenant switch completed.");
	}

	private void beginLifecycleTransition(Runnable transition, boolean promptForRunningActions) {
		if (lifecycleTransitionInProgress) {
			return;
		}
		lifecycleTransitionInProgress = true;
		statusBeforeLifecycleTransition = statusLabel != null ? statusLabel.getText() : null;
		setLifecycleControlsDisabled(true);
		setLifecycleStatus();

		BackgroundActionCoordinator coordinator = BackgroundActionCoordinator.getInstance();
		coordinator.quiesce(QuiesceMode.WAIT, Duration.ZERO).whenComplete((result, failure) -> runOnFxThread(() -> {
			if (failure != null) {
				handleLifecycleFailure(failure);
				return;
			}
			if (result.completed()) {
				runLifecycleTransition(transition);
				return;
			}
			if (!promptForRunningActions) {
				awaitBackgroundActions(transition, QuiesceMode.WAIT);
				return;
			}
			chooseQuiesceMode(result).ifPresentOrElse(mode -> awaitBackgroundActions(transition, mode), this::abortLifecycleTransition);
		}));
	}

	private void awaitBackgroundActions(Runnable transition, QuiesceMode mode) {
		BackgroundActionCoordinator.getInstance().quiesce(mode, BACKGROUND_ACTION_TIMEOUT)
				.whenComplete((result, failure) -> runOnFxThread(() -> handleQuiesceResult(transition, result, failure)));
	}

	private void handleQuiesceResult(Runnable transition, QuiesceResult result, Throwable failure) {
		if (failure != null) {
			handleLifecycleFailure(failure);
			return;
		}
		if (result.completed()) {
			runLifecycleTransition(transition);
			return;
		}
		chooseTimeoutMode(result).ifPresentOrElse(mode -> awaitBackgroundActions(transition, mode), this::abortLifecycleTransition);
	}

	private Optional<QuiesceMode> chooseQuiesceMode(QuiesceResult result) {
		return chooseQuiesceMode("UI_BACKGROUND_ACTIONS_HEADER", "UI_BACKGROUND_ACTIONS_QUESTION", result);
	}

	private Optional<QuiesceMode> chooseTimeoutMode(QuiesceResult result) {
		return chooseQuiesceMode("UI_BACKGROUND_ACTIONS_TIMEOUT_HEADER", "UI_BACKGROUND_ACTIONS_TIMEOUT_QUESTION", result);
	}

	private Optional<QuiesceMode> chooseQuiesceMode(String headerKey, String questionKey, QuiesceResult result) {
		ButtonType waitButton = new ButtonType(getText("UI_BUTTON_WAIT"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelActionsButton = new ButtonType(getText("UI_BUTTON_CANCEL_ACTIONS"), ButtonBar.ButtonData.OTHER);
		ButtonType abortButton = new ButtonType(getText("UI_BUTTON_ABORT_TRANSITION"), ButtonBar.ButtonData.CANCEL_CLOSE);
		Optional<ButtonType> choice = DialogWindowSupport.showChoice(primaryStage, javafx.scene.control.Alert.AlertType.CONFIRMATION,
				getText("UI_BACKGROUND_ACTIONS_TITLE"), getText(headerKey),
				getText(questionKey, String.join(", ", result.activeActionNames())), waitButton, cancelActionsButton, abortButton);
		if (choice.filter(waitButton::equals).isPresent()) {
			return Optional.of(QuiesceMode.WAIT);
		}
		if (choice.filter(cancelActionsButton::equals).isPresent()) {
			return Optional.of(QuiesceMode.CANCEL);
		}
		return Optional.empty();
	}

	private void runLifecycleTransition(Runnable transition) {
		try {
			transition.run();
		} catch (RuntimeException failure) {
			handleLifecycleFailure(failure);
		}
	}

	private void handleLifecycleFailure(Throwable failure) {
		log.error("Could not finish application lifecycle transition", failure);
		showWarning(primaryStage, getText("UI_BACKGROUND_ACTIONS_TRANSITION_FAILED"));
		abortLifecycleTransition();
	}

	private void abortLifecycleTransition() {
		BackgroundActionCoordinator.getInstance().resume();
		finishLifecycleTransition();
	}

	private void finishLifecycleTransition() {
		lifecycleTransitionInProgress = false;
		setLifecycleControlsDisabled(false);
		if (statusLabel != null && statusBeforeLifecycleTransition != null) {
			statusLabel.setText(statusBeforeLifecycleTransition);
		}
		statusBeforeLifecycleTransition = null;
	}

	private void setLifecycleControlsDisabled(boolean disabled) {
		if (root != null) {
			root.setDisable(disabled);
		}
	}

	private void setLifecycleStatus() {
		if (statusLabel != null) {
			statusLabel.setText(getText("UI_BACKGROUND_ACTIONS_WAITING"));
		}
	}

	private void resetMainWindowState() {
		OverviewPanelFactory.clear();
		bean = null;
		root = null;
		statusLabel = null;
		updateProgressBar = null;
		updateProgressLabel = null;
		versionLabel = null;
	}

	private String formatAccountNames(List<BankAccount> bankAccounts) {
		return String.join(", ", bankAccounts.stream().map(bankAccount -> formatAccountName(bankAccount)).toList());
	}

	private String formatAccountName(BankAccount bankAccount) {
		if (bankAccount.getAccountName() != null && !bankAccount.getAccountName().isBlank()) {
			return bankAccount.getAccountName();
		}
		if (bankAccount.getIban() != null && !bankAccount.getIban().isBlank()) {
			return bankAccount.getIban();
		}
		return bankAccount.getNumber() != null && !bankAccount.getNumber().isBlank() ? bankAccount.getNumber() : "?";
	}

	private String formatBankLabel(BankAccount bankAccount) {
		String bankName = bankAccount.getBankName();
		String blz = bankAccount.getBlz();
		if (bankName != null && !bankName.isBlank() && blz != null && !blz.isBlank()) {
			return bankName + " (" + blz + ")";
		}
		if (bankName != null && !bankName.isBlank()) {
			return bankName;
		}
		return blz != null && !blz.isBlank() ? blz : formatAccountName(bankAccount);
	}

	public void storeOptionsQuietly() {
		try {
			GuiLayoutState.captureWindow(primaryStage);
			RestoreHandler.storeOptions(getOptionsMap());
		} catch (Exception e) {
			log.error("IOException: {}", e.getMessage());
		}
	}

	public Map<String, String> getOptionsMap() {
		return optionsMap;
	}

	public Stage getStage() {
		return primaryStage;
	}
}
