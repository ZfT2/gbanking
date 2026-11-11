package de.zft2.gbanking.gui.dialog.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionOperation;
import de.zft2.gbanking.db.dao.enu.StockCorporateActionType;
import de.zft2.gbanking.db.dao.stock.StockCorporateAction;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.service.stock.StockCorporateActionService;
import de.zft2.gbanking.service.stock.StockCorporateActionService.OperationRequest;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

class StockCorporateActionDialogTest {
	@Test
	@SuppressWarnings("unchecked")
	void subscriptionShouldUseTheSelectedActionAndOnlyOfferMatchingOperations() {
		StockCorporateActionService service = mock(StockCorporateActionService.class);
		StockPortfolioService portfolios = mock(StockPortfolioService.class);
		StockCorporateAction action = new StockCorporateAction();
		action.setId(7);
		action.setName("Bezugsrecht");
		action.setActionType(StockCorporateActionType.RIGHTS_ISSUE);
		action.setEffectiveDate(LocalDate.of(2026, 9, 15));
		action.setCurrency(Currency.EUR);
		action.setAllocationFromE9(1_000_000_000L);
		action.setAllocationToE9(1_000_000_000L);
		action.setRatioFromE9(10_000_000_000L);
		action.setRatioToE9(1_000_000_000L);
		when(service.getActions()).thenReturn(List.of(action));
		PortfolioSummary portfolio = new PortfolioSummary(1, "Depot", "Bank", "123", LocalDate.of(2026, 1, 1),
				2, "Verrechnung", "Bank", "DE123", Currency.EUR, BigDecimal.ZERO);

		JavaFxTestSupport.runFx(() -> {
			StockCorporateActionDialog dialog = new StockCorporateActionDialog(null, service, portfolios, portfolio, () -> {});
			TabPane tabs = (TabPane) dialog.dialog().getScene().getRoot().lookup(".tab-pane");
			GridPane form = (GridPane) ((ScrollPane) tabs.getTabs().get(0).getContent()).getContent();
			ComboBox<StockCorporateAction> selected = (ComboBox<StockCorporateAction>) form.lookupAll(".combo-box").stream()
					.filter(node -> BaseMessages.getTextStatic("UI_STOCK_CORPORATE_ACTION").equals(node.getAccessibleText()))
					.findFirst().orElseThrow();
			selected.setValue(action);
			ComboBox<StockCorporateActionOperation> operation = (ComboBox<StockCorporateActionOperation>) form.lookupAll(".combo-box").stream()
					.filter(node -> BaseMessages.getTextStatic("UI_STOCK_CORPORATE_OPERATION").equals(node.getAccessibleText()))
					.findFirst().orElseThrow();
			assertEquals(5, operation.getItems().size());
			operation.setValue(StockCorporateActionOperation.SUBSCRIPTION);
			TextField quantity = (TextField) form.lookupAll(".text-field").stream()
					.filter(node -> BaseMessages.getTextStatic("UI_STOCK_CORPORATE_QUANTITY").equals(node.getAccessibleText()))
					.findFirst().orElseThrow();
			quantity.setText("100");
			((Button) form.lookupAll(".button").stream().filter(Button.class::isInstance)
					.filter(node -> ((Button) node).getText().equals(BaseMessages.getTextStatic("UI_STOCK_CORPORATE_RECORD")))
					.findFirst().orElseThrow()).fire();
			verify(service).record(portfolio, new OperationRequest(7, StockCorporateActionOperation.SUBSCRIPTION,
					action.getEffectiveDate(), new BigDecimal("100"), null, null, null, null, null));
			dialog.dialog().close();
		});
	}
}
