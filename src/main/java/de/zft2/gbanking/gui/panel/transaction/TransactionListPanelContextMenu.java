package de.zft2.gbanking.gui.panel.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import de.zft2.gbanking.BaseMessagesBean;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.zft2.gbanking.gui.util.BookingFileActionSupport;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.booking.BookingCategoryService;
import de.zft2.gbanking.service.booking.BookingService;
import de.zft2.gbanking.service.booking.BookingSplitService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

class TransactionListPanelContextMenu extends ContextMenu implements BaseMessagesBean {

	private final BankingCapabilityService bankingCapabilityService;
	private final BookingService bookingService;
	private final BookingCategoryService bookingCategoryService;
	private final BookingSplitService bookingSplitService;
	private final AccountTransactionService accountTransactionService;

	private TransactionListPanel panelTransactionList;
	private TransactionsOverviewBasePanel parentPanel;
	private ObservableList<Booking> masterData;

	MenuItem newManualItem = new MenuItem(getText("UI_MENU_BOOKING_NEW_MANUAL"));
	MenuItem editManualItem = new MenuItem(getText("UI_MENU_BOOKING_EDIT_MANUAL"));
	MenuItem deleteBookingItem = new MenuItem(getText("UI_MENU_BOOKING_DELETE"));
	MenuItem deleteFromDateItem = new MenuItem(getText("UI_MENU_BOOKING_DELETE_FROM_DATE"));
	MenuItem deleteUntilDateItem = new MenuItem(getText("UI_MENU_BOOKING_DELETE_UNTIL_DATE"));
	MenuItem applyCategoryRulesItem = new MenuItem(getText("UI_MENU_BOOKING_APPLY_CATEGORY_RULES"));
	MenuItem assignCategoryItem = new MenuItem(getText("UI_MENU_BOOKING_ASSIGN_CATEGORY"));
	MenuItem clearCategoryItem = new MenuItem(getText("UI_MENU_BOOKING_CLEAR_CATEGORY"));
	MenuItem releaseRebookingLinksItem = new MenuItem(getText("UI_MENU_BOOKING_RELEASE_REBOOKING_LINKS"));
	MenuItem importCsvItem = new MenuItem(getText("UI_MENU_FILE_CSV_FORMATS"));
	MenuItem importFp3Item = new MenuItem(getText("UI_MENU_FILE_FP3"));
	MenuItem importMt940Item = new MenuItem(getText("UI_MENU_FILE_MT940"));
	MenuItem importXmlItem = new MenuItem(getText("UI_MENU_FILE_XML"));
	MenuItem exportCsvItem = new MenuItem(getText("UI_MENU_FILE_CSV"));
	MenuItem exportFp3Item = new MenuItem(getText("UI_MENU_FILE_FP3"));
	MenuItem exportMt940Item = new MenuItem(getText("UI_MENU_FILE_MT940"));
	MenuItem exportXmlItem = new MenuItem(getText("UI_MENU_FILE_XML"));

	TransactionListPanelContextMenu(TransactionListPanel parentPanelTransactionList) {
		this(parentPanelTransactionList, ServiceRegistry.getService(BankingCapabilityService.class), ServiceRegistry.getService(BookingService.class),
				ServiceRegistry.getService(BookingCategoryService.class), ServiceRegistry.getService(BookingSplitService.class),
				ServiceRegistry.getService(AccountTransactionService.class));
	}

	TransactionListPanelContextMenu(TransactionListPanel parentPanelTransactionList, BankingCapabilityService bankingCapabilityService,
			BookingService bookingService, BookingCategoryService bookingCategoryService, BookingSplitService bookingSplitService,
			AccountTransactionService accountTransactionService) {
		this.bankingCapabilityService = bankingCapabilityService;
		this.bookingService = bookingService;
		this.bookingCategoryService = bookingCategoryService;
		this.bookingSplitService = bookingSplitService;
		this.accountTransactionService = accountTransactionService;
		this.panelTransactionList = parentPanelTransactionList;
		this.parentPanel = parentPanelTransactionList.parentPanel;
		this.masterData = parentPanelTransactionList.getMasterData();
		initContextMenu();
	}

