package de.zft2.gbanking.gui;

import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.gui.dialog.rebooking.RebookingToolDialog;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.KeyboardShortcutDispatcher.Action;
import de.zft2.gbanking.gui.panel.action.PinAskDialog;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

final class GBankingMenuBar extends MenuBar implements BaseGui {

	private static final String UI_MENU_FILE_CSV = "UI_MENU_FILE_CSV";

	private final Stage stage;
	private final GBankingGui gui;
	private final GBankingService gBankingService;
	private final BankingCapabilityService bankingCapabilityService;

	GBankingMenuBar(GBankingGui gui) {
		this(gui, ServiceRegistry.getService(GBankingService.class), ServiceRegistry.getService(BankingCapabilityService.class));
	}

	GBankingMenuBar(GBankingGui gui, GBankingService gBankingService, BankingCapabilityService bankingCapabilityService) {
		this.stage = gui.getStage();
		this.gui = gui;
		this.gBankingService = gBankingService;
		this.bankingCapabilityService = bankingCapabilityService;
		createMenuBar();
	}

	private void createMenuBar() {

		Menu fileMenu = new Menu(getText("UI_MENU_FILE"));
		Menu editMenu = new Menu(getText("UI_MENU_EDIT"));
		Menu viewMenu = new Menu(getText("UI_MENU_VIEW"));
		Menu executeMenu = new Menu(getText("UI_MENU_EXECUTE"));
		Menu analysisMenu = new Menu(getText("UI_MENU_ANALYSIS"));
		Menu settingsMenu = new Menu(getText("UI_MENU_SETTINGS"));
		Menu aboutMenu = new Menu(getText("UI_MENU_ABOUT"));

		Menu fileImportMenu = new Menu(getText("UI_MENU_FILE_IMPORT"));
		Menu fileImportBookingsMenu = new Menu(getText("UI_MENU_FILE_BOOKINGS"));
		MenuItem fileImportBookingsCsv = createMenuItem("UI_MENU_FILE_CSV_FORMATS", () -> gui.processBookingImport(ExportType.BOOKINGS_CSV));
		MenuItem fileImportBookingsFp3 = createMenuItem("UI_MENU_FILE_FP3", () -> gui.processBookingImport(ExportType.BOOKINGS_FP3));
		MenuItem fileImportBookingsMt940 = createMenuItem("UI_MENU_FILE_MT940", () -> gui.processBookingImport(ExportType.BOOKINGS_MT940));
		MenuItem fileImportBookingsXml = createMenuItem("UI_MENU_FILE_XML", () -> gui.processBookingImport(ExportType.BOOKINGS_XML));
		fileImportBookingsMenu.getItems().addAll(fileImportBookingsCsv, fileImportBookingsFp3, fileImportBookingsMt940, fileImportBookingsXml);
		Menu fileImportOrdersMenu = new Menu(getText("UI_MENU_FILE_ORDERS"));
		MenuItem fileImportCsvOrders = createMenuItem("UI_MENU_FILE_CSV_MONEYPLEX", () -> gui.processMoneyTransferImport(ExportType.MONEYTRANSFERS_CSV));
		MenuItem fileImportSepaOrders = createMenuItem("UI_MENU_FILE_XML_SEPA", () -> gui.processMoneyTransferImport(ExportType.MONEYTRANSFERS_SEPA_XML));
		fileImportOrdersMenu.getItems().addAll(fileImportCsvOrders, fileImportSepaOrders);
		Menu fileImportStockMenu = new Menu(getText("UI_MENU_FILE_STOCK_PORTFOLIOS"));
		MenuItem fileImportStockCsv = createMenuItem("UI_MENU_FILE_CSV_FORMATS", () -> gui.processStockCsvImport());
		MenuItem fileImportStockXml = createMenuItem("UI_MENU_FILE_PP_XML", () -> gui.processStockImport(ExportType.STOCK_PP_XML));
		fileImportStockMenu.getItems().addAll(fileImportStockCsv, fileImportStockXml);
		fileImportMenu.getItems().addAll(fileImportBookingsMenu, fileImportOrdersMenu, fileImportStockMenu);

		Menu fileExportMenu = new Menu(getText("UI_MENU_FILE_EXPORT"));
		Menu fileExportBookingsMenu = new Menu(getText("UI_MENU_FILE_BOOKINGS"));
		MenuItem fileExportCSV = createMenuItem(UI_MENU_FILE_CSV, () -> gui.processExport(ExportType.BOOKINGS_CSV));
		MenuItem fileExportFP3 = createMenuItem("UI_MENU_FILE_FP3", () -> gui.processExport(ExportType.BOOKINGS_FP3));
		MenuItem fileExportMT940 = createMenuItem("UI_MENU_FILE_MT940", () -> gui.processExport(ExportType.BOOKINGS_MT940));
		MenuItem fileExportXML = createMenuItem("UI_MENU_FILE_XML", () -> gui.processExport(ExportType.BOOKINGS_XML));
		fileExportBookingsMenu.getItems().addAll(fileExportCSV, fileExportFP3, fileExportMT940, fileExportXML);
		Menu fileExportOrdersMenu = new Menu(getText("UI_MENU_FILE_ORDERS"));
		MenuItem fileExportCSVOrders = createMenuItem("UI_MENU_FILE_CSV_MONEYPLEX", () -> gui.processExport(ExportType.MONEYTRANSFERS_CSV));
		MenuItem fileExportSepaOrders = createMenuItem("UI_MENU_FILE_XML_SEPA", () -> gui.processExport(ExportType.MONEYTRANSFERS_SEPA_XML));
		fileExportOrdersMenu.getItems().addAll(fileExportCSVOrders, fileExportSepaOrders);
		Menu fileExportStockMenu = new Menu(getText("UI_MENU_FILE_STOCK_PORTFOLIOS"));
		Menu fileExportPortfolioPerformanceMenu = new Menu(getText("UI_MENU_FILE_PORTFOLIO_PERFORMANCE"));
		MenuItem fileExportStockTransactions = createMenuItem("UI_MENU_FILE_PP_PORTFOLIO_TRANSACTIONS", () -> gui.processStockExport(ExportType.STOCK_PP_TRANSACTIONS_CSV));
		MenuItem fileExportStockAccountTransactions = createMenuItem("UI_MENU_FILE_PP_ACCOUNT_TRANSACTIONS", () -> gui.processStockExport(ExportType.STOCK_PP_ACCOUNT_TRANSACTIONS_CSV));
		MenuItem fileExportStockSecurities = createMenuItem("UI_MENU_FILE_PP_SECURITIES", () -> gui.processStockExport(ExportType.STOCK_PP_SECURITIES_CSV));
		MenuItem fileExportStockPrices = createMenuItem("UI_MENU_FILE_PP_PRICES", () -> gui.processStockExport(ExportType.STOCK_PP_PRICES_CSV));
		fileExportPortfolioPerformanceMenu.getItems().addAll(fileExportStockTransactions,
				fileExportStockAccountTransactions, fileExportStockSecurities, fileExportStockPrices);
		fileExportStockMenu.getItems().add(fileExportPortfolioPerformanceMenu);
		fileExportMenu.getItems().addAll(fileExportBookingsMenu, fileExportOrdersMenu, fileExportStockMenu);

		MenuItem createBackupMenuItem = createMenuItem("UI_MENU_FILE_CREATE_BACKUP", () -> gui.createTenantBackup());
		MenuItem restoreBackupMenuItem = createMenuItem("UI_MENU_FILE_RESTORE_BACKUP", () -> gui.restoreTenantBackup());

		MenuItem fileExitMenuItem = createMenuItem("UI_MENU_FILE_EXIT", () -> gui.shutdownApplication());
		fileExitMenuItem.setAccelerator(KeyboardShortcuts.EXIT);

		MenuItem switchTenantMenuItem = createMenuItem("UI_MENU_FILE_SWITCH_TENANT", () -> gui.switchTenant());

		fileMenu.getItems().addAll(fileImportMenu, fileExportMenu, new SeparatorMenuItem(), createBackupMenuItem, restoreBackupMenuItem,
				new SeparatorMenuItem(), switchTenantMenuItem, fileExitMenuItem);

		MenuItem editBankAccessMenuItem = createMenuItem("UI_MENU_EDIT_BANKACCESS", () -> gui.activateOverview(PageContext.BANKACCESS));

		MenuItem editOpenActionsMenuItem = createMenuItem("UI_MENU_EDIT_OPEN_ACTIONS", () -> gui.activateOverview(PageContext.OPEN_ACTIONS));

		MenuItem editAccountsMenuItem = createMenuItem("UI_MENU_EDIT_ACCOUNTS",
				() -> gui.activateOverview(PageContext.ACCOUNTS_TRANSACTIONS));
		editAccountsMenuItem.getStyleClass().add("gbanking-emphasized-menu-item");

		MenuItem editStockPortfoliosMenuItem = createMenuItem("UI_MENU_EDIT_STOCK_PORTFOLIOS", () -> gui.activateOverview(PageContext.STOCK_PORTFOLIOS));

		MenuItem editOrdersMenuItem = createMenuItem("UI_MENU_EDIT_ORDERS", () -> gui.activateOverview(PageContext.ACCOUNTS_MONEYTRANSFERS));

		MenuItem editCategoriesMenuItem = createMenuItem("UI_MENU_EDIT_CATEGORIES", () -> gui.activateOverview(PageContext.CATEGORIES));

		MenuItem editRecipientsMenuItem = createMenuItem("UI_MENU_EDIT_RECIPIENTS", () -> gui.activateOverview(PageContext.RECIPIENTS));

		MenuItem editAllAccountsMenuItem = createMenuItem("UI_MENU_EDIT_ALL_ACCOUNTS", () -> gui.activateOverview(PageContext.ALL_ACCOUNTS));

		MenuItem editAllStockPortfoliosMenuItem = createMenuItem("UI_MENU_EDIT_ALL_STOCK_PORTFOLIOS", () -> gui.activateOverview(PageContext.ALL_STOCK_PORTFOLIOS));

		MenuItem editAllSecuritiesMenuItem = createMenuItem("UI_MENU_EDIT_ALL_SECURITIES", () -> gui.activateOverview(PageContext.ALL_SECURITIES));

		MenuItem editAllTransactionsMenuItem = createMenuItem("UI_MENU_EDIT_ALL_TRANSACTIONS", () -> gui.activateOverview(PageContext.ALL_TRANSACTIONS));
		Menu administrationMenu = new Menu(getText("UI_MENU_EDIT_ADMINISTRATION"));
		administrationMenu.getItems().addAll(editAllAccountsMenuItem, editAllStockPortfoliosMenuItem,
				editAllSecuritiesMenuItem, editAllTransactionsMenuItem);

		MenuItem instituteDirectoryMenuItem = createMenuItem("UI_MENU_EDIT_INSTITUTES", () -> gui.activateOverview(PageContext.INSTITUTES));

		editMenu.getItems().addAll(editOpenActionsMenuItem, editBankAccessMenuItem, editAccountsMenuItem, editStockPortfoliosMenuItem,
				editOrdersMenuItem, editCategoriesMenuItem,
				editRecipientsMenuItem, administrationMenu, instituteDirectoryMenuItem);

		MenuItem refreshMenuItem = createMenuItem("UI_MENU_VIEW_REFRESH", () -> gui.handleShortcut(Action.REFRESH));
		refreshMenuItem.setAccelerator(KeyboardShortcuts.REFRESH);
		CheckMenuItem onlyOnlineAccountsMenuItem = new CheckMenuItem(getText("UI_MENU_VIEW_ONLY_ONLINE_ACCOUNTS"));
		onlyOnlineAccountsMenuItem.setSelected(gui.isOnlyOnlineAccountsVisible());
		onlyOnlineAccountsMenuItem.setOnAction(event -> gui.setOnlyOnlineAccountsVisible(onlyOnlineAccountsMenuItem.isSelected()));
		CheckMenuItem onlySecuritiesWithHoldingsMenuItem = new CheckMenuItem(
				getText("UI_MENU_VIEW_ONLY_SECURITIES_WITH_HOLDINGS"));
		onlySecuritiesWithHoldingsMenuItem.setSelected(gui.isOnlySecuritiesWithHoldingsVisible());
		onlySecuritiesWithHoldingsMenuItem.setOnAction(event -> gui.setOnlySecuritiesWithHoldingsVisible(
				onlySecuritiesWithHoldingsMenuItem.isSelected()));
		viewMenu.getItems().addAll(refreshMenuItem, new SeparatorMenuItem(), onlyOnlineAccountsMenuItem,
				onlySecuritiesWithHoldingsMenuItem);

		PinAskDialog pinWindow = new PinAskDialog(stage);

		MenuItem updateAccountsMenuItem = createMenuItem("UI_MENU_EXECUTE_UPDATE_ACCOUNTS", () -> gui.updateAccounts(pinWindow));

		MenuItem executeTransfersMenuItem = createMenuItem("UI_MENU_EXECUTE_TRANSFERS", () -> gui.executeTransfers(pinWindow));
		MenuItem executeSelectedActionsMenuItem = createMenuItem("UI_MENU_EXECUTE_SELECTED_ACTIONS", () -> gui.executeSelectedOpenActions(pinWindow));
		executeSelectedActionsMenuItem.setVisible(false);

		Menu orderInventoryMenu = new Menu(getText("UI_MENU_EXECUTE_ORDER_INVENTORY"));
		MenuItem retrieveStandingOrdersMenuItem = createMenuItem("UI_MENU_EXECUTE_RETRIEVE_STANDING_ORDERS", () -> gui.retrieveOrderInventory(pinWindow, OrderType.STANDING_ORDER));
		MenuItem retrieveScheduledTransfersMenuItem = createMenuItem("UI_MENU_EXECUTE_RETRIEVE_SCHEDULED_TRANSFERS", () -> gui.retrieveOrderInventory(pinWindow, OrderType.SCHEDULED_TRANSFER));
		orderInventoryMenu.getItems().addAll(retrieveStandingOrdersMenuItem, retrieveScheduledTransfersMenuItem);
		MenuItem assignRebookingsMenuItem = createMenuItem("UI_MENU_EXECUTE_ASSIGN_REBOOKINGS", () -> {
			new RebookingToolDialog(stage, gui.getSelectedAccountsForRebooking()).assignRebookings();
			gui.refreshTransactionOverviews();
		});
		MenuItem createRebookingsMenuItem = createMenuItem("UI_MENU_EXECUTE_CREATE_REBOOKINGS", () -> {
			new RebookingToolDialog(stage, gui.getSelectedAccountsForRebooking()).createRebookings();
			gui.refreshTransactionOverviews();
		});
		SeparatorMenuItem rebookingSeparator = new SeparatorMenuItem();
		List<MenuItem> standardExecuteItems = List.of(updateAccountsMenuItem, executeTransfersMenuItem, orderInventoryMenu,
				rebookingSeparator, assignRebookingsMenuItem, createRebookingsMenuItem);
		executeMenu.setOnShowing(event -> {
			boolean openActionsActive = gui.isOpenActionsOverviewActive();
			standardExecuteItems.forEach(item -> item.setVisible(!openActionsActive));
			executeSelectedActionsMenuItem.setVisible(openActionsActive);
			executeSelectedActionsMenuItem.setDisable(openActionsActive && !gui.hasSelectedOpenActions());
			executeTransfersMenuItem.setDisable(openActionsActive);
			orderInventoryMenu.setDisable(openActionsActive);
			List<BankAccount> accountUpdateAccounts = gBankingService != null ? gui.getSelectedAccountsForAccountUpdate() : List.of();
			updateAccountsMenuItem.setDisable(
					openActionsActive || accountUpdateAccounts.isEmpty()
							|| accountUpdateAccounts.stream().noneMatch(bankingCapabilityService::supportsAccountUpdate));
			List<BankAccount> inventoryAccounts = gBankingService != null ? gui.getSelectedAccountsForOrderInventory() : List.of();
			retrieveStandingOrdersMenuItem.setDisable(inventoryAccounts.isEmpty()
					|| inventoryAccounts.stream().noneMatch(account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.STANDING_ORDER)));
			retrieveScheduledTransfersMenuItem.setDisable(inventoryAccounts.isEmpty()
					|| inventoryAccounts.stream().noneMatch(account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.SCHEDULED_TRANSFER)));
		});

		executeMenu.getItems().add(executeSelectedActionsMenuItem);
		executeMenu.getItems().addAll(standardExecuteItems);

		MenuItem balanceAnalysisMenuItem = createMenuItem("UI_MENU_ANALYSIS_BALANCES", () -> gui.activateOverview(PageContext.ANALYSIS));
		MenuItem categoryAnalysisMenuItem = createMenuItem("UI_MENU_ANALYSIS_CATEGORIES", () -> gui.activateOverview(PageContext.CATEGORY_ANALYSIS));
		analysisMenu.getItems().addAll(balanceAnalysisMenuItem, categoryAnalysisMenuItem);

		MenuItem settingsMenuItem = createMenuItem("UI_MENU_SETTINGS_OPEN", () -> gui.showSettingsWindow());
		settingsMenuItem.setAccelerator(KeyboardShortcuts.SETTINGS);
		settingsMenu.getItems().add(settingsMenuItem);

		MenuItem manualMenuItem = createMenuItem("UI_MENU_ABOUT_MANUAL", () -> gui.showManual());
		manualMenuItem.setAccelerator(KeyboardShortcuts.HELP);
		MenuItem checkUpdateMenuItem = createMenuItem("UI_MENU_UPDATE_CHECK", () -> gui.checkForApplicationUpdates());
		MenuItem openLogDirectoryMenuItem = createMenuItem("UI_MENU_ABOUT_OPEN_LOG_DIRECTORY", () -> gui.openLogDirectory());
		MenuItem createDiagnosticPackageMenuItem = createMenuItem("UI_MENU_ABOUT_CREATE_DIAGNOSTIC_PACKAGE", () -> gui.createDiagnosticPackage());
		MenuItem aboutMenuItem = createMenuItem("UI_MENU_ABOUT_OPEN", () -> gui.showAboutWindow());
		aboutMenu.getItems().addAll(manualMenuItem, new SeparatorMenuItem(), checkUpdateMenuItem, new SeparatorMenuItem(), openLogDirectoryMenuItem,
				createDiagnosticPackageMenuItem, new SeparatorMenuItem(), aboutMenuItem);

		this.getMenus().addAll(fileMenu, editMenu, viewMenu, executeMenu, analysisMenu, settingsMenu, aboutMenu);
	}

	/**
	 * Creates a menu item with the given text and action.
	 *
	 * @param textKey the key of the text of the menu item
	 * @param action  the action executed when the menu item is selected
	 * @return the created menu item
	 */
	private MenuItem createMenuItem(String textKey, Runnable action) {
		MenuItem menuItem = new MenuItem(getText(textKey));
		menuItem.setOnAction(event -> action.run());
		return menuItem;
	}

}
