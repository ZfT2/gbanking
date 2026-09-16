package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;
import de.zft2.gbanking.gui.panel.stock.StockPortfolioManagementDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockPortfolioManagementListPanel;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioDetails;

public class AllStockPortfoliosOverviewPanel extends OverviewBasePanel {

	private final StockPortfolioManagementDetailPanel detailPanel;
	private final StockPortfolioManagementListPanel listPanel;

	public AllStockPortfoliosOverviewPanel() {
		this(ServiceRegistry.getService(StockPortfolioAdministrationService.class));
	}

	AllStockPortfoliosOverviewPanel(StockPortfolioAdministrationService service) {
		setPageContext(PageContext.ALL_STOCK_PORTFOLIOS);
		detailPanel = new StockPortfolioManagementDetailPanel(service, this::reloadAndSelect);
		listPanel = new StockPortfolioManagementListPanel(service, detailPanel::updatePanel);
		setOverviewContent("UI_PANEL_ALL_STOCK_PORTFOLIOS", new DetailListPane(detailPanel, listPanel));
	}

	@Override
	public void refreshOnShow() {
		PortfolioDetails selected = listPanel.getSelectedItem();
		reloadAndSelect(selected != null ? selected.portfolioId() : null);
	}

	private void reloadAndSelect(Integer portfolioId) {
		listPanel.reload(portfolioId);
		if (portfolioId == null || listPanel.getSelectedItem() == null) {
			detailPanel.updatePanel(null);
		}
	}
}
