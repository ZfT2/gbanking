package de.zft2.gbanking.gui.panel.stock;

import java.util.List;

import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.dialog.stock.SecurityPriceDialog;
import de.zft2.gbanking.gui.panel.BasePanel;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PriceSummary;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.Priority;

public class StockSecurityPricePanel extends BasePanel {

	private final StockPortfolioService service;
	private final GBankingTableView<PriceSummary> table = new GBankingTableView<>();
	private final Button managePricesButton = new Button(getText("UI_STOCK_SECURITY_MANAGE_PRICES"));
	private SecurityDetails security;

	public StockSecurityPricePanel(StockPortfolioService service) {
		this.service = service;
		setSpacing(8);
		setPadding(new Insets(8, 0, 0, 0));
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		configureTable();
		managePricesButton.setOnAction(event -> openPriceDialog());
		getChildren().setAll(new Label(getText("UI_PANEL_STOCK_SECURITY_PRICES")), table,
				FormStyleUtils.createButtonBar(managePricesButton));
		setVgrow(table, Priority.ALWAYS);
		updatePanel(null);
	}

	private void configureTable() {
		table.getColumns().setAll(createColumns());
		table.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
	}

	private List<TableColumn<PriceSummary, ?>> createColumns() {
		return List.of(
				TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_FIELD_DATE"), PriceSummary::date, 105),
				TableColumnFactory.createAmountColumn(getText("UI_STOCK_FIELD_PRICE"), PriceSummary::price),
				TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_FIELD_CURRENCY"),
						summary -> summary.currency().name(), 85),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_SECURITY_PRICE_TYPE"),
						summary -> summary.priceType().toString(), 100, 140),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_SOURCE"),
						PriceSummary::sourceName, 120, 180));
	}

	public void updatePanel(SecurityDetails selectedSecurity) {
		security = selectedSecurity;
		managePricesButton.setDisable(security == null);
		reloadPrices();
	}

	private void reloadPrices() {
		table.setItems(FXCollections.observableArrayList(
				security != null ? service.getPrices(security.securityId()) : List.of()));
	}

	private void openPriceDialog() {
		if (security == null) {
			return;
		}
		new SecurityPriceDialog(getOwnerWindow(), security.securityId(), security.name(), security.quoteCurrency(),
				() -> service.getPrices(security.securityId()),
				(priceId, date, price, currency, confirmed) -> service.savePrice(
						security.securityId(), priceId, date, price, currency, confirmed),
				(priceId, confirmed) -> service.deletePrice(security.securityId(), priceId, confirmed),
				this::reloadPrices).show();
	}
}