	private void initContextMenu() {
		Menu useAsTemplateMenu = createUseAsTemplateMenu();
		Menu categoryMenu = createCategoryMenu();
		Menu importMenu = new Menu(getText("UI_MENU_FILE_IMPORT"));

		importMenu.getItems().addAll(importCsvItem, importFp3Item, importMt940Item, importXmlItem);
		Menu exportMenu = new Menu(getText("UI_MENU_FILE_EXPORT"));
		exportMenu.getItems().addAll(exportCsvItem, exportFp3Item, exportMt940Item, exportXmlItem);

		newManualItem.setOnAction(event -> handleNewManualBooking());
		editManualItem.setOnAction(event -> handleEditManualBooking());
		deleteBookingItem.setOnAction(event -> handleDeleteBooking());
		deleteFromDateItem.setOnAction(event -> handleDeleteBookingBlock(true));
		deleteUntilDateItem.setOnAction(event -> handleDeleteBookingBlock(false));
		applyCategoryRulesItem.setOnAction(event -> handleApplyCategoryRules());
		assignCategoryItem.setOnAction(event -> handleAssignCategory());
		clearCategoryItem.setOnAction(event -> handleClearCategory());
		releaseRebookingLinksItem.setOnAction(event -> handleReleaseRebookingLinks());
		importCsvItem.setOnAction(event -> handleImportBookings(ExportType.BOOKINGS_CSV));
		importFp3Item.setOnAction(event -> handleImportBookings(ExportType.BOOKINGS_FP3));
		importMt940Item.setOnAction(event -> handleImportBookings(ExportType.BOOKINGS_MT940));
		importXmlItem.setOnAction(event -> handleImportBookings(ExportType.BOOKINGS_XML));
		exportCsvItem.setOnAction(event -> handleExportBookings(ExportType.BOOKINGS_CSV));
		exportFp3Item.setOnAction(event -> handleExportBookings(ExportType.BOOKINGS_FP3));
		exportMt940Item.setOnAction(event -> handleExportBookings(ExportType.BOOKINGS_MT940));
		exportXmlItem.setOnAction(event -> handleExportBookings(ExportType.BOOKINGS_XML));

		this.getItems().addAll(newManualItem, editManualItem, deleteBookingItem, deleteFromDateItem, deleteUntilDateItem, categoryMenu,
				releaseRebookingLinksItem, new SeparatorMenuItem(), useAsTemplateMenu, new SeparatorMenuItem(), importMenu, exportMenu);

		this.setOnShowing(event -> updateContextMenuState(useAsTemplateMenu, categoryMenu, importMenu, exportMenu));
	}

	private Menu createCategoryMenu() {
		Menu categoryMenu = new Menu(getText("UI_MENU_BOOKING_CATEGORY"));
		categoryMenu.getItems().addAll(applyCategoryRulesItem, assignCategoryItem, clearCategoryItem);
		return categoryMenu;
	}

	private Menu createUseAsTemplateMenu() {
		Menu useAsTemplateMenu = new Menu(getText("UI_MENU_BOOKING_USE_AS_TEMPLATE"));
		MenuItem newTransferItem = new MenuItem(getText("UI_MENU_BOOKING_TEMPLATE_NEW_TRANSFER"));
		MenuItem newScheduledTransferItem = new MenuItem(getText("UI_MENU_BOOKING_TEMPLATE_NEW_SCHEDULED_TRANSFER"));
		MenuItem newStandingOrderItem = new MenuItem(getText("UI_MENU_BOOKING_TEMPLATE_NEW_STANDING_ORDER"));
		newTransferItem.getProperties().put(OrderType.class, OrderType.TRANSFER);
		newScheduledTransferItem.getProperties().put(OrderType.class, OrderType.SCHEDULED_TRANSFER);
		newStandingOrderItem.getProperties().put(OrderType.class, OrderType.STANDING_ORDER);

		newTransferItem.setOnAction(event -> handleUseAsTemplate(OrderType.TRANSFER));
		newScheduledTransferItem.setOnAction(event -> handleUseAsTemplate(OrderType.SCHEDULED_TRANSFER));
		newStandingOrderItem.setOnAction(event -> handleUseAsTemplate(OrderType.STANDING_ORDER));

		useAsTemplateMenu.getItems().addAll(newTransferItem, newScheduledTransferItem, newStandingOrderItem);
		return useAsTemplateMenu;
	}

