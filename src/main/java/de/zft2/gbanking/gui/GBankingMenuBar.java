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

public class GBankingMenuBar extends MenuBar implements BaseGui {

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
		MenuItem fileImportBookingsCsv = new MenuItem(getText(UI_MENU_FILE_CSV));
		fileImportBookingsCsv.setOnAction(e -> gui.processBookingImport(ExportType.BOOKINGS_CSV));
		MenuItem fileImportBookingsFp3 = new MenuItem(getText("UI_MENU_FILE_FP3"));
		fileImportBookingsFp3.setOnAction(e -> gui.processBookingImport(ExportType.BOOKINGS_FP3));
		MenuItem fileImportBookingsMt940 = new MenuItem(getText("UI_MENU_FILE_MT940"));
		fileImportBookingsMt940.setOnAction(e -> gui.processBookingImport(ExportType.BOOKINGS_MT940));
		MenuItem fileImportBookingsXml = new MenuItem(getText("UI_MENU_FILE_XML"));
		fileImportBookingsXml.setOnAction(e -> gui.processBookingImport(ExportType.BOOKINGS_XML));
		fileImportBookingsMenu.getItems().addAll(fileImportBookingsCsv, fileImportBookingsFp3, fileImportBookingsMt940, fileImportBookingsXml);
		Menu fileImportOrdersMenu = new Menu(getText("UI_MENU_FILE_ORDERS"));
		MenuItem fileImportCsvOrders = new MenuItem(getText(UI_MENU_FILE_CSV));
		fileImportCsvOrders.setOnAction(e -> gui.processMoneyTransferCsvImport());
		fileImportOrdersMenu.getItems().add(fileImportCsvOrders);
		MenuItem fileImportCreditcard = new MenuItem(getText("UI_MENU_FILE_IMPORT_CREDITCARD"));
		fileImportCreditcard.setOnAction(e -> gui.processCreditcardImport());
		fileImportMenu.getItems().addAll(fileImportBookingsMenu, fileImportOrdersMenu, new SeparatorMenuItem(), fileImportCreditcard);

		Menu fileExportMenu = new Menu(getText("UI_MENU_FILE_EXPORT"));
		Menu fileExportBookingsMenu = new Menu(getText("UI_MENU_FILE_BOOKINGS"));
		MenuItem fileExportCSV = new MenuItem(getText(UI_MENU_FILE_CSV));
		fileExportCSV.setOnAction(e -> gui.processExport(ExportType.BOOKINGS_CSV));
		MenuItem fileExportFP3 = new MenuItem(getText("UI_MENU_FILE_FP3"));
		fileExportFP3.setOnAction(e -> gui.processExport(ExportType.BOOKINGS_FP3));
		MenuItem fileExportMT940 = new MenuItem(getText("UI_MENU_FILE_MT940"));
		fileExportMT940.setOnAction(e -> gui.processExport(ExportType.BOOKINGS_MT940));
		MenuItem fileExportXML = new MenuItem(getText("UI_MENU_FILE_XML"));
		fileExportXML.setOnAction(e -> gui.processExport(ExportType.BOOKINGS_XML));
		fileExportBookingsMenu.getItems().addAll(fileExportCSV, fileExportFP3, fileExportMT940, fileExportXML);
		Menu fileExportOrdersMenu = new Menu(getText("UI_MENU_FILE_ORDERS"));
		MenuItem fileExportCSVOrders = new MenuItem(getText(UI_MENU_FILE_CSV));
		fileExportCSVOrders.setOnAction(e -> gui.processExport(ExportType.MONEYTRANSFERS_CSV));
		fileExportOrdersMenu.getItems().add(fileExportCSVOrders);
		fileExportMenu.getItems().addAll(fileExportBookingsMenu, fileExportOrdersMenu);

		MenuItem createBackupMenuItem = new MenuItem(getText("UI_MENU_FILE_CREATE_BACKUP"));
		createBackupMenuItem.setOnAction(event -> gui.createTenantBackup());
		MenuItem restoreBackupMenuItem = new MenuItem(getText("UI_MENU_FILE_RESTORE_BACKUP"));
		restoreBackupMenuItem.setOnAction(event -> gui.restoreTenantBackup());

		MenuItem fileExitMenuItem = new MenuItem(getText("UI_MENU_FILE_EXIT"));
		fileExitMenuItem.setOnAction(e -> gui.shutdownApplication());
		fileExitMenuItem.setAccelerator(KeyboardShortcuts.EXIT);

		MenuItem switchTenantMenuItem = new MenuItem(getText("UI_MENU_FILE_SWITCH_TENANT"));
		switchTenantMenuItem.setOnAction(e -> gui.switchTenant());

