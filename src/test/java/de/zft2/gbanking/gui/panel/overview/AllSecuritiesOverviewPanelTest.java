package de.zft2.gbanking.gui.panel.overview;

import static de.zft2.gbanking.service.ServiceStubbingUtil.createWithServices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityHolding;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecuritySaveRequest;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

class AllSecuritiesOverviewPanelTest {

	@Test
	void productSelectionShouldShowNonSelectableHeadingsAndSaveSubtype() {
		StockSecurityAdministrationService service = mock(StockSecurityAdministrationService.class);
		SecurityDetails saved = new SecurityDetails(5, "Zertifikat", "", "", null, StockSecurityType.KNOCK_OUT_WARRANT,
				StockQuantityType.UNITS, null, Currency.EUR, StockQuotationType.ABSOLUTE, null,
				StockSecurityState.ACTIVE, "", "", "", "", null, null);
		when(service.save(any())).thenReturn(saved);
		JavaFxTestSupport.runFx(() -> {
			StockSecurityDetailPanel panel = createWithServices(() -> new StockSecurityDetailPanel(null), service);
			Stage stage = new Stage();
			stage.setScene(new javafx.scene.Scene(panel, 1200, 650));
			try {
				stage.show();
				panel.editSecurity(null);
				ComboBox<?> combo = assertInstanceOf(ComboBox.class, panel.lookup("#security-type"));
				assertEquals(StockSecurityType.UNKNOWN, combo.getValue());
				panel.editSecurity(saved);
				combo.getSelectionModel().select(combo.getItems().indexOf(StockSecurityType.UNKNOWN));
				combo.show();
				ComboBoxListViewSkin<?> skin = assertInstanceOf(ComboBoxListViewSkin.class, combo.getSkin());
				ListView<?> popup = assertInstanceOf(ListView.class, skin.getPopupContent());
				popup.scrollTo(combo.getItems().indexOf(StockSecurityType.KNOCK_OUT_WARRANT));
				popup.layout();
				popup.applyCss();
				ListCell<?> cell = popup.lookupAll(".list-cell").stream().filter(node -> node instanceof ListCell<?>)
						.map(node -> (ListCell<?>) node).filter(item -> item.getItem() == StockSecurityType.KNOCK_OUT_WARRANT)
						.findFirst().orElseThrow();
				VBox content = assertInstanceOf(VBox.class, cell.getGraphic());
				assertEquals(3, content.getChildren().size());
				for (int index = 0; index < 2; index++) {
					Label heading = assertInstanceOf(Label.class, content.getChildren().get(index));
					assertTrue(heading.getStyleClass().contains("security-type-heading"));
					clickChoice(heading);
					assertEquals(StockSecurityType.UNKNOWN, combo.getValue());
					assertTrue(combo.isShowing());
				}
				clickChoice(content.getChildren().get(2));
				assertEquals(StockSecurityType.KNOCK_OUT_WARRANT, combo.getValue());
				combo.hide();
				panel.lookupAll(".button").stream().filter(node -> node instanceof Button)
						.map(node -> (Button) node).filter(button -> button.getText().equals(BaseMessages.getTextStatic("UI_BUTTON_SAVE")))
						.findFirst().orElseThrow().fire();
				assertEquals(StockSecurityType.KNOCK_OUT_WARRANT, combo.getValue());
				assertTrue(combo.isDisabled());
				panel.updatePanel(null);
				panel.updatePanel(saved);
				assertEquals(StockSecurityType.KNOCK_OUT_WARRANT, combo.getValue());
			} finally {
				stage.close();
			}
		});
		ArgumentCaptor<SecuritySaveRequest> request = ArgumentCaptor.forClass(SecuritySaveRequest.class);
		verify(service).save(request.capture());
		assertEquals(StockSecurityType.KNOCK_OUT_WARRANT, request.getValue().securityType());
	}

