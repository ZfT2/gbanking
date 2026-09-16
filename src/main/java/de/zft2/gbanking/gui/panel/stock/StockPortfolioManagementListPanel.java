package de.zft2.gbanking.gui.panel.stock;

import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioDetails;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class StockPortfolioManagementListPanel extends AbstractFilterableTablePanel<PortfolioDetails> {

	private final StockPortfolioAdministrationService service;
	private final Consumer<PortfolioDetails> selectionHandler;

	public StockPortfolioManagementListPanel(StockPortfolioAdministrationService service,
			Consumer<PortfolioDetails> selectionHandler) {
		super(FXCollections.observableArrayList());
		this.service = service;
		this.selectionHandler = selectionHandler;
		setColumns(createColumns());
		configureTableLayout("stockPortfolios.ALL_STOCK_PORTFOLIOS");
		onSelection(selectionHandler);
		setPanelTitle(getText("UI_PANEL_STOCK_PORTFOLIO_LIST") + " (0)");
	}

	private List<TableColumn<PortfolioDetails, ?>> createColumns() {
		return List.of(
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_PORTFOLIO"), PortfolioDetails::name, 180, 230),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_ACCOUNT_NUMBER"), PortfolioDetails::accountNumber, 130, 170),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_BANK"), PortfolioDetails::bankName, 130, 170),
				TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_FIELD_CURRENCY"),
						portfolio -> portfolio.currency() != null ? portfolio.currency().name() : "", 80),
				TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_ACCOUNT_STATE"),
						portfolio -> portfolio.accountState() != null ? portfolio.accountState().toString() : "", 100),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_SETTLEMENT_ACCOUNT"),
						PortfolioDetails::settlementAccountName, 180, 240),
				TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_FIELD_OPENED_AT"), PortfolioDetails::openedAt, 110),
				TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"), PortfolioDetails::updatedAt, 120));
	}

	@Override
	protected boolean matchesFilter(PortfolioDetails portfolio, String filter) {
		return matchesAny(filter, portfolio.name(), portfolio.accountNumber(), portfolio.iban(), portfolio.bankName(),
				portfolio.bic(), portfolio.blz(), portfolio.ownerName(), portfolio.ownerName2(),
				portfolio.currency() != null ? portfolio.currency().name() : null,
				portfolio.accountState() != null ? portfolio.accountState().toString() : null,
				portfolio.settlementAccountName());
	}

	public void reload(Integer portfolioIdToSelect) {
		replaceItemsFrom(service::getPortfolios);
		setPanelTitle(getText("UI_PANEL_STOCK_PORTFOLIO_LIST") + " (" + masterData.size() + ")");
		PortfolioDetails portfolio = portfolioIdToSelect != null ? masterData.stream()
				.filter(candidate -> candidate.portfolioId() == portfolioIdToSelect).findFirst().orElse(null) : null;
		if (portfolio != null) {
			tableView.getSelectionModel().select(portfolio);
			selectionHandler.accept(portfolio);
		} else {
			tableView.getSelectionModel().clearSelection();
		}
	}
}
