package de.zft2.gbanking.gui.panel.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.stock.StockSecurityDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityListPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityPricePanel;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityHolding;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

class AllSecuritiesOverviewPanelTest {

	@Test
	@SuppressWarnings("unchecked")
	void securityFilterShouldCombineUsageAndPortfolioChoicesWithTextSearchAndSurviveReload() {
		StockSecurityAdministrationService service = mock(StockSecurityAdministrationService.class);
		StockPortfolioService portfolios = mock(StockPortfolioService.class);
		SecurityDetails historical = security(1, "Historical"), held = security(2, "Held"), unused = security(3, "Unused");
		when(service.getSecurities(false)).thenReturn(List.of(historical, held, unused));
		when(service.getSecurityUsage()).thenReturn(new StockSecurityAdministrationService.SecurityUsage(
				Set.of(1, 2), Map.of(10, Set.of(2))));
		when(portfolios.getPortfolios()).thenReturn(List.of(new PortfolioSummary(10, "Depot A", "Bank", "",
				LocalDate.of(2026, 1, 1), 11, "", "", "", Currency.EUR, BigDecimal.ZERO)));
		GuiContext.setOnlySecuritiesWithHoldingsVisible(false);
		JavaFxTestSupport.runFx(() -> {
			AtomicReference<SecurityDetails> selected = new AtomicReference<>();
			StockSecurityListPanel list = new StockSecurityListPanel(service, portfolios, selected::set);
			list.reload(null);
			javafx.scene.Scene scene = new javafx.scene.Scene(list, 520, 600);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			list.applyCss();
			list.layout();
			ComboBox<?> filter = (ComboBox<?>) ((HBox) list.getTop()).getChildren().get(2);
			assertEquals(200, filter.getWidth(), 1);
			list.resize(320, 600);
			list.layout();
			assertTrue(filter.getWidth() >= 118 * 4.0 / 3);
			assertTrue(filter.getBoundsInParent().getMaxX() <= list.getWidth());
			TableView<SecurityDetails> table = (TableView<SecurityDetails>) list.getCenter();
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_SECURITY_FILTER"), filter.getAccessibleText());
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(List.of(historical, held, unused), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(1);
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(List.of(historical, held), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(2);
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(3);
			assertEquals("Bestand in Depot A", filter.getValue().toString());
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			list.reload(2);
			assertEquals(3, filter.getSelectionModel().getSelectedIndex());
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(held, selected.get());
			filter.getSelectionModel().select(4);
			assertEquals(List.of(unused), List.copyOf(table.getItems()));
			assertNull(selected.get());
			TextField text = (TextField) ((HBox) list.getBottom()).getChildren().get(1);
			text.setText("Held");
			filter.getSelectionModel().select(1);
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(4);
			assertTrue(table.getItems().isEmpty());
			text.clear();
			list.reload(null);
			when(portfolios.getPortfolios()).thenReturn(List.of());
			filter.getSelectionModel().select(3);
			list.reload(null);
			assertEquals(0, filter.getSelectionModel().getSelectedIndex());
			GuiContext.setOnlySecuritiesWithHoldingsVisible(true);
			list.reload(null);
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			GuiContext.setOnlySecuritiesWithHoldingsVisible(false);
			list.reload(null);
			assertEquals(List.of(historical, held, unused), List.copyOf(table.getItems()));
		});
	}

	private static SecurityDetails security(int id, String name) {
		return new SecurityDetails(id, name, "", "", null, null, StockQuantityType.UNITS,
				null, Currency.EUR, null, null, null, "", "", "", "", null, null);
	}

	@AfterEach
	void closeDatabase() {
		GuiContext.setOnlySecuritiesWithHoldingsVisible(false);
		GuiContext.setStockPortfolioNavigationHandler(null);
		DBControllerTestUtil.closeAndNullifyConnection();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@SuppressWarnings("unchecked")
	void holdingsButtonShouldShowPositionsAndNavigateOnSingleRowClick(boolean hasHoldings) {
		StockSecurityAdministrationService securities = mock(StockSecurityAdministrationService.class);
		StockPortfolioService portfolios = mock(StockPortfolioService.class);
		SecurityDetails security = new SecurityDetails(5, "Aktie", "", "", null, null, StockQuantityType.UNITS,
				null, Currency.EUR, null, null, null, "", "", "", "", null, null);
		PortfolioSummary portfolio = new PortfolioSummary(2, "Depot B", "Bank", "", LocalDate.of(2026, 1, 1),
				3, "Konto", "", "", Currency.EUR, BigDecimal.ZERO);
		SecurityHolding holding = new SecurityHolding(portfolio, StockQuantityType.UNITS, new BigDecimal("0.123456789"));
		when(securities.getSecurities(false)).thenReturn(List.of(security));
		when(portfolios.getSecurityHoldings(5)).thenReturn(hasHoldings ? List.of(holding) : List.of());
		AtomicReference<Integer> navigated = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		GuiContext.setStockPortfolioNavigationHandler(id -> navigated.set(id));
		JavaFxTestSupport.runFx(() -> {
			AllSecuritiesOverviewPanel panel = new AllSecuritiesOverviewPanel(securities, portfolios);
			panel.refreshOnShow();
			MasterContentPane content = (MasterContentPane) panel.getChildren().get(1);
			StockSecurityListPanel list = (StockSecurityListPanel) content.getItems().get(0);
			StockSecurityDetailPanel details = (StockSecurityDetailPanel) ((VBox) content.getItems().get(1)).getChildren().get(0);
			new javafx.scene.Scene(panel, 1280, 900);
			panel.resize(1280, 900);
			panel.applyCss();
			panel.layout();
			Button button = details.lookupAll(".button").stream().filter(node -> node instanceof Button)
					.map(node -> (Button) node).filter(item -> item.getText().equals(BaseMessages.getTextStatic("UI_STOCK_SECURITY_HOLDINGS")))
					.findFirst().orElseThrow();
			assertTrue(button.isDisabled());
			((TableView<SecurityDetails>) list.getCenter()).getSelectionModel().selectFirst();
			HBox bar = (HBox) button.getParent();
			assertEquals(button, bar.getChildren().get(bar.getChildren().size() - 1));
			assertEquals(Priority.ALWAYS, HBox.getHgrow(bar.getChildren().get(bar.getChildren().size() - 2)));
			panel.layout();
			assertEquals(bar.getWidth(), button.getBoundsInParent().getMaxX(), 1);
			Platform.runLater(() -> {
				Stage dialog = (Stage) Window.getWindows().stream().filter(window -> window.isShowing()).findFirst().orElseThrow();
				try {
					TableView<SecurityHolding> table = (TableView<SecurityHolding>) ((DialogPane) dialog.getScene().getRoot()).getContent();
					assertEquals(hasHoldings ? List.of(holding) : List.of(), List.copyOf(table.getItems()));
					if (!hasHoldings) {
						assertEquals(BaseMessages.getTextStatic("UI_STOCK_SECURITY_NO_HOLDINGS"), ((Label) table.getPlaceholder()).getText());
						((Button) ((DialogPane) dialog.getScene().getRoot()).lookupButton(ButtonType.CLOSE)).fire();
						return;
					}
					assertEquals("Depot B", table.getColumns().get(0).getCellData(0));
					assertEquals("0,123456789", table.getColumns().get(2).getCellData(0));
					TableRow<SecurityHolding> row = table.getRowFactory().call(table);
					row.updateTableView(table);
					row.updateIndex(0);
					row.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
							MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null));
				} catch (AssertionError | RuntimeException error) {
					failure.set(error);
				} finally {
					dialog.close();
				}
			});
			button.fire();
		});
		assertNull(failure.get());
		assertEquals(hasHoldings ? 2 : null, navigated.get());
	}

	@Test
	void shouldUseMasterDetailLayoutWithPriceTable() {
		StockSecurityAdministrationService securityService = mock(StockSecurityAdministrationService.class);
		StockPortfolioService portfolioService = mock(StockPortfolioService.class);
		when(securityService.getSecurities(false)).thenReturn(List.of());

		JavaFxTestSupport.runFx(() -> {
			AllSecuritiesOverviewPanel panel = new AllSecuritiesOverviewPanel(securityService, portfolioService);
			panel.refreshOnShow();

			assertEquals(PageContext.ALL_SECURITIES, panel.getPageContext());
			MasterContentPane content = assertInstanceOf(MasterContentPane.class, panel.getChildren().get(1));
			StockSecurityListPanel list = assertInstanceOf(StockSecurityListPanel.class, content.getItems().get(0));
			assertEquals(3, assertInstanceOf(TableView.class, list.getCenter()).getColumns().size());
			VBox details = assertInstanceOf(VBox.class, content.getItems().get(1));
			assertInstanceOf(StockSecurityDetailPanel.class, details.getChildren().get(0));
			StockSecurityPricePanel prices = assertInstanceOf(StockSecurityPricePanel.class,
					details.getChildren().get(1));
			assertEquals(5, assertInstanceOf(TableView.class, prices.getChildren().get(1)).getColumns().size());
		});
	}
}