	private static void clickChoice(Node node) {
		var point = node.localToScene(1, 1);
		for (var eventType : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
			node.fireEvent(new MouseEvent(eventType, point.getX(), point.getY(), 1, 1, MouseButton.PRIMARY, 1,
					false, false, false, false, eventType == MouseEvent.MOUSE_PRESSED, false, false, false, false, true,
					new PickResult(node, point.getX(), point.getY())));
		}
	}

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
				LocalDate.of(2026, Month.JANUARY, 1), 11, "", "", "", Currency.EUR, BigDecimal.ZERO)));
		GuiContext.setOnlySecuritiesWithHoldingsVisible(false);
		JavaFxTestSupport.runFx(() -> {
			AtomicReference<SecurityDetails> selected = new AtomicReference<>();
			StockSecurityListPanel list = createWithServices(() -> new StockSecurityListPanel(selected::set), service,
					portfolios);
			list.reload(null);
			javafx.scene.Scene scene = new javafx.scene.Scene(list, 520, 600);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			list.applyCss();
			list.layout();
			ComboBox<?> filter = (ComboBox<?>) ((HBox) list.getTop()).getChildren().get(2);
			assertEquals(200, filter.getWidth(), 1);
			list.resize(list.getMinWidth(), 600);
			list.layout();
			assertTrue(filter.getWidth() >= 118 * 4.0 / 3);
			assertTrue(filter.getBoundsInParent().getMaxX() <= list.getWidth());
			TableView<SecurityDetails> table = (TableView<SecurityDetails>) list.getCenter();
			assertEquals(BaseMessages.getTextStatic("UI_STOCK_SECURITY_FILTER"), filter.getAccessibleText());
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(2, filter.getSelectionModel().getSelectedIndex());
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			filter.getSelectionModel().selectFirst();
			list.reload(null);
			assertEquals(0, filter.getSelectionModel().getSelectedIndex());
			assertEquals(List.of(historical, held, unused), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(1);
			assertEquals(filter.getValue().toString(), filter.getButtonCell().getText());
			assertEquals(List.of(historical, held), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(2);
			assertEquals(List.of(held), List.copyOf(table.getItems()));
			filter.getSelectionModel().select(3);
			assertEquals(Messages.getInstance().getFormattedMessage("UI_STOCK_SECURITY_FILTER_PORTFOLIO", "Depot A"),
					filter.getValue().toString());
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
		ServiceRegistry.resetServices();
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
		PortfolioSummary portfolio = new PortfolioSummary(2, "Depot B", "Bank", "", LocalDate.of(2026, Month.JANUARY, 1),
				3, "Konto", "", "", Currency.EUR, BigDecimal.ZERO);
		SecurityHolding holding = new SecurityHolding(portfolio, StockQuantityType.UNITS, new BigDecimal("0.123456789"));
		when(securities.getSecurities(false)).thenReturn(List.of(security));
		when(securities.getSecurityUsage()).thenReturn(new StockSecurityAdministrationService.SecurityUsage(
				Set.of(5), hasHoldings ? Map.of(2, Set.of(5)) : Map.of()));
		when(portfolios.getSecurityHoldings(5)).thenReturn(hasHoldings ? List.of(holding) : List.of());
		AtomicReference<Integer> navigated = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		GuiContext.setStockPortfolioNavigationHandler(id -> navigated.set(id));
		JavaFxTestSupport.runFx(() -> {
			AllSecuritiesOverviewPanel panel = createWithServices(() -> new AllSecuritiesOverviewPanel(), securities,
					portfolios);
			panel.refreshOnShow();
			MasterContentPane content = (MasterContentPane) panel.getChildren().get(1);
			StockSecurityListPanel list = (StockSecurityListPanel) content.getItems().get(0);
			((ComboBox<?>) list.lookup("#security-filter")).getSelectionModel().selectFirst();
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
			AllSecuritiesOverviewPanel panel = createWithServices(() -> new AllSecuritiesOverviewPanel(), securityService,
					portfolioService);
			panel.refreshOnShow();

			assertEquals(PageContext.ALL_SECURITIES, panel.getPageContext());
			MasterContentPane content = assertInstanceOf(MasterContentPane.class, panel.getChildren().get(1));
			StockSecurityListPanel list = assertInstanceOf(StockSecurityListPanel.class, content.getItems().get(0));
			TableView<?> table = assertInstanceOf(TableView.class, list.getCenter());
			assertEquals(3, table.getColumns().size());
			javafx.scene.Scene scene = new javafx.scene.Scene(panel, 1600, 900);
			scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
			panel.resize(1600, 900);
			panel.applyCss();
			panel.layout();
			assertTrue(list.getWidth() >= 400);
			assertEquals(115, table.getColumns().get(1).getWidth(), 1);
			assertEquals(85, table.getColumns().get(2).getWidth(), 1);
			double previousNameWidth = table.getColumns().get(0).getWidth();
			double previousTableWidth = table.getWidth();
			list.resize(list.getWidth() + 60, list.getHeight());
			list.layout();
			assertTrue(table.getWidth() > previousTableWidth);
			assertEquals(previousNameWidth + table.getWidth() - previousTableWidth, table.getColumns().get(0).getWidth(), 1);
			assertEquals(115, table.getColumns().get(1).getWidth(), 1);
			assertEquals(85, table.getColumns().get(2).getWidth(), 1);
			VBox details = assertInstanceOf(VBox.class, content.getItems().get(1));
			assertInstanceOf(StockSecurityDetailPanel.class, details.getChildren().get(0));
			StockSecurityPricePanel prices = assertInstanceOf(StockSecurityPricePanel.class,
					details.getChildren().get(1));
			assertEquals(5, assertInstanceOf(TableView.class, prices.getChildren().get(1)).getColumns().size());
		});
	}
}
