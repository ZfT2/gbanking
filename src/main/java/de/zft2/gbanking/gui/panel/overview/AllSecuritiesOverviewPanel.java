package de.zft2.gbanking.gui.panel.overview;

import java.util.List;

import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.layout.MasterContentPane;
import de.zft2.gbanking.gui.panel.stock.StockSecurityDetailPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityListPanel;
import de.zft2.gbanking.gui.panel.stock.StockSecurityPricePanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.SecurityHolding;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
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
		detailPanel.setHoldingsHandler(security -> {
			try {
				showHoldings(portfolioService, security);
			} catch (RuntimeException exception) {
				DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, exception.getMessage());
			}
		});
		pricePanel = new StockSecurityPricePanel(portfolioService);
		listPanel = new StockSecurityListPanel(securityService, portfolioService, this::showSecurity);

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

	private void showHoldings(StockPortfolioService service, SecurityDetails security) {
		TableView<SecurityHolding> table = new TableView<>(FXCollections.observableArrayList(
				service.getSecurityHoldings(security.securityId())));
		var quantityFormat = StockPortfolioOverviewPanel.createQuantityFormat();
		table.getColumns().setAll(List.of(
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_PORTFOLIO"), row -> row.portfolio().displayName(), 180),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_BANK"), row -> row.portfolio().bankName(), 140),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_QUANTITY"), row -> quantityFormat.format(row.quantity()), 120),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_SECURITY_QUANTITY_TYPE"), row -> row.quantityType().toString(), 120)));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setPlaceholder(new Label(getText("UI_STOCK_SECURITY_NO_HOLDINGS")));
		table.setPrefSize(620, Math.min(350, 70 + table.getItems().size() * 28));
		Alert dialog = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
		dialog.initOwner(getOwnerWindow());
		dialog.setTitle(getText("UI_STOCK_SECURITY_HOLDINGS"));
		dialog.setHeaderText(security.name());
		dialog.getDialogPane().setContent(table);
		table.setRowFactory(ignored -> {
			TableRow<SecurityHolding> row = new TableRow<>();
			row.setOnMouseClicked(event -> {
				if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
					dialog.close();
					GuiContext.showStockPortfolio(row.getItem().portfolio().portfolioId());
				}
			});
			return row;
		});
		dialog.showAndWait();
	}
}