		fileMenu.getItems().addAll(fileImportMenu, fileExportMenu, new SeparatorMenuItem(), createBackupMenuItem, restoreBackupMenuItem,
				new SeparatorMenuItem(), switchTenantMenuItem, fileExitMenuItem);

		MenuItem editBankAccessMenuItem = new MenuItem(getText("UI_MENU_EDIT_BANKACCESS"));
		editBankAccessMenuItem.setOnAction(e -> gui.activateOverview(PageContext.BANKACCESS));

		MenuItem editOpenActionsMenuItem = new MenuItem(getText("UI_MENU_EDIT_OPEN_ACTIONS"));
		editOpenActionsMenuItem.setOnAction(e -> gui.activateOverview(PageContext.OPEN_ACTIONS));

		MenuItem editAccountsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ACCOUNTS"));
		editAccountsMenuItem.setOnAction(e -> gui.activateOverview(PageContext.ACCOUNTS_TRANSACTIONS));

		MenuItem editOrdersMenuItem = new MenuItem(getText("UI_MENU_EDIT_ORDERS"));
		editOrdersMenuItem.setOnAction(e -> gui.activateOverview(PageContext.ACCOUNTS_MONEYTRANSFERS));

		MenuItem editCategoriesMenuItem = new MenuItem(getText("UI_MENU_EDIT_CATEGORIES"));
		editCategoriesMenuItem.setOnAction(e -> gui.activateOverview(PageContext.CATEGORIES));

		MenuItem editRecipientsMenuItem = new MenuItem(getText("UI_MENU_EDIT_RECIPIENTS"));
		editRecipientsMenuItem.setOnAction(e -> gui.activateOverview(PageContext.RECIPIENTS));

		MenuItem editAllAccountsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ALL_ACCOUNTS"));
		editAllAccountsMenuItem.setOnAction(e -> gui.activateOverview(PageContext.ALL_ACCOUNTS));

		MenuItem editAllTransactionsMenuItem = new MenuItem(getText("UI_MENU_EDIT_ALL_TRANSACTIONS"));
		editAllTransactionsMenuItem.setOnAction(e -> gui.activateOverview(PageContext.ALL_TRANSACTIONS));

		MenuItem instituteDirectoryMenuItem = new MenuItem(getText("UI_MENU_EDIT_INSTITUTES"));
		instituteDirectoryMenuItem.setOnAction(e -> gui.activateOverview(PageContext.INSTITUTES));

		editMenu.getItems().addAll(editOpenActionsMenuItem, editBankAccessMenuItem, editAccountsMenuItem, editOrdersMenuItem, editCategoriesMenuItem,
				editRecipientsMenuItem, editAllAccountsMenuItem, editAllTransactionsMenuItem, instituteDirectoryMenuItem);

		MenuItem refreshMenuItem = new MenuItem(getText("UI_MENU_VIEW_REFRESH"));
		refreshMenuItem.setOnAction(event -> gui.handleShortcut(Action.REFRESH));
		refreshMenuItem.setAccelerator(KeyboardShortcuts.REFRESH);
		CheckMenuItem onlyOnlineAccountsMenuItem = new CheckMenuItem(getText("UI_MENU_VIEW_ONLY_ONLINE_ACCOUNTS"));
		onlyOnlineAccountsMenuItem.setSelected(gui.isOnlyOnlineAccountsVisible());
		onlyOnlineAccountsMenuItem.setOnAction(event -> gui.setOnlyOnlineAccountsVisible(onlyOnlineAccountsMenuItem.isSelected()));
		viewMenu.getItems().addAll(refreshMenuItem, new SeparatorMenuItem(), onlyOnlineAccountsMenuItem);

		PinAskDialog pinWindow = new PinAskDialog(stage);

		MenuItem updateAccountsMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_UPDATE_ACCOUNTS"));
		updateAccountsMenuItem.setOnAction(e -> gui.updateAccounts(pinWindow));

		MenuItem executeTransfersMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_TRANSFERS"));
		executeTransfersMenuItem.setOnAction(e -> gui.executeTransfers(pinWindow));
		MenuItem executeSelectedActionsMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_SELECTED_ACTIONS"));
		executeSelectedActionsMenuItem.setOnAction(e -> gui.executeSelectedOpenActions(pinWindow));
		executeSelectedActionsMenuItem.setVisible(false);