	private void updateContextMenuState(Menu useAsTemplateMenu, Menu categoryMenu, Menu importMenu, Menu exportMenu) {
		Booking selectedBooking = getSelectedBooking();
		boolean hasSelectedBooking = selectedBooking != null;
		BankAccount contextAccount = resolveContextAccount(selectedBooking);
		boolean canCreateManual = contextAccount != null;
		boolean canUseTemplate = hasSelectedBooking && contextAccount != null;
		boolean canEditManual = hasSelectedBooking && selectedBooking.getSource() == Source.MANUELL;
		boolean canDeleteBooking = hasSelectedBooking && isSingleDeleteSource(selectedBooking.getSource());
		boolean canDeleteBlock = hasSelectedBooking && isBlockDeleteSource(selectedBooking.getSource());
		List<Booking> actionBookings = getActionBookings();
		boolean hasActionBookings = !actionBookings.isEmpty();
		boolean canApplyCategoryRules = !masterData.isEmpty();
		boolean canAssignCategory = hasActionBookings;
		boolean canClearCategory = actionBookings.stream().anyMatch(booking -> getCategoryId(booking) > 0);
		boolean canReleaseRebookingLinks = actionBookings.stream().anyMatch(this::hasCrossBookingLink);

		newManualItem.setDisable(!canCreateManual);
		editManualItem.setDisable(!canEditManual);
		deleteBookingItem.setDisable(!canDeleteBooking);
		deleteFromDateItem.setDisable(!canDeleteBlock);
		deleteUntilDateItem.setDisable(!canDeleteBlock);
		applyCategoryRulesItem.setDisable(!canApplyCategoryRules);
		assignCategoryItem.setDisable(!canAssignCategory);
		clearCategoryItem.setDisable(!canClearCategory);
		categoryMenu.setDisable(!canApplyCategoryRules && !canAssignCategory && !canClearCategory);
		releaseRebookingLinksItem.setDisable(!canReleaseRebookingLinks);
		useAsTemplateMenu.setDisable(!canUseTemplate);
		for (MenuItem templateItem : useAsTemplateMenu.getItems()) {
			OrderType orderType = (OrderType) templateItem.getProperties().get(OrderType.class);
			templateItem.setDisable(!canUseTemplate || orderType == null || !bankingCapabilityService.supportsTransferOrderType(contextAccount, orderType));
		}
		importMenu.setDisable(contextAccount == null);
		exportMenu.setDisable(contextAccount == null);
	}

	private void handleNewManualBooking() {
		Booking selectedBooking = getSelectedBooking();
		if (resolveContextAccount(selectedBooking) == null) {
			return;
		}
		parentPanel.getTransactionDetailPanel().startNewManualBooking();
	}

