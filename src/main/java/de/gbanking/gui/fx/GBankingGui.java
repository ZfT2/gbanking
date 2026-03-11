package de.gbanking.gui.fx;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.GBankingBean;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.gui.fx.enu.FileType;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.model.AccountTableModel;
import de.gbanking.gui.fx.panel.about.AboutPanel;
import de.gbanking.gui.fx.panel.action.PinAskDialog;
import de.gbanking.gui.fx.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.AllTransactionsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.fx.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.fx.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.fx.panel.overview.OverviewBasePanel;
import de.gbanking.gui.fx.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.fx.progress.FileExportProgressBarPanel;
import de.gbanking.gui.fx.progress.FileImportProgressBarPanel;
import javafx.application.Application;
import javafx.application.Platform;
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

	private final FileChooser fileChooser = new FileChooser();

	private final Map<String, String> optionsMap = new HashMap<>();
	private final Map<String, OverviewBasePanel> overviewPanelMap = new HashMap<>();

	private String choosenFile;
	private GBankingBean bean;

	private BorderPane root;
	private Label statusLabel;

	private AccountsTransactionsOverviewPanel overviewPanel;
	private MoneyTransferOverviewPanel moneyTransferPanel;
	private BankAccessOverviewPanel bankAccessOverviewPanel;
	private CategoryOverviewPanel categoryOverviewPanel;
	private RecipientOverviewPanel recipientOverviewPanel;
	private AllAccountsOverviewPanel allAccountsOverviewPanel;
	private AllTransactionsOverviewPanel allTransactionsOverviewPanel;

	private OverviewBasePanel activeOverviewPanel;

	private Stage primaryStage;

	@Override
	public void start(Stage stage) {
		this.primaryStage = stage;

		root = new BorderPane();
		root.setPadding(new Insets(4));

		restoreOptions();
		configureFileChooser();

		root.setTop(createMenuBar());

		statusLabel = new Label("Info");
		BorderPane bottom = new BorderPane();
		bottom.setPadding(new Insets(4));
		bottom.setLeft(statusLabel);
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

	private void createAndShowStartGui() {
		overviewPanel = new AccountsTransactionsOverviewPanel();
		overviewPanel.createOverallPanel(true);
		activeOverviewPanel = overviewPanel;
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

		Menu fileMenu = new Menu("Datei");
		Menu editMenu = new Menu("Bearbeiten");
		Menu executeMenu = new Menu("Ausführen");
		Menu aboutMenu = new Menu("Über");

		MenuItem fileNewMenuItem = new MenuItem("Neu");
		fileNewMenuItem.setOnAction(e -> statusLabel.setText("New MenuItem clicked"));

		MenuItem fileOpenMenuItem = new MenuItem("Öffnen");
		fileOpenMenuItem.setOnAction(e -> statusLabel.setText("Open MenuItem clicked"));

		MenuItem fileSaveMenuItem = new MenuItem("Speichern");
		fileSaveMenuItem.setOnAction(e -> statusLabel.setText("Save MenuItem clicked"));

		Menu fileImportMenu = new Menu("Importieren...");
		MenuItem fileImportXML = new MenuItem("XML");
		fileImportXML.setOnAction(e -> processImport());
		fileImportMenu.getItems().add(fileImportXML);

		Menu fileExportMenu = new Menu("Exportieren...");
		MenuItem fileExportCSV = new MenuItem("CSV");
		fileExportCSV.setOnAction(e -> processExport(FileType.CSV));

		MenuItem fileExportXML = new MenuItem("XML");
		fileExportXML.setOnAction(e -> processExport(FileType.XML));

		fileExportMenu.getItems().addAll(fileExportCSV, fileExportXML);

		MenuItem fileExitMenuItem = new MenuItem("Beenden");
		fileExitMenuItem.setOnAction(e -> shutdownApplication());

		fileMenu.getItems().addAll(fileNewMenuItem, fileOpenMenuItem, fileSaveMenuItem, fileImportMenu, fileExportMenu, new SeparatorMenuItem(),
				fileExitMenuItem);

		MenuItem editAccountsMenuItem = new MenuItem("Konten...");
		editAccountsMenuItem.setOnAction(e -> activateOverview(PageContext.ACCOUNTS_TRANSACTIONS.name()));

		MenuItem editOrdersMenuItem = new MenuItem("Aufträge...");
		editOrdersMenuItem.setOnAction(e -> activateOverview(PageContext.ACCOUNTS_MONEYTRANSFERS.name()));

		MenuItem editBankAccessMenuItem = new MenuItem("Bankzugänge...");
		editBankAccessMenuItem.setOnAction(e -> activateOverview(PageContext.BANKACCESS.name()));

		MenuItem editCategoriesMenuItem = new MenuItem("Kategorien...");
		editCategoriesMenuItem.setOnAction(e -> activateOverview(PageContext.CATEGORIES.name()));

		MenuItem editRecipientsMenuItem = new MenuItem("Adressbuch...");
		editRecipientsMenuItem.setOnAction(e -> activateOverview(PageContext.RECIPIENTS.name()));

		MenuItem editAllAccountsMenuItem = new MenuItem("Alle Konten...");
		editAllAccountsMenuItem.setOnAction(e -> activateOverview(PageContext.ALL_ACCOUNTS.name()));

		MenuItem editAllTransactionsMenuItem = new MenuItem("Alle Umsätze...");
		editAllTransactionsMenuItem.setOnAction(e -> activateOverview(PageContext.ALL_TRANSACTIONS.name()));

		editMenu.getItems().addAll(editAccountsMenuItem, editOrdersMenuItem, editBankAccessMenuItem, editCategoriesMenuItem, editRecipientsMenuItem,
				editAllAccountsMenuItem, editAllTransactionsMenuItem);

		PinAskDialog pinWindow = new PinAskDialog(primaryStage);

		MenuItem updateAccountsMenuItem = new MenuItem("Umsätze abrufen");
		updateAccountsMenuItem.setOnAction(e -> updateAccounts(pinWindow));

		MenuItem executeTransfersMenuItem = new MenuItem("Aufträge ausführen");
		executeTransfersMenuItem.setOnAction(e -> executeTransfers(pinWindow));

		executeMenu.getItems().addAll(updateAccountsMenuItem, executeTransfersMenuItem);

		MenuItem aboutMenuItem = new MenuItem("Über GBanking...");
		aboutMenuItem.setOnAction(e -> showAboutWindow());
		aboutMenu.getItems().add(aboutMenuItem);

		menuBar.getMenus().addAll(fileMenu, editMenu, executeMenu, aboutMenu);
		return menuBar;
	}

	private void activateOverview(String actionCommand) {
		statusLabel.setText(actionCommand + " MenuItem clicked");

		OverviewBasePanel panelToActivate = overviewPanelMap.get(actionCommand);

		if (panelToActivate == null) {
			panelToActivate = createOverviewPanel(actionCommand);
			overviewPanelMap.put(actionCommand, panelToActivate);
		}

		if (panelToActivate != null) {
			panelToActivate.setDisable(false);
			activeOverviewPanel = panelToActivate;
			root.setCenter(panelToActivate);
		}
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

		for (BankAccount bankAccount : checkedAccounts) {
			Stage pinDialog = pinWindow.createNewPinAskDialog();
			pinDialog.showAndWait();
			char[] pin = pinWindow.getPin();

			log.info("Account to update: {}", bankAccount.getAccountName());
			bean.retrieveAccountTransactions(bankAccount, pin);
		}

		bean.postRetriveActions(checkedAccounts);
	}

	private void executeTransfers(PinAskDialog pinWindow) {
		log.info("executeTransfers called.");

		List<MoneyTransfer> moneytransferList = bean.retrieveOpenTransfers();

		int accountId = -1;
		BankAccount bankAccount = null;
		char[] pin = null;

		for (MoneyTransfer moneytransfer : moneytransferList) {
			if (accountId != moneytransfer.getAccountId()) {
				accountId = moneytransfer.getAccountId();
				bankAccount = bean.getAccountForOpenMoneytransfers(accountId);

				Stage pinDialog = pinWindow.createNewPinAskDialog();
				pinDialog.showAndWait();
				pin = pinWindow.getPin();
			}

			bean.executeTransfer(moneytransfer, bankAccount, pin);
		}
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
		try {
			RestoreHandler.storeOptions(optionsMap);
		} catch (Exception e) {
			log.error("IOException: {}", e.getMessage());
		}
		Platform.exit();
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
		return de.gbanking.messages.Messages.getInstance().getMessage(key);
	}

	public static void main(String[] args) {
		launch(args);
	}
}