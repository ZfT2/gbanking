package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.stock.StockSecurityDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityListPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityPricePanel;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AllSecuritiesOverviewPanel extends OverviewBasePanel {

	private static final double MASTER_DIVIDER = 0.28;

	private final StockSecurityDetailPanel detailPanel;
	private final StockSecurityPricePanel pricePanel;
	private final StockSecurityListPanel listPanel;

	public AllSecuritiesOverviewPanel() {
		this(ServiceRegistry.getService(StockSecurityAdministrationService.class),
				ServiceRegistry.getService(StockPortfolioService.class));
	}

	AllSecuritiesOverviewPanel(StockSecurityAdministrationService securityService,
			StockPortfolioService portfolioService) {
		setPageContext(PageContext.ALL_SECURITIES);
		detailPanel = new StockSecurityDetailPanel(securityService, this::reloadAndSelect);
		pricePanel = new StockSecurityPricePanel(portfolioService);
		listPanel = new StockSecurityListPanel(securityService, this::showSecurity);

		VBox content = new VBox(detailPanel, pricePanel);
		VBox.setVgrow(pricePanel, Priority.ALWAYS);
		setOverviewContent("UI_PANEL_ALL_SECURITIES",
				new MasterContentPane(listPanel, content, "stockSecurities.main", MASTER_DIVIDER));
	}

	@Override
	public void refreshOnShow() {
		SecurityDetails selected = listPanel.getSelectedItem();
		reloadAndSelect(selected != null ? selected.securityId() : null);
	}

	private void reloadAndSelect(Integer securityId) {
		listPanel.reload(securityId);
		if (securityId == null || listPanel.getSelectedItem() == null) {
			showSecurity(null);
		}
	}

	private void showSecurity(SecurityDetails security) {
		detailPanel.updatePanel(security);
		pricePanel.updatePanel(security);
	}
}
