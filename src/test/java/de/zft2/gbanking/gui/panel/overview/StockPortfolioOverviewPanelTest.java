package de.zft2.gbanking.gui.panel.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.stock.StockSecurityDetailPanel;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionHistory;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.TransactionSummary;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

class StockPortfolioOverviewPanelTest {

	@AfterEach
	void closeDatabase() {
		GuiContext.setOnlyOnlineAccountsVisible(false);
		DBControllerTestUtil.closeAndNullifyConnection();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	@SuppressWarnings("unchecked")
	void holdingsActionsShouldCreateOrEditSecurityRatherThanTransaction(boolean edit) {
		StockPortfolioService service = mock(StockPortfolioService.class);
		StockSecurityAdministrationService securities = mock(StockSecurityAdministrationService.class);
		PortfolioSummary portfolio = portfolio(1, "Depot");
		PositionSummary position = new PositionSummary(1, 2, "Aktie", null, null, StockQuantityType.UNITS,
				BigDecimal.TEN, null, null, null, null, Currency.EUR, null);
		SecurityDetails security = new SecurityDetails(2, "Aktie", "", "", null, StockSecurityType.STOCK,
				StockQuantityType.UNITS, null, Currency.EUR, StockQuotationType.ABSOLUTE, null,
				StockSecurityState.ACTIVE, "", "", "", "", null, null);
		when(service.getPortfolios()).thenReturn(List.of(portfolio));
		when(service.getPositions(1)).thenReturn(List.of(position));
		when(securities.getSecurities()).thenReturn(List.of(security));
		when(securities.save(any())).thenReturn(security);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		JavaFxTestSupport.runFx(() -> {
			StockPortfolioOverviewPanel panel = new StockPortfolioOverviewPanel(service, mock(BankingCapabilityService.class), securities);
			panel.refreshOnShow();
			VBox right = (VBox) ((SplitPane) panel.getChildren().get(1)).getItems().get(1);
			TabPane tabs = (TabPane) right.getChildren().get(0);
			tabs.getSelectionModel().select(1);
			VBox section = (VBox) ((StackPane) tabContent(tabs, 1).getChildren().get(1)).getChildren().get(0);
			TableView<PositionSummary> positions = (TableView<PositionSummary>) section.getChildren().get(1);
			assertTrue(tabButtons(tabs, 1).get(1).isDisabled());
			positions.getSelectionModel().selectFirst();
			assertFalse(tabButtons(tabs, 1).get(1).isDisabled());
			assertTrue(tabButtons(tabs, 1).get(2).isDisabled());
			Platform.runLater(() -> {
				Stage dialog = (Stage) Window.getWindows().stream().filter(window -> window.isShowing()).findFirst().orElseThrow();
				try {
					var root = dialog.getScene().getRoot();
					assertTrue(root instanceof StockSecurityDetailPanel);
					Label nameLabel = root.lookupAll(".label").stream().filter(node -> node instanceof Label)
							.map(node -> (Label) node).filter(label -> label.getText().equals(BaseMessages.getTextStatic("UI_STOCK_SECURITY_NAME")))
							.findFirst().orElseThrow();
					TextField name = (TextField) ((HBox) nameLabel.getParent()).getChildren().get(1);
					assertEquals(edit ? "Aktie" : "", name.getText());
					assertTrue(name.isEditable());
					name.setText("Geändert AG");
					root.lookupAll(".button").stream().filter(node -> node instanceof Button)
							.map(node -> (Button) node).filter(button -> button.getText().equals(BaseMessages.getTextStatic("UI_BUTTON_SAVE")))
							.findFirst().orElseThrow().fire();
				} catch (AssertionError | RuntimeException error) {
					failure.set(error);
				} finally {
					dialog.close();
				}
			});
			tabButtons(tabs, 1).get(edit ? 1 : 0).fire();
			assertEquals(tabs.getTabs().get(1), tabs.getSelectionModel().getSelectedItem());
		});
		assertNull(failure.get());
		verify(securities).save(argThat(request -> (edit ? Integer.valueOf(2).equals(request.securityId())
				: request.securityId() == null) && request.name().equals("Geändert AG")));
	}

	@Test
	@SuppressWarnings("unchecked")
	void positionHistoryShouldIncludePredecessorsByDefaultAndAllowReturningToPortfolio() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		PortfolioSummary portfolio = portfolio(1, "Depot");
		PositionSummary position = new PositionSummary(1, 2, "Neu AG", null, null, StockQuantityType.UNITS,
				BigDecimal.TEN, null, null, null, null, Currency.EUR, null);
		TransactionSummary oldTrade = new TransactionSummary(1, StockTransactionType.BUY, 1, "Alt AG", null, null,
				LocalDate.of(2026, Month.JANUARY, 1), BigDecimal.TEN, null, null, Currency.EUR, 0, false, null);
		TransactionSummary newTrade = new TransactionSummary(2, StockTransactionType.CORPORATE_ACTION, 2, "Neu AG", null, null,
				LocalDate.of(2026, Month.FEBRUARY, 1), BigDecimal.TEN, null, null, Currency.EUR, 0, false, null);
		when(service.getPortfolios()).thenReturn(List.of(portfolio));
		when(service.getPositions(1)).thenReturn(List.of(position));
		when(service.getTransactions(1)).thenReturn(List.of(oldTrade, newTrade));
		when(service.getPositionHistory(1, 2, true)).thenReturn(new PositionHistory(List.of("Neu AG", "Alt AG"), List.of(oldTrade, newTrade)));
		when(service.getPositionHistory(1, 2, false)).thenReturn(new PositionHistory(List.of("Neu AG"), List.of(newTrade)));

		JavaFxTestSupport.runFx(() -> {
			StockPortfolioOverviewPanel panel = new StockPortfolioOverviewPanel(service, mock(BankingCapabilityService.class));
			panel.refreshOnShow();
			SplitPane main = (SplitPane) panel.getChildren().get(1);
			VBox right = (VBox) main.getItems().get(1);
			TabPane tabs = (TabPane) right.getChildren().get(0);
			SplitPane tables = (SplitPane) tabContent(tabs, 0).getChildren().get(1);
			TableView<PositionSummary> positions = (TableView<PositionSummary>) tableSection(tables, 0).getChildren().get(1);
			VBox transactionSection = tableSection(tables, 1);
			TableView<TransactionSummary> transactions = (TableView<TransactionSummary>) transactionSection.getChildren().get(1);
			var transactionRow = transactions.getRowFactory().call(transactions);
			transactionRow.setItem(oldTrade);
			assertTrue(transactionRow.getContextMenu().getItems().stream()
					.filter(item -> item.getText().equals(BaseMessages.getTextStatic("UI_STOCK_CONVERT_TO_TRANSFER")))
					.findFirst().orElseThrow().isDisable());
			TransactionSummary delivery = new TransactionSummary(3, StockTransactionType.DELIVERY_IN, 2, "Neu AG", null, null,
					LocalDate.of(2026, 2, 1), BigDecimal.TEN, null, null, Currency.EUR, 0, true, null);
			transactionRow.setItem(delivery);
			assertFalse(transactionRow.getContextMenu().getItems().stream()
					.filter(item -> item.getText().equals(BaseMessages.getTextStatic("UI_STOCK_CONVERT_TO_TRANSFER")))
					.findFirst().orElseThrow().isDisable());
			var row = positions.getRowFactory().call(positions);
			row.setItem(position);
			row.getContextMenu().getItems().stream()
					.filter(item -> item.getText().equals(BaseMessages.getTextStatic("UI_STOCK_POSITION_HISTORY")))
					.findFirst().orElseThrow().fire();

			HBox header = (HBox) transactionSection.getChildren().get(0);
			CheckBox include = (CheckBox) header.getChildren().get(1);
			assertTrue(include.isSelected());
			assertEquals(List.of(oldTrade, newTrade), List.copyOf(transactions.getItems()));
			include.fire();
			assertEquals(List.of(newTrade), List.copyOf(transactions.getItems()));
			assertEquals(tabs.getTabs().get(2), tabs.getSelectionModel().getSelectedItem());
			tabs.getSelectionModel().select(3);
			assertTrue(panel.selectTransaction(1, oldTrade.transactionId()));
			assertEquals(oldTrade, transactions.getSelectionModel().getSelectedItem());
			assertEquals(tabs.getTabs().get(2), tabs.getSelectionModel().getSelectedItem());
			assertFalse(include.isVisible());
			((Button) header.getChildren().get(2)).fire();
			assertEquals(List.of(oldTrade, newTrade), List.copyOf(transactions.getItems()));
			assertFalse(include.isVisible());
		});
	}

