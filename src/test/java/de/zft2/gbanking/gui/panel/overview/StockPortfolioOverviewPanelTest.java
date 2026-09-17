package de.zft2.gbanking.gui.panel.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

class StockPortfolioOverviewPanelTest {

	@AfterEach
	void closeDatabase() {
		GuiContext.setOnlyOnlineAccountsVisible(false);
		DBControllerTestUtil.closeAndNullifyConnection();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldProvidePortfolioAndSettlementAccountLayout() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		BankingCapabilityService capabilityService = mock(BankingCapabilityService.class);
		PortfolioSummary portfolio = new PortfolioSummary(1, "Depot A", "Bank A", "DE123",
				LocalDate.of(2026, 1, 1), 2, "Girokonto", "Bank A", "DE456", Currency.EUR,
				new BigDecimal("100.00"));
		when(service.getPortfolios()).thenReturn(List.of(portfolio));
		when(service.getPositions(portfolio.portfolioId())).thenReturn(List.of());
		when(service.getTransactions(portfolio.portfolioId())).thenReturn(List.of());
		when(service.getAccountTransactions(portfolio.settlementAccountId())).thenReturn(List.of());
		BankAccount portfolioAccount = new BankAccount();
		portfolioAccount.setId(10);
		portfolioAccount.setAccountType(AccountType.DEPOT);
		when(service.getPortfolioAccount(portfolio)).thenReturn(portfolioAccount);

		JavaFxTestSupport.runFx(() -> {
			StockPortfolioOverviewPanel panel = new StockPortfolioOverviewPanel(service, capabilityService);
			panel.refreshOnShow();

			assertEquals(PageContext.STOCK_PORTFOLIOS, panel.getPageContext());
			SplitPane mainPane = (SplitPane) panel.getChildren().get(1);
			VBox navigation = (VBox) mainPane.getItems().get(0);
			VBox right = (VBox) mainPane.getItems().get(1);
			TabPane tabs = (TabPane) right.getChildren().get(0);
			assertEquals(Region.USE_PREF_SIZE, tabs.getMinHeight());
			assertEquals(Region.USE_PREF_SIZE, tabs.getMaxHeight());
			assertEquals(List.of(BaseMessages.getTextStatic("UI_STOCK_TAB_PORTFOLIO"),
					BaseMessages.getTextStatic("UI_STOCK_TAB_SETTLEMENT_ACCOUNT")),
					tabs.getTabs().stream().map(tab -> tab.getText()).toList());
			StackPane content = (StackPane) right.getChildren().get(1);
			SplitPane portfolioTables = (SplitPane) content.getChildren().get(0);
			VBox accountTransactions = (VBox) content.getChildren().get(1);
			assertEquals(Orientation.VERTICAL, portfolioTables.getOrientation());
			assertTrue(portfolioTables.getItems().stream().allMatch(section -> ((VBox) section).getChildren().get(1) instanceof TableView));
			assertTrue(accountTransactions.getChildren().get(1) instanceof TableView);
			TreeTableView<?> tree = (TreeTableView<?>) navigation.getChildren().get(1);
			assertTrue(navigation.lookupAll(".button").isEmpty());
			assertFalse(tree.isShowRoot());
			assertEquals(2, tree.getColumns().size());
			assertEquals(1, tree.getRoot().getChildren().size());
			assertEquals(1, tree.getRoot().getChildren().get(0).getChildren().size());
			TableView<?> positions = (TableView<?>) ((VBox) portfolioTables.getItems().get(0)).getChildren().get(1);
			assertEquals(10, positions.getColumns().size());
			assertFalse(positions.isEditable());
			assertFalse(positions.getColumns().get(4).isEditable());
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_COLUMN_ACQUISITION_VALUE"),
					positions.getColumns().get(5).getText());
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_COLUMN_PERFORMANCE"),
					positions.getColumns().get(9).getText());
			TableView<?> transactions = (TableView<?>) ((VBox) portfolioTables.getItems().get(1)).getChildren().get(1);
			assertEquals(9, transactions.getColumns().size());
			assertEquals(190, transactions.getColumns().get(1).getPrefWidth());
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_COLUMN_PRICE"),
					transactions.getColumns().get(6).getText());
			var selectionColumn = (javafx.scene.control.TreeTableColumn<Object, Boolean>) (javafx.scene.control.TreeTableColumn<?, ?>)
					tree.getColumns().get(0);
			var portfolioItem = (javafx.scene.control.TreeItem<Object>) (javafx.scene.control.TreeItem<?>)
					tree.getRoot().getChildren().get(0);
			BooleanProperty selected = (BooleanProperty) selectionColumn.getCellObservableValue(portfolioItem);
			selected.set(true);
			assertEquals(List.of(portfolioAccount), panel.getCheckedAccounts());
			GridPane settlementDetails = (GridPane) tabs.getTabs().get(1).getContent();
			Button editButton = settlementDetails.lookupAll(".button").stream()
					.filter(Button.class::isInstance)
					.map(Button.class::cast)
					.filter(button -> BaseMessages.getTextStatic("UI_STOCK_ACTION_EDIT_SETTLEMENT_ACCOUNT").equals(button.getText()))
					.findFirst().orElseThrow();
			assertTrue(editButton.getStyleClass().contains("gbanking-form-button"));
			assertEquals(4, GridPane.getRowIndex(editButton.getParent()));
			GridPane portfolioDetails = (GridPane) tabs.getTabs().get(0).getContent();
			assertEquals(3, portfolioDetails.lookupAll(".gbanking-form-button").size());
			assertTrue(portfolioDetails.lookupAll(".gbanking-button-bar").stream()
					.allMatch(node -> Integer.valueOf(4).equals(GridPane.getRowIndex(node))));
		});
	}

	@Test
	void shouldShowOnlyFinTsCapablePortfoliosWhenOnlineFilterIsEnabled() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		BankingCapabilityService capabilityService = mock(BankingCapabilityService.class);
		PortfolioSummary onlinePortfolio = portfolio(1, "Online-Depot");
		PortfolioSummary offlinePortfolio = portfolio(2, "Offline-Depot");
		BankAccount onlineAccount = portfolioAccount(11, false);
		BankAccount offlineAccount = portfolioAccount(12, true);
		when(service.getPortfolios()).thenReturn(List.of(onlinePortfolio, offlinePortfolio));
		when(service.getPortfolioAccount(onlinePortfolio)).thenReturn(onlineAccount);
		when(service.getPortfolioAccount(offlinePortfolio)).thenReturn(offlineAccount);
		when(capabilityService.supportsStockPortfolio(onlineAccount)).thenReturn(true);
		when(capabilityService.supportsStockPortfolio(offlineAccount)).thenReturn(false);
		GuiContext.setOnlyOnlineAccountsVisible(true);

		JavaFxTestSupport.runFx(() -> {
			StockPortfolioOverviewPanel panel = new StockPortfolioOverviewPanel(service, capabilityService);
			panel.refreshOnShow();
			TreeTableView<?> tree = navigationTree(panel);
			assertEquals(1, tree.getRoot().getChildren().size());
			assertEquals(onlinePortfolio, panel.getSelectedPortfolio());

			GuiContext.setOnlyOnlineAccountsVisible(false);
			panel.refreshOnShow();
			assertEquals(2, tree.getRoot().getChildren().size());
		});
	}

	private static PortfolioSummary portfolio(int id, String name) {
		return new PortfolioSummary(id, name, "Bank", "DEPOT-" + id, LocalDate.of(2026, 1, 1),
				100 + id, "Verrechnung", "Bank", "DE123", Currency.EUR, BigDecimal.ZERO);
	}

	private static BankAccount portfolioAccount(int id, boolean offline) {
		BankAccount account = new BankAccount();
		account.setId(id);
		account.setAccountType(AccountType.DEPOT);
		account.setOfflineAccount(offline);
		return account;
	}

	private static TreeTableView<?> navigationTree(StockPortfolioOverviewPanel panel) {
		SplitPane mainPane = (SplitPane) panel.getChildren().get(1);
		VBox navigation = (VBox) mainPane.getItems().get(0);
		return (TreeTableView<?>) navigation.getChildren().get(1);
	}

	@Test
	void shouldFormatWholeQuantitiesWithoutDecimalPlaces() {
		assertEquals("1.234", StockPortfolioOverviewPanel.createQuantityFormat().format(new BigDecimal("1234.00")));
		assertEquals("1.234,5", StockPortfolioOverviewPanel.createQuantityFormat().format(new BigDecimal("1234.50")));
	}

	@Test
	void shouldApplyPerformanceColorsByComparisonWithAcquisitionValue() {
		JavaFxTestSupport.runFx(() -> {
			TableCell<Object, Object> cell = new TableCell<>();

			StockPortfolioOverviewPanel.applyPerformanceStyle(cell, BigDecimal.ONE);
			assertTrue(cell.getStyleClass().contains("amount-positive"));
			StockPortfolioOverviewPanel.applyPerformanceStyle(cell, BigDecimal.ONE.negate());
			assertTrue(cell.getStyleClass().contains("amount-negative"));
			StockPortfolioOverviewPanel.applyPerformanceStyle(cell, BigDecimal.ZERO);
			assertTrue(cell.getStyleClass().contains("amount-neutral"));
		});
	}
}
