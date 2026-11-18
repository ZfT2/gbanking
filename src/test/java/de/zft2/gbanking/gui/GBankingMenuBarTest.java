package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.enu.AccountGrouping;
import de.zft2.gbanking.service.BankingCapabilityService;
import javafx.event.Event;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;

class GBankingMenuBarTest implements BaseMessages {

	@Test
	void groupingMenuShouldUseTheSharedViewSetting() {
		JavaFxTestSupport.runFx(() -> {
			AccountGrouping previous = GuiLayoutState.accountGroupingProperty().get();
			try {
				GBankingMenuBar bar = new GBankingMenuBar(mock(GBankingGui.class), null, mock(BankingCapabilityService.class));
				Menu menu = findMenu(findMenu(bar.getMenus(), "UI_MENU_VIEW").getItems(), "UI_MENU_VIEW_ACCOUNT_GROUPING");
				RadioMenuItem type = (RadioMenuItem) menu.getItems().get(1);
				type.fire();
				assertEquals(AccountGrouping.TYPE, GuiLayoutState.accountGroupingProperty().get());
				GuiLayoutState.accountGroupingProperty().set(AccountGrouping.BANK);
				Event.fireEvent(menu, new Event(Menu.ON_SHOWING));
				assertTrue(((RadioMenuItem) menu.getItems().get(2)).isSelected());
			} finally {
				GuiLayoutState.accountGroupingProperty().set(previous);
			}
		});
	}

	@Test
	void editMenuShouldGroupAdministrationViewsAndEmphasizeAccounts() {
		JavaFxTestSupport.runFx(() -> {
			GBankingMenuBar menuBar = new GBankingMenuBar(mock(GBankingGui.class), null,
					mock(BankingCapabilityService.class));
			Menu editMenu = findMenu(menuBar.getMenus(), "UI_MENU_EDIT");
			Menu administrationMenu = findMenu(editMenu.getItems(), "UI_MENU_EDIT_ADMINISTRATION");

			assertEquals(List.of(getText("UI_MENU_EDIT_ALL_ACCOUNTS"),
					getText("UI_MENU_EDIT_ALL_STOCK_PORTFOLIOS"), getText("UI_MENU_EDIT_ALL_SECURITIES"),
					getText("UI_MENU_EDIT_ALL_TRANSACTIONS")),
					administrationMenu.getItems().stream().map(MenuItem::getText).toList());
			MenuItem accounts = editMenu.getItems().stream()
					.filter(item -> getText("UI_MENU_EDIT_ACCOUNTS").equals(item.getText()))
					.findFirst().orElseThrow();
			assertTrue(accounts.getStyleClass().contains("gbanking-emphasized-menu-item"));
			Menu viewMenu = findMenu(menuBar.getMenus(), "UI_MENU_VIEW");
			assertTrue(viewMenu.getItems().stream().anyMatch(item ->
					getText("UI_MENU_VIEW_ONLY_SECURITIES_WITH_HOLDINGS").equals(item.getText())));
		});
	}

	@Test
	void fileMenuShouldGroupStockCsvImportAndPortfolioPerformanceExports() {
		JavaFxTestSupport.runFx(() -> {
			GBankingMenuBar menuBar = new GBankingMenuBar(mock(GBankingGui.class), null,
					mock(BankingCapabilityService.class));
			Menu fileMenu = findMenu(menuBar.getMenus(), "UI_MENU_FILE");
			Menu importMenu = findMenu(fileMenu.getItems(), "UI_MENU_FILE_IMPORT");
			Menu importStockMenu = findMenu(importMenu.getItems(), "UI_MENU_FILE_STOCK_PORTFOLIOS");
			assertEquals(List.of(getText("UI_MENU_FILE_CSV_FORMATS"), getText("UI_MENU_FILE_PP_XML")),
					importStockMenu.getItems().stream().map(MenuItem::getText).toList());

			Menu exportMenu = findMenu(fileMenu.getItems(), "UI_MENU_FILE_EXPORT");
			Menu exportStockMenu = findMenu(exportMenu.getItems(), "UI_MENU_FILE_STOCK_PORTFOLIOS");
			Menu portfolioPerformance = findMenu(exportStockMenu.getItems(), "UI_MENU_FILE_PORTFOLIO_PERFORMANCE");
			assertEquals(List.of(getText("UI_MENU_FILE_PP_PORTFOLIO_TRANSACTIONS"),
					getText("UI_MENU_FILE_PP_ACCOUNT_TRANSACTIONS"), getText("UI_MENU_FILE_PP_SECURITIES"),
					getText("UI_MENU_FILE_PP_PRICES")),
					portfolioPerformance.getItems().stream().map(MenuItem::getText).toList());
		});
	}

	private Menu findMenu(List<? extends MenuItem> items, String messageKey) {
		return items.stream().filter(Menu.class::isInstance).map(Menu.class::cast)
				.filter(menu -> getText(messageKey).equals(menu.getText())).findFirst().orElseThrow();
	}
}