	@Test
	void transactionNavigationShouldSelectTheMatchingPortfolioRatherThanTheFirst() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		PortfolioSummary first = portfolio(1, "Erstes Depot"), target = portfolio(2, "Zieldepot");
		TransactionSummary transaction = new TransactionSummary(7, StockTransactionType.BUY, 2, "Aktie", null, null,
				LocalDate.of(2026, Month.JANUARY, 1), BigDecimal.ONE, null, null, Currency.EUR, 0, false, null);
		when(service.getPortfolios()).thenReturn(List.of(first, target));
		when(service.getTransactions(2)).thenReturn(List.of(transaction));
		Booking booking = new Booking();
		booking.setId(42);
		booking.setAccountId(101);
		booking.setAccountName("Früheres Verrechnungskonto");
		when(service.getSettlementBookings(2)).thenReturn(Map.of(7, List.of(booking)));
		JavaFxTestSupport.runFx(() -> {
			StockPortfolioOverviewPanel panel = new StockPortfolioOverviewPanel(service, mock(BankingCapabilityService.class));
			panel.refreshOnShow();
			assertEquals(first, panel.getSelectedPortfolio());
			assertTrue(panel.selectTransaction(2, 7));
			assertEquals(target, panel.getSelectedPortfolio());
			SplitPane main = (SplitPane) panel.getChildren().get(1);
			VBox right = (VBox) main.getItems().get(1);
			TabPane tabs = (TabPane) right.getChildren().get(0);
			VBox transactionSection = (VBox) ((StackPane) tabContent(tabs, 2).getChildren().get(1)).getChildren().get(0);
			@SuppressWarnings("unchecked")
			TableView<TransactionSummary> transactions = (TableView<TransactionSummary>) transactionSection.getChildren().get(1);
			@SuppressWarnings("unchecked")
			TableColumn<TransactionSummary, List<Booking>> column =
					(TableColumn<TransactionSummary, List<Booking>>) transactions.getColumns().get(9);
			TableCell<TransactionSummary, List<Booking>> cell = column.getCellFactory().call(column);
			cell.updateTableView(transactions);
			cell.updateTableColumn(column);
			cell.updateIndex(0);
			AtomicReference<Booking> navigated = new AtomicReference<>();
			GuiContext.setAccountBookingNavigationHandler(item -> navigated.set(item));
			try {
				Hyperlink link = (Hyperlink) ((VBox) cell.getGraphic()).getChildren().get(0);
				assertEquals(booking.getAccountName(), link.getText());
				link.fire();
				assertEquals(booking, navigated.get());
			} finally {
				GuiContext.setAccountBookingNavigationHandler(null);
			}
			cell.updateIndex(-1);
			assertNull(cell.getGraphic());
			assertFalse(panel.selectTransaction(2, 999));
			assertFalse(panel.selectTransaction(999, 7));
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldProvidePortfolioAndSettlementAccountLayout() {
		StockPortfolioService service = mock(StockPortfolioService.class);
		BankingCapabilityService capabilityService = mock(BankingCapabilityService.class);
		PortfolioSummary portfolio = new PortfolioSummary(1, "Depot A", "Bank A", "DE123",
				LocalDate.of(2026, Month.JANUARY, 1), 2, "Girokonto", "Bank A", "DE456", Currency.EUR,
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
			new javafx.scene.Scene(panel, 1280, 900);
			panel.resize(1280, 900);
			panel.applyCss();
			panel.layout();

			assertEquals(PageContext.STOCK_PORTFOLIOS, panel.getPageContext());
			SplitPane mainPane = (SplitPane) panel.getChildren().get(1);
			VBox navigation = (VBox) mainPane.getItems().get(0);
			VBox right = (VBox) mainPane.getItems().get(1);
			TabPane tabs = (TabPane) right.getChildren().get(0);
			assertEquals(Priority.ALWAYS, VBox.getVgrow(tabs));
			assertEquals(List.of(BaseMessages.getTextStatic("UI_STOCK_TAB_PORTFOLIO"),
					BaseMessages.getTextStatic("UI_STOCK_TAB_POSITIONS"), BaseMessages.getTextStatic("UI_STOCK_TAB_TRANSACTIONS"),
					BaseMessages.getTextStatic("UI_STOCK_TAB_SETTLEMENT_ACCOUNT")),
					tabs.getTabs().stream().map(tab -> tab.getText()).toList());
			SplitPane portfolioTables = (SplitPane) tabContent(tabs, 0).getChildren().get(1);
			assertEquals(Orientation.VERTICAL, portfolioTables.getOrientation());
			VBox positionSection = tableSection(portfolioTables, 0);
			VBox transactionSection = tableSection(portfolioTables, 1);
			TreeTableView<?> tree = (TreeTableView<?>) navigation.getChildren().get(1);
			assertTrue(navigation.lookupAll(".button").isEmpty());
			assertFalse(tree.isShowRoot());
			assertEquals(2, tree.getColumns().size());
			assertEquals(1, tree.getRoot().getChildren().size());
			assertEquals(1, tree.getRoot().getChildren().get(0).getChildren().size());
			TableView<?> positions = (TableView<?>) positionSection.getChildren().get(1);
			double overviewHeight = positions.getHeight();
			assertEquals(10, positions.getColumns().size());
			assertFalse(positions.isEditable());
			assertFalse(positions.getColumns().get(4).isEditable());
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_COLUMN_ACQUISITION_VALUE"),
					positions.getColumns().get(5).getText());
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_COLUMN_PERFORMANCE"),
					positions.getColumns().get(9).getText());
			TableView<?> transactions = (TableView<?>) transactionSection.getChildren().get(1);
			assertEquals(10, transactions.getColumns().size());
			assertEquals(BaseMessages.getTextStatic("UI_TABLE_COUNTER_ACCOUNT"), transactions.getColumns().get(9).getText());
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
			GridPane settlementDetails = (GridPane) tabContent(tabs, 3).getChildren().get(0);
			Button editButton = settlementDetails.lookupAll(".button").stream()
					.filter(Button.class::isInstance)
					.map(Button.class::cast)
					.filter(button -> BaseMessages.getTextStatic("UI_STOCK_ACTION_EDIT_SETTLEMENT_ACCOUNT").equals(button.getText()))
					.findFirst().orElseThrow();
			assertTrue(editButton.getStyleClass().contains("gbanking-form-button"));
			assertEquals(4, GridPane.getRowIndex(editButton.getParent()));
			GridPane portfolioDetails = (GridPane) tabContent(tabs, 0).getChildren().get(0);
			assertTrue(portfolioDetails.lookupAll(".button").isEmpty());
			assertEquals(List.of("UI_BUTTON_NEW", "UI_BUTTON_EDIT", "UI_BUTTON_DELETE", "UI_STOCK_ACTION_EXCHANGE")
					.stream().map(key -> BaseMessages.getTextStatic(key)).toList(), tabButtons(tabs, 1).stream().map(button -> button.getText()).toList());
			assertEquals(List.of("UI_BUTTON_NEW", "UI_BUTTON_EDIT", "UI_BUTTON_DELETE", "UI_STOCK_CORPORATE_TITLE")
					.stream().map(key -> BaseMessages.getTextStatic(key)).toList(), tabButtons(tabs, 2).stream().map(button -> button.getText()).toList());
			tabs.getSelectionModel().select(1);
			panel.applyCss();
			panel.layout();
			assertTrue(positions.getHeight() > overviewHeight);
			assertEquals(positionSection, ((StackPane) tabContent(tabs, 1).getChildren().get(1)).getChildren().get(0));
			assertEquals(Priority.ALWAYS, VBox.getVgrow(positionSection.getParent()));
			assertEquals(Priority.ALWAYS, VBox.getVgrow(positions));
			tabs.getSelectionModel().select(2);
			assertEquals(transactionSection, ((StackPane) tabContent(tabs, 2).getChildren().get(1)).getChildren().get(0));
			assertEquals(Priority.ALWAYS, VBox.getVgrow(transactionSection.getParent()));
			assertEquals(Priority.ALWAYS, VBox.getVgrow(transactions));
			panel.refreshOnShow();
			assertEquals(tabs.getTabs().get(2), tabs.getSelectionModel().getSelectedItem());
			tabs.getSelectionModel().select(3);
			assertTrue(((VBox) tabContent(tabs, 3).getChildren().get(1)).getChildren().get(1) instanceof TableView);
			tabs.getSelectionModel().select(0);
			assertEquals(portfolioTables, tabContent(tabs, 0).getChildren().get(1));
			assertEquals(positionSection, tableSection(portfolioTables, 0));
			assertEquals(transactionSection, tableSection(portfolioTables, 1));
		});
	}

	private static VBox tableSection(SplitPane tables, int index) {
		return (VBox) ((StackPane) tables.getItems().get(index)).getChildren().get(0);
	}

	private static List<Button> tabButtons(TabPane tabs, int index) {
		return ((HBox) tabContent(tabs, index).getChildren().get(0)).getChildren().stream()
				.filter(node -> node instanceof Button).map(node -> (Button) node).toList();
	}

	private static VBox tabContent(TabPane tabs, int index) {
		return (VBox) tabs.getTabs().get(index).getContent();
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
		return new PortfolioSummary(id, name, "Bank", "DEPOT-" + id, LocalDate.of(2026, Month.JANUARY, 1),
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