		Menu orderInventoryMenu = new Menu(getText("UI_MENU_EXECUTE_ORDER_INVENTORY"));
		MenuItem retrieveStandingOrdersMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_RETRIEVE_STANDING_ORDERS"));
		retrieveStandingOrdersMenuItem.setOnAction(e -> gui.retrieveOrderInventory(pinWindow, OrderType.STANDING_ORDER));
		MenuItem retrieveScheduledTransfersMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_RETRIEVE_SCHEDULED_TRANSFERS"));
		retrieveScheduledTransfersMenuItem.setOnAction(e -> gui.retrieveOrderInventory(pinWindow, OrderType.SCHEDULED_TRANSFER));
		orderInventoryMenu.getItems().addAll(retrieveStandingOrdersMenuItem, retrieveScheduledTransfersMenuItem);
		MenuItem assignRebookingsMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_ASSIGN_REBOOKINGS"));
		assignRebookingsMenuItem.setOnAction(e -> {
			new RebookingToolDialog(stage, gui.getSelectedAccountsForAccountUpdate()).assignRebookings();
			gui.refreshTransactionOverviews();
		});
		MenuItem createRebookingsMenuItem = new MenuItem(getText("UI_MENU_EXECUTE_CREATE_REBOOKINGS"));
		createRebookingsMenuItem.setOnAction(e -> {
			new RebookingToolDialog(stage, gui.getSelectedAccountsForAccountUpdate()).createRebookings();
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
							|| accountUpdateAccounts.stream().noneMatch(account -> bankingCapabilityService.supportsAccountTransactions(account)));
			List<BankAccount> inventoryAccounts = gBankingService != null ? gui.getSelectedAccountsForOrderInventory() : List.of();
			retrieveStandingOrdersMenuItem.setDisable(inventoryAccounts.isEmpty()
					|| inventoryAccounts.stream().noneMatch(account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.STANDING_ORDER)));
			retrieveScheduledTransfersMenuItem.setDisable(inventoryAccounts.isEmpty()
					|| inventoryAccounts.stream().noneMatch(account -> bankingCapabilityService.supportsOrderInventory(account, OrderType.SCHEDULED_TRANSFER)));
		});

		executeMenu.getItems().add(executeSelectedActionsMenuItem);
		executeMenu.getItems().addAll(standardExecuteItems);

		MenuItem balanceAnalysisMenuItem = new MenuItem(getText("UI_MENU_ANALYSIS_BALANCES"));
		balanceAnalysisMenuItem.setOnAction(e -> gui.activateOverview(PageContext.ANALYSIS));
		MenuItem categoryAnalysisMenuItem = new MenuItem(getText("UI_MENU_ANALYSIS_CATEGORIES"));
		categoryAnalysisMenuItem.setOnAction(e -> gui.activateOverview(PageContext.CATEGORY_ANALYSIS));
		analysisMenu.getItems().addAll(balanceAnalysisMenuItem, categoryAnalysisMenuItem);

		MenuItem settingsMenuItem = new MenuItem(getText("UI_MENU_SETTINGS_OPEN"));
		settingsMenuItem.setOnAction(e -> gui.showSettingsWindow());
		settingsMenuItem.setAccelerator(KeyboardShortcuts.SETTINGS);
		settingsMenu.getItems().add(settingsMenuItem);

		MenuItem manualMenuItem = new MenuItem(getText("UI_MENU_ABOUT_MANUAL"));
		manualMenuItem.setOnAction(e -> gui.showManual());
		manualMenuItem.setAccelerator(KeyboardShortcuts.HELP);
		MenuItem checkUpdateMenuItem = new MenuItem(getText("UI_MENU_UPDATE_CHECK"));
		checkUpdateMenuItem.setOnAction(e -> gui.checkForApplicationUpdates());
		MenuItem openLogDirectoryMenuItem = new MenuItem(getText("UI_MENU_ABOUT_OPEN_LOG_DIRECTORY"));
		openLogDirectoryMenuItem.setOnAction(e -> gui.openLogDirectory());
		MenuItem createDiagnosticPackageMenuItem = new MenuItem(getText("UI_MENU_ABOUT_CREATE_DIAGNOSTIC_PACKAGE"));
		createDiagnosticPackageMenuItem.setOnAction(e -> gui.createDiagnosticPackage());
		MenuItem aboutMenuItem = new MenuItem(getText("UI_MENU_ABOUT_OPEN"));
		aboutMenuItem.setOnAction(e -> gui.showAboutWindow());
		aboutMenu.getItems().addAll(manualMenuItem, new SeparatorMenuItem(), checkUpdateMenuItem, new SeparatorMenuItem(), openLogDirectoryMenuItem,
				createDiagnosticPackageMenuItem, new SeparatorMenuItem(), aboutMenuItem);

		this.getMenus().addAll(fileMenu, editMenu, viewMenu, executeMenu, analysisMenu, settingsMenu, aboutMenu);
	}

}
