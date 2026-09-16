package de.zft2.gbanking.gui.panel.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.stock.StockSecurityDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityListPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityPricePanel;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

class AllSecuritiesOverviewPanelTest {

	@AfterEach
	void closeDatabase() {
		DBControllerTestUtil.closeAndNullifyConnection();
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
