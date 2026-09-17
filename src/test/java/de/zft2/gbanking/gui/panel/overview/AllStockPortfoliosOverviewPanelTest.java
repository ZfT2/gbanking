package de.zft2.gbanking.gui.panel.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.stock.StockPortfolioManagementDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockPortfolioManagementListPanel;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioDetails;
import javafx.scene.control.TableView;

class AllStockPortfoliosOverviewPanelTest {

	@AfterEach
	void closeDatabase() {
		GuiContext.setOnlyOnlineAccountsVisible(false);
		DBControllerTestUtil.closeAndNullifyConnection();
	}

	@Test
	void shouldUseAllAccountsStyleDetailAndListLayout() {
		StockPortfolioAdministrationService service = mock(StockPortfolioAdministrationService.class);
		when(service.getPortfolios()).thenReturn(List.of(mock(PortfolioDetails.class), mock(PortfolioDetails.class)));
		GuiContext.setOnlyOnlineAccountsVisible(true);

		JavaFxTestSupport.runFx(() -> {
			AllStockPortfoliosOverviewPanel panel = new AllStockPortfoliosOverviewPanel(service);
			panel.refreshOnShow();

			assertEquals(PageContext.ALL_STOCK_PORTFOLIOS, panel.getPageContext());
			DetailListPane content = assertInstanceOf(DetailListPane.class, panel.getChildren().get(1));
			assertInstanceOf(StockPortfolioManagementDetailPanel.class, content.getTop());
			StockPortfolioManagementListPanel listPanel = assertInstanceOf(
					StockPortfolioManagementListPanel.class, content.getCenter());
			TableView<?> table = assertInstanceOf(TableView.class, listPanel.getCenter());
			assertEquals(8, table.getColumns().size());
			assertEquals(2, table.getItems().size());
		});
	}
}