	private void handleEditManualBooking() {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || selectedBooking.getSource() != Source.MANUELL) {
			return;
		}
		panelTransactionList.handleBookingSelection(selectedBooking);
		parentPanel.getTransactionDetailPanel().startEditDisplayedBooking();
	}

	private void handleDeleteBooking() {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || !isSingleDeleteSource(selectedBooking.getSource())) {
			return;
		}

		if (!DialogWindowSupport.showConfirmation(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.CONFIRMATION,
				getText("ALERT_BOOKING_DELETE_SELECTED_TITLE"), getText("ALERT_BOOKING_DELETE_SELECTED_HEADER"), getText("ALERT_BOOKING_DELETE_SELECTED_TEXT"),
				ButtonType.OK, ButtonType.CANCEL)) {
			return;
		}

		bookingSplitService.deleteBookingWithSplits(selectedBooking);
		reloadAfterDeletion();
	}

	private void handleDeleteBookingBlock(boolean deleteFromDate) {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || !isBlockDeleteSource(selectedBooking.getSource())) {
			return;
		}

		String rangeText = deleteFromDate ? getText("UI_MENU_BOOKING_DELETE_FROM_DATE") : getText("UI_MENU_BOOKING_DELETE_UNTIL_DATE");
		String confirmTextKey = deleteFromDate ? "ALERT_BOOKING_BLOCK_DELETE_TEXT" : "ALERT_BOOKING_BLOCK_DELETE_TEXT_UNTIL";
		if (!DialogWindowSupport.showConfirmation(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.CONFIRMATION,
				getText("ALERT_BOOKING_BLOCK_DELETE_TITLE"), getText("ALERT_BOOKING_BLOCK_DELETE_HEADER"),
				getText(confirmTextKey, rangeText, DateFormatUtils.formatShort(getRelevantBookingDate(selectedBooking))), ButtonType.OK, ButtonType.CANCEL)) {
			return;
		}

		bookingService.deleteBookingsInBlock(selectedBooking, deleteFromDate);
		reloadAfterDeletion();
	}

	private void handleApplyCategoryRules() {
		if (masterData.isEmpty()) {
			return;
		}

		Optional<ButtonType> choice = DialogWindowSupport.showChoice(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.CONFIRMATION,
				getText("ALERT_BOOKING_CATEGORY_RULES_APPLY_TITLE"), getText("ALERT_BOOKING_CATEGORY_RULES_APPLY_HEADER"),
				getText("ALERT_BOOKING_CATEGORY_RULES_APPLY_TEXT"), ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
		if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
			return;
		}

		boolean overwriteExistingCategories = choice.get() == ButtonType.YES;
		int updatedBookingCount = bookingCategoryService.applyCategoryRulesToBookings(List.copyOf(masterData), overwriteExistingCategories);
		reloadAfterCategoryRuleApplication();
		DialogWindowSupport.showAlert(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.INFORMATION,
				getText("ALERT_BOOKING_CATEGORY_RULES_APPLY_RESULT", updatedBookingCount));
	}

	private void handleAssignCategory() {
		List<Booking> actionBookings = getActionBookings();
		if (actionBookings.isEmpty()) {
			return;
		}

		List<Category> categories = dbController.getAll(Category.class);
		if (categories.isEmpty()) {
			DialogWindowSupport.showAlert(panelTransactionList.getTableWindow(), Alert.AlertType.INFORMATION,
					getText("ALERT_BOOKING_ASSIGN_CATEGORY_NO_CATEGORY"));
			return;
		}

		Optional<Category> selectedCategory = showAssignCategoryDialog(categories, actionBookings);
		if (selectedCategory.isEmpty()) {
			return;
		}

		Integer selectedBookingId = Optional.ofNullable(getSelectedBooking()).map(Booking::getId).orElse(null);
		int updatedBookingCount = bookingCategoryService.assignCategoryToBookings(selectedCategory.get(), actionBookings);
		reloadAfterCategoryRuleApplication();
		panelTransactionList.selectBookingById(selectedBookingId);
		DialogWindowSupport.showAlert(panelTransactionList.getTableWindow(), Alert.AlertType.INFORMATION,
				getText("ALERT_BOOKING_ASSIGN_CATEGORY_RESULT", updatedBookingCount));
	}

	private void handleClearCategory() {
		List<Booking> actionBookings = getActionBookings();
		List<Booking> categorizedBookings = getCategorizedBookings(actionBookings);
		if (categorizedBookings.isEmpty()) {
			return;
		}

		if (!DialogWindowSupport.showConfirmation(panelTransactionList.getTableWindow(), Alert.AlertType.CONFIRMATION,
				getText("ALERT_BOOKING_CLEAR_CATEGORY_TITLE"), getText("ALERT_BOOKING_CLEAR_CATEGORY_HEADER"),
				getText("ALERT_BOOKING_CLEAR_CATEGORY_TEXT", categorizedBookings.size(), actionBookings.size()), ButtonType.OK, ButtonType.CANCEL)) {
			return;
		}

		Integer selectedBookingId = Optional.ofNullable(getSelectedBooking()).map(Booking::getId).orElse(null);
		int updatedBookingCount = bookingCategoryService.clearCategoryFromBookings(categorizedBookings);
		reloadAfterCategoryRuleApplication();
		panelTransactionList.selectBookingById(selectedBookingId);
		DialogWindowSupport.showAlert(panelTransactionList.getTableWindow(), Alert.AlertType.INFORMATION,
				getText("ALERT_BOOKING_CLEAR_CATEGORY_RESULT", updatedBookingCount));
	}

	private void handleReleaseRebookingLinks() {
		List<Booking> linkedBookings = getActionBookings().stream().filter(this::hasCrossBookingLink).toList();
		if (linkedBookings.isEmpty()) {
			return;
		}

		if (!DialogWindowSupport.showConfirmation(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.CONFIRMATION,
				getText("ALERT_BOOKING_RELEASE_REBOOKING_TITLE"), getText("ALERT_BOOKING_RELEASE_REBOOKING_HEADER"),
				getText("ALERT_BOOKING_RELEASE_REBOOKING_TEXT"), ButtonType.OK, ButtonType.CANCEL)) {
			return;
		}

		int updatedBookings = accountTransactionService.releaseRebookingLinks(linkedBookings);
		reloadAfterRebookingLinkRelease();
		DialogWindowSupport.showAlert(panelTransactionList.getTableWindow(), javafx.scene.control.Alert.AlertType.INFORMATION,
				getText("ALERT_BOOKING_RELEASE_REBOOKING_RESULT", updatedBookings));
	}

	private Optional<Category> showAssignCategoryDialog(List<Category> categories, List<Booking> actionBookings) {
		List<CategoryOption> categoryOptions = categories.stream().map(CategoryOption::new).toList();
		CategoryOption defaultOption = resolvePreselectedCategoryOption(categoryOptions, actionBookings).orElse(categoryOptions.get(0));

		return DialogWindowSupport.showSelection(panelTransactionList.getTableWindow(), getText("ALERT_BOOKING_ASSIGN_CATEGORY_TITLE"),
				getText("ALERT_BOOKING_ASSIGN_CATEGORY_HEADER"), getText("ALERT_BOOKING_ASSIGN_CATEGORY_TEXT"), defaultOption, categoryOptions)
				.map(CategoryOption::category);
	}

	private Optional<CategoryOption> resolvePreselectedCategoryOption(List<CategoryOption> categoryOptions, List<Booking> actionBookings) {
		int selectedCategoryId = resolveSingleSelectedCategoryId(actionBookings);
		return selectedCategoryId > 0 ? categoryOptions.stream().filter(option -> option.category().getId() == selectedCategoryId).findFirst()
				: Optional.empty();
	}

	private int resolveSingleSelectedCategoryId(List<Booking> actionBookings) {
		int selectedCategoryId = 0;
		for (Booking booking : actionBookings) {
			int categoryId = getCategoryId(booking);
			if (categoryId <= 0) {
				continue;
			}
			if (selectedCategoryId > 0 && selectedCategoryId != categoryId) {
				return 0;
			}
			selectedCategoryId = categoryId;
		}
		return selectedCategoryId;
	}

	private int getCategoryId(Booking booking) {
		if (booking == null) {
			return 0;
		}
		Category category = booking.getCategory();
		return category != null && category.getId() > 0 ? category.getId() : booking.getCategoryId();
	}

	private List<Booking> getCategorizedBookings(List<Booking> bookings) {
		return bookings == null ? List.of() : bookings.stream().filter(booking -> getCategoryId(booking) > 0).toList();
	}

	private record CategoryOption(Category category) {
		@Override
		public String toString() {
			return category != null && category.getFullName() != null ? category.getFullName() : "";
		}
	}

	private void reloadAfterDeletion() {
		parentPanel.getTransactionDetailPanel().clearDisplayedBooking();
		panelTransactionList.reload();
		BankAccount currentAccount = currentContextAccount();
		if (currentAccount != null && parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			panelTransactionList.updateModelBooking(bookingService.getBookingsForAccount(currentAccount.getId()));
		}
	}

	private void reloadAfterCategoryRuleApplication() {
		BankAccount contextAccount = currentContextAccount();
		if (contextAccount != null && parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			panelTransactionList.updateModelBooking(bookingService.getBookingsForAccount(contextAccount.getId()));
			return;
		}
		panelTransactionList.reload();
	}

	private void reloadAfterRebookingLinkRelease() {
		parentPanel.getTransactionDetailPanel().clearDisplayedBooking();
		reloadAfterCategoryRuleApplication();
	}

	private void handleUseAsTemplate(OrderType orderType) {
		Booking selectedBooking = getSelectedBooking();
		if (selectedBooking == null || resolveContextAccount(selectedBooking) == null) {
			return;
		}
		panelTransactionList.handleBookingSelection(selectedBooking);
		GuiContext.useBookingAsMoneyTransferTemplate(selectedBooking, orderType);
	}

	private void handleExportBookings(ExportType exportType) {
		BankAccount contextAccount = resolveContextAccount(getSelectedBooking());
		BookingFileActionSupport.exportBookings(panelTransactionList.getTableWindow(), contextAccount, exportType, parentAccountListPanel());
	}

	private void handleImportBookings(ExportType importType) {
		BankAccount contextAccount = resolveContextAccount(getSelectedBooking());
		BookingFileActionSupport.importBookings(panelTransactionList.getTableWindow(), contextAccount, importType, parentAccountListPanel(),
				() -> refreshAfterImport(contextAccount));
	}

	private void refreshAfterImport(BankAccount contextAccount) {
		if (parentPanel instanceof AccountsTransactionsOverviewPanel accountsPanel) {
			accountsPanel.getAccountListPanel().reload();
			panelTransactionList.updateModelBooking(bookingService.getBookingsForAccount(contextAccount.getId()));
			panelTransactionList.updatePanelBorder(getText("UI_PANEL_TRANSACTIONS") + " - " + contextAccount.getAccountName());
			return;
		}
		panelTransactionList.reload();
	}

	private Booking getSelectedBooking() {
		return panelTransactionList.getSelectedItem();
	}

	private List<Booking> getActionBookings() {
		List<Booking> checkedBookings = masterData.stream().filter(Booking::isSelected).toList();
		if (!checkedBookings.isEmpty()) {
			return checkedBookings;
		}
		Booking selectedBooking = getSelectedBooking();
		return selectedBooking != null ? List.of(selectedBooking) : List.of();
	}

	private boolean hasCrossBookingLink(Booking booking) {
		Integer crossBookingId = booking != null ? booking.getCrossBookingId() : null;
		return crossBookingId != null && crossBookingId > 0;
	}

	private boolean isSingleDeleteSource(Source source) {
		return source == Source.MANUELL || source == Source.MANUELL_NEW || source == Source.AUTO_ADJUSTING || source == Source.AUTO_ADJUSTING_NEW;
	}

	private boolean isBlockDeleteSource(Source source) {
		return source == Source.ONLINE || source == Source.ONLINE_NEW || source == Source.ONLINE_PRENO || source == Source.ONLINE_PRENO_NEW
				|| source == Source.IMPORT || source == Source.IMPORT_NEW || source == Source.IMPORT_INITIAL || source == Source.IMPORT_INITIAL_NEW;
	}

	private LocalDate getRelevantBookingDate(Booking booking) {
		return booking.getDateBooking() != null ? booking.getDateBooking() : booking.getDateValue();
	}

	private de.zft2.gbanking.db.dao.BankAccount resolveContextAccount(Booking selectedBooking) {
		de.zft2.gbanking.db.dao.BankAccount account = currentContextAccount();
		if (account != null) {
			parentPanel.getTransactionDetailPanel().setCurrentAccount(account);
			return account;
		}
		if (selectedBooking == null) {
			return null;
		}
		account = dbController.getById(de.zft2.gbanking.db.dao.BankAccount.class, selectedBooking.getAccountId());
		if (account != null) {
			parentPanel.getTransactionDetailPanel().setCurrentAccount(account);
		}
		return account;
	}

	private de.zft2.gbanking.db.dao.BankAccount currentContextAccount() {
		if (parentPanel instanceof AccountsTransactionsOverviewPanel accountsPanel) {
			return Optional.ofNullable(accountsPanel.getSelectedAccount()).orElse(null);
		}
		return null;
	}

	private de.zft2.gbanking.gui.panel.account.AccountListPanel parentAccountListPanel() {
		return parentPanel instanceof AccountsTransactionsOverviewPanel accountsPanel ? accountsPanel.getAccountListPanel() : null;
	}
}
