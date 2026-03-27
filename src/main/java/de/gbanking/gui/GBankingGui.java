package de.gbanking.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.GBankingBean;
import de.gbanking.SystemInfo;
import de.gbanking.cache.InstituteLookupCache;
import de.gbanking.db.DBController;
import de.gbanking.db.DbRuntimeContext;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.gui.dialog.tenant.TenantSelectionDialog;
import de.gbanking.gui.enu.FileType;
import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.model.AccountTableModel;
import de.gbanking.gui.panel.about.AboutPanel;
import de.gbanking.gui.panel.action.PinAskDialog;
import de.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.panel.overview.AllTransactionsOverviewPanel;
import de.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.panel.overview.OverviewBasePanel;
import de.gbanking.gui.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.panel.setting.SettingsDialog;
import de.gbanking.gui.progress.FileExportProgressBarPanel;
import de.gbanking.gui.progress.FileImportProgressBarPanel;
import de.gbanking.messages.Messages;
import de.gbanking.tenant.TenantStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class GBankingGui extends Application {

	private static final Logger log = LogManager.getLogger(GBankingGui.class);
	private static final String LAST_PATH_SELECTED = "lastPathSelected";
	private static final String LAST_TENANT_ID = "lastTenantId";
	private static final String LANGUAGE = "language";

	private final FileChooser fileChooser = new FileChooser();

	private final Map<String, String> optionsMap = new HashMap<>();
	private final Map<String, OverviewBasePanel> overviewPanelMap = new HashMap<>();

	private String choosenFile;
	private GBankingBean bean;

	private BorderPane root;
	private Label statusLabel;
	private Label versionLabel;

	private AccountsTransactionsOverviewPanel overviewPanel;
	private MoneyTransferOverviewPanel moneyTransferPanel;
	private BankAccessOverviewPanel bankAccessOverviewPanel;
	private CategoryOverviewPanel categoryOverviewPanel;
	private RecipientOverviewPanel recipientOverviewPanel;
	private AllAccountsOverviewPanel allAccountsOverviewPanel;
	private AllTransactionsOverviewPanel allTransactionsOverviewPanel;

	private Stage primaryStage;

	@Override
	public void start(Stage stage) {
		this.primaryStage = stage;

		restoreOptions();

		if (!loginTenant()) {
			Platform.exit();
			return;
		}

		initializeMainWindow(stage);
		log.info("GBanking started.");
	}

	private void restoreOptions() {
		try {
			RestoreHandler.restoreOptions(optionsMap);
		} catch (IOException ioe) {
			log.error("IOException in restore: {}", ioe.getMessage());
		}
	}

	private void configureFileChooser() {
		if (optionsMap.get(LAST_PATH_SELECTED) != null) {
			fileChooser.setInitialDirectory(new File(optionsMap.get(LAST_PATH_SELECTED)));
		}
	}

	private boolean loginTenant() {
		Messages.setLocale(Messages.localeFromCode(optionsMap.get(LANGUAGE)));

		TenantStore tenantStore = new TenantStore();
		TenantSelectionDialog tenantDialog = new TenantSelectionDialog(primaryStage, tenantStore);
		return tenantDialog.showAndWait(optionsMap.get(LAST_TENANT_ID), optionsMap.get(LANGUAGE)).map(loginResult -> {
			optionsMap.put(LAST_TENANT_ID, loginResult.lastSelectedTenantId());
			optionsMap.put(LANGUAGE, loginResult.languageCode());
			DbRuntimeContext.setCurrentDbDirectory(tenantStore.getTenantDirectory(loginResult.tenant().id()).toString());
			return true;
		}).orElseGet(() -> {
			optionsMap.put(LANGUAGE, tenantDialog.getSelectedLanguageCode());
			storeOptionsQuietly();
			return false;
		});
	}

	private void initializeMainWindow(Stage stage) {
		root = new BorderPane();
		root.setPadding(new Insets(4));

		configureFileChooser();
		root.setTop(createMenuBar());

		statusLabel = new Label("Info");
		BorderPane bottom = new BorderPane();
		bottom.setPadding(new Insets(4));
		bottom.setLeft(statusLabel);

		var javaVersion = SystemInfo.javaVersion();
		var javafxVersion = SystemInfo.javafxVersion();

		versionLabel = new Label("JavaFX " + javafxVersion + ", running on Java " + javaVersion + ".");
		bottom.setRight(versionLabel);
		root.setBottom(bottom);

		createAndShowStartGui();
		createOverviewPanelMap();

		Scene scene = new Scene(root, 1600, 1200);
		stage.setTitle("GBanking");
		stage.setScene(scene);
		stage.setOnCloseRequest(event -> shutdownApplication());

		try {
			Image icon = new Image(getClass().getResourceAsStream("/icon_coin.png"));
			stage.getIcons().add(icon);
		} catch (Exception e) {
			log.error("Error setting icon: {}", e.getMessage());
		}

		scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
		stage.show();

		bean = new GBankingBean();
		bean.setup();
	}

	private void createAndShowStartGui() {
		overviewPanel = new AccountsTransactionsOverviewPanel();
		overviewPanel.createOverallPanel(true);
		root.setCenter(overviewPanel);
	}

	private void createOverviewPanelMap() {
		overviewPanelMap.put(PageContext.ACCOUNTS_TRANSACTIONS.name(), overviewPanel);
		overviewPanelMap.put(PageContext.ACCOUNTS_MONEYTRANSFERS.name(), moneyTransferPanel);
		overviewPanelMap.put(PageContext.BANKACCESS.name(), bankAccessOverviewPanel);
		overviewPanelMap.put(PageContext.CATEGORIES.name(), categoryOverviewPanel);
		overviewPanelMap.put(PageContext.RECIPIENTS.name(), recipientOverviewPanel);
		overviewPanelMap.put(PageContext.ALL_ACCOUNTS.name(), allAccountsOverviewPanel);
		overviewPanelMap.put(PageContext.ALL_TRANSACTIONS.name(), allTransactionsOverviewPanel);
	}

	private MenuBar createMenuBar() {
		MenuBar menuBar = new MenuBar();

		Menu fileMenu = new Menu(getText("UI_MENU_FILE"));
		Menu editMenu = new Menu(getText("UI_MENU_EDIT"));
		Menu executeMenu = new Menu(getText("UI_MENU_EXECUTE"));
		Menu settingsMenu = new Menu(getText("UI_MENU_SETTINGS"));
		Menu aboutMenu = new Menu(getText("UI_MENU_ABOUT"));

		MenuItem fileNewMenuItem = new MenuItem(getText("UI_MENU_FILE_NEW"));
		fileNewMenuItem.setOnAction(e -> statusLabel.setText(getText("UI_STATUS_NEW")));

		MenuItem fileOpenMenuItem = new MenuItem(getText("UI_MENU_FILE_OPEN"));
		fileOpenMenuItem.setOnAction(e -> statusLabel.setText(getText("UI_STATUS_OPEN")));

		MenuItem fileSaveMenuItem = new MenuItem(getText("UI_MENU_FILE_SAVE"));
		fileSaveMenuItem.setOnAction(e -> statusLabel.setText(getText("UI_STATUS_SAVE")));

		Menu fileImportMenu = new Menu(getText("UI_MENU_FILE_IMPORT"));
		MenuItem fileImportXML = new MenuItem("XML");
		fileImportXML.setOnAction(e -> processImport());
		fileImportMenu.getItems().add(fileImportXML);

		Menu fileExportMenu = new Menu(getText("UI_MENU_FILE_EXPORT"));
		MenuItem fileExportCSV = new MenuItem("CSV");
		fileExportCSV.setOnAction(e -> processExport(FileType.CSV));
		MenuItem fileExportXML = new MenuItem("XML");
		fileExportXML.setOnAction(e -> processExport(FileType.XML));
		fileExportMenu.getItems().addAll(fileExportCSV, fileExportXML);

		MenuItem fileExitMenuItem = new MenuItem(getText("UI_MENU_FILE_EXIT"));
		fileExitMenuItem.setOnAction(e -> shutdownApplication());

		MenuItem switchTenantMenuItem = new MenuItem(getText("UI_MENU_FILE_SWITCH_TENANT"));
		switchTenantMenuItem.setOnAction(e -> switchTenant());

		fileMenu.getItems().addAll(fileNewMenuItem, fileOpenMenuItem, fileSaveMenuItem, fileImportMenu, fileExportMenu, new SeparatorMenuItem(),
				switchTenantMenuItem, fileExitMenuItem);

		MenuItem editAccountsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ACCOUNTS"));
		editAccountsMenuItem.setOnAction(e -> activateOverview(PageContext.ACCOUNTS_TRANSACTIONS.name()));

		MenuItem editOrdersMenuItem = new MenuItem(getText("UI_MENU_EDIT_ORDERS"));
		editOrdersMenuItem.setOnAction(e -> activateOverview(PageContext.ACCOUNTS_MONEYTRANSFERS.name()));

		MenuItem editBankAccessMenuItem = new MenuItem(getText("UI_MENU_EDIT_BANKACCESS"));
		editBankAccessMenuItem.setOnAction(e -> activateOverview(PageContext.BANKACCESS.name()));

		MenuItem editCategoriesMenuItem = new MenuItem(getText("UI_MENU_EDIT_CATEGORIES"));
		editCategoriesMenuItem.setOnAction(e -> activateOverview(PageContext.CATEGORIES.name()));

		MenuItem editRecipientsMenuItem = new MenuItem(getText("UI_MENU_EDIT_RECIPIENTS"));
		editRecipientsMenuItem.setOnAction(e -> activateOverview(PageContext.RECIPIENTS.name()));

		MenuItem editAllAccountsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ALL_ACCOUNTS"));
		editAllAccountsMenuItem.setOnAction(e -> activateOverview(PageContext.ALL_ACCOUNTS.name()));

		MenuItem editAllTransactionsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ALL_TRANSACTIONS"));
		editAllTransactionsMenuItem.setOnAction(e -> activateOverview(PageContext.ALL_TRANSACTIONS.name()));

		editMenu.getItems().addAll(editAccountsMenuItem, editOrdersMenuItem, editBankAccessMenuItem, editCategoriesMenuItem, editRecipientsMenuItem,
				editAllAccountsMenuItem, editAllTransactionsMenuItem);

		PinAskDialog pinWindow = new PinAskDialog(primaryStage);

		MenuItem updateAccountsMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_UPDATE_ACCOUNTS"));
		updateAccountsMenuItem.setOnAction(e -> updateAccounts(pinWindow));

		MenuItem executeTransfersMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_TRANSFERS"));
		executeTransfersMenuItem.setOnAction(e -> executeTransfers(pinWindow));

		executeMenu.getItems().addAll(updateAccountsMenuItem, executeTransfersMenuItem);

		MenuItem settingsMenuItem = new MenuItem(getText("UI_MENU_SETTINGS_OPEN"));
		settingsMenuItem.setOnAction(e -> showSettingsWindow());
		settingsMenu.getItems().add(settingsMenuItem);

		MenuItem aboutMenuItem = new MenuItem(getText("UI_MENU_ABOUT_OPEN"));
		aboutMenuItem.setOnAction(e -> showAboutWindow());
		aboutMenu.getItems().add(aboutMenuItem);

		menuBar.getMenus().addAll(fileMenu, editMenu, executeMenu, settingsMenu, aboutMenu);
		return menuBar;
	}

	private void activateOverview(String actionCommand) {
		statusLabel.setText(actionCommand + " MenuItem clicked");

		OverviewBasePanel panelToActivate = overviewPanelMap.get(actionCommand);

		if (panelToActivate == null) {
			panelToActivate = createOverviewPanel(actionCommand);
			overviewPanelMap.put(actionCommand, panelToActivate);
		}

		panelToActivate.setDisable(false);
		root.setCenter(panelToActivate);
		panelToActivate.refreshOnShow();
	}

	private OverviewBasePanel createOverviewPanel(String actionCommand) {
		return switch (PageContext.valueOf(actionCommand)) {
		case ACCOUNTS_TRANSACTIONS -> {
			AccountsTransactionsOverviewPanel panel = new AccountsTransactionsOverviewPanel();
			panel.createOverallPanel(true);
			overviewPanel = panel;
			yield panel;
		}
		case ACCOUNTS_MONEYTRANSFERS -> {
			MoneyTransferOverviewPanel panel = new MoneyTransferOverviewPanel();
			panel.createOverallPanel(true);
			moneyTransferPanel = panel;
			yield panel;
		}
		case BANKACCESS -> {
			BankAccessOverviewPanel panel = new BankAccessOverviewPanel();
			panel.createOverallPanel(true);
			bankAccessOverviewPanel = panel;
			yield panel;
		}
		case CATEGORIES -> {
			CategoryOverviewPanel panel = new CategoryOverviewPanel();
			panel.createOverallPanel(true);
			categoryOverviewPanel = panel;
			yield panel;
		}
		case RECIPIENTS -> {
			RecipientOverviewPanel panel = new RecipientOverviewPanel();
			panel.createOverallPanel(true);
			recipientOverviewPanel = panel;
			yield panel;
		}
		case ALL_ACCOUNTS -> {
			AllAccountsOverviewPanel panel = new AllAccountsOverviewPanel();
			panel.createOverallPanel(true);
			allAccountsOverviewPanel = panel;
			yield panel;
		}
		case ALL_TRANSACTIONS -> {
			AllTransactionsOverviewPanel panel = new AllTransactionsOverviewPanel();
			panel.createOverallPanel(true);
			allTransactionsOverviewPanel = panel;
			yield panel;
		}
		};
	}

	private void updateAccounts(PinAskDialog pinWindow) {
		log.info("updateAccounts called.");

		if (overviewPanel == null || overviewPanel.getAccountListPanel() == null) {
			showWarning(getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}

		AccountTableModel modelAccount = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = modelAccount.getCheckedAccounts();

		if (checkedAccounts.isEmpty()) {
			log.info("no Accounts selected!");
			showWarning(getText("ALERT_ACCOUNT_NO_SELECTION"));
			return;
		}

		Map<BankAccount, char[]> pinMap = new LinkedHashMap<>();
		for (BankAccount bankAccount : checkedAccounts) {
			pinWindow.setBankInfo(bankAccount.getBlz(), bankAccount.getBankName());
			Stage pinDialog = pinWindow.createNewPinAskDialog();
			pinDialog.showAndWait();
			char[] pin = pinWindow.getPin();
			if (pin == null || pin.length == 0) {
				return;
			}
			pinMap.put(bankAccount, pin);
		}

		Task<Void> updateTask = new Task<>() {
			@Override
			protected Void call() {
				for (Entry<BankAccount, char[]> entry : pinMap.entrySet()) {
					BankAccount bankAccount = entry.getKey();
					log.info("Account to update: {}", bankAccount.getAccountName());
					bean.retrieveAccountTransactions(bankAccount, entry.getValue());
				}
				bean.postRetriveActions(checkedAccounts);
				return null;
			}
		};
		updateTask.setOnSucceeded(event -> overviewPanel.refreshOnShow());
		updateTask.setOnFailed(event -> log.error("Error updating accounts", updateTask.getException()));

		Thread thread = new Thread(updateTask, "gbanking-hbci-update-accounts");
		thread.setDaemon(true);
		thread.start();
	}

	private void executeTransfers(PinAskDialog pinWindow) {
		log.info("executeTransfers called.");

		List<MoneyTransfer> moneytransferList = bean.retrieveOpenTransfers();

		Map<Integer, char[]> pinMap = new LinkedHashMap<>();
		Map<Integer, BankAccount> accountMap = new LinkedHashMap<>();

		for (MoneyTransfer moneytransfer : moneytransferList) {
			int accountId = moneytransfer.getAccountId();
			if (!pinMap.containsKey(accountId)) {
				BankAccount bankAccount = bean.getAccountForOpenMoneytransfers(accountId);
				accountMap.put(accountId, bankAccount);
				pinWindow.setBankInfo(bankAccount.getBlz(), bankAccount.getBankName());

				Stage pinDialog = pinWindow.createNewPinAskDialog();
				pinDialog.showAndWait();
				char[] pin = pinWindow.getPin();
				if (pin == null || pin.length == 0) {
					return;
				}
				pinMap.put(accountId, pin);
			}
		}

		Task<Void> transferTask = new Task<>() {
			@Override
			protected Void call() {
				for (MoneyTransfer moneytransfer : moneytransferList) {
					int accountId = moneytransfer.getAccountId();
					bean.executeTransfer(moneytransfer, accountMap.get(accountId), pinMap.get(accountId));
				}
				return null;
			}
		};
		transferTask.setOnSucceeded(event -> {
			if (moneyTransferPanel != null) {
				moneyTransferPanel.refreshOnShow();
			}
		});
		transferTask.setOnFailed(event -> log.error("Error executing transfers", transferTask.getException()));

		Thread thread = new Thread(transferTask, "gbanking-hbci-execute-transfers");
		thread.setDaemon(true);
		thread.start();
	}

	private void showSettingsWindow() {
		SettingsDialog settingsDialog = new SettingsDialog(primaryStage);
		Stage settingsWindow = settingsDialog.createWindow();
		settingsWindow.showAndWait();
	}

	private void showAboutWindow() {
		AboutPanel aboutPanel = new AboutPanel(primaryStage);
		Stage aboutWindow = aboutPanel.createNewAboutWindow();
		aboutWindow.showAndWait();
	}

	private void processImport() {
		handleFileChooserDialog(FileType.XML);
		try {
			importFile(FileType.XML);
		} catch (Exception e) {
			log.error("Import failed", e);
		}
	}

	private void processExport(FileType fileType) {
		if (overviewPanel == null || overviewPanel.getAccountListPanel() == null) {
			return;
		}

		AccountTableModel modelAccount = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = modelAccount.getCheckedAccounts();

		if (checkedAccounts.isEmpty()) {
			log.info("no Accounts selected, so using all!");
			checkedAccounts = modelAccount.getAllAccounts();
		}

		handleFileChooserDialog(fileType);

		try {
			exportFile(checkedAccounts, fileType);
		} catch (Exception e) {
			log.error("Export failed", e);
		}
	}

	private void handleFileChooserDialog(FileType fileType) {
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(fileType.getDescription(), "*" + fileType.getSuffix()));

		File selectedFile = fileChooser.showOpenDialog(primaryStage);

		if (selectedFile != null) {
			choosenFile = selectedFile.getAbsolutePath();
			optionsMap.put(LAST_PATH_SELECTED, selectedFile.getParent());
		}
	}

	private void importFile(FileType fileType) throws Exception {
		if (choosenFile != null) {
			FileImportProgressBarPanel progressPanel = new FileImportProgressBarPanel(primaryStage);
			Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressPanel.startTask(choosenFile, fileType, overviewPanel.getAccountListPanel());
			progressWindow.show();
		} else {
			log.warn("no import file chosen!");
		}
	}

	private void exportFile(List<BankAccount> checkedAccounts, FileType fileType) throws Exception {
		if (choosenFile != null) {
			FileExportProgressBarPanel progressPanel = new FileExportProgressBarPanel(primaryStage, checkedAccounts);

			Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressPanel.startTask(choosenFile, fileType, overviewPanel.getAccountListPanel());
			progressWindow.show();
		} else {
			log.warn("no export file chosen!");
		}
	}

	private void shutdownApplication() {
		storeOptionsQuietly();
		Platform.exit();
	}

	private void switchTenant() {
		String previousDbDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Path previousDbPath = Path.of(previousDbDirectory);

		DBController.resetConnection();
		InstituteLookupCache.clear();
		primaryStage.hide();

		if (!loginTenant()) {
			if (Files.exists(previousDbPath)) {
				DbRuntimeContext.setCurrentDbDirectory(previousDbDirectory);
				DBController.getInstance(previousDbDirectory);
				primaryStage.show();
			} else {
				Platform.exit();
			}
			return;
		}

		resetMainWindowState();
		initializeMainWindow(primaryStage);
	}

	private void resetMainWindowState() {
		overviewPanelMap.clear();
		overviewPanel = null;
		moneyTransferPanel = null;
		bankAccessOverviewPanel = null;
		categoryOverviewPanel = null;
		recipientOverviewPanel = null;
		allAccountsOverviewPanel = null;
		allTransactionsOverviewPanel = null;
		bean = null;
		root = null;
		statusLabel = null;
		versionLabel = null;
		choosenFile = null;
	}

	private void showWarning(String text) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.initOwner(primaryStage);
		alert.setHeaderText(null);
		alert.setContentText(text);
		alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		alert.showAndWait();
	}

	private String getText(String key) {
		return Messages.getInstance().getMessage(key);
	}

	private void storeOptionsQuietly() {
		try {
			RestoreHandler.storeOptions(optionsMap);
		} catch (Exception e) {
			log.error("IOException: {}", e.getMessage());
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
