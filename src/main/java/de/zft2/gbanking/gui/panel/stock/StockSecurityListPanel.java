package de.zft2.gbanking.gui.panel.stock;

import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class StockSecurityListPanel extends AbstractFilterableTablePanel<SecurityDetails> {

	private static final double LIST_MIN_WIDTH = 320;
	private static final double LIST_PREF_WIDTH = 400;
	private static final double LIST_MAX_WIDTH = 520;

	private final StockSecurityAdministrationService service;
	private final Consumer<SecurityDetails> selectionHandler;

	public StockSecurityListPanel(StockSecurityAdministrationService service,
			Consumer<SecurityDetails> selectionHandler) {
		super(FXCollections.observableArrayList());
		this.service = service;
		this.selectionHandler = selectionHandler;
		setMinWidth(LIST_MIN_WIDTH);
		setPrefWidth(LIST_PREF_WIDTH);
		setMaxWidth(LIST_MAX_WIDTH);
		setColumns(createColumns());
		configureTableLayout("stockSecurities.ALL_SECURITIES");
		onSelection(selectionHandler);
		updateTitle();
	}

	private List<TableColumn<SecurityDetails, ?>> createColumns() {
		return List.of(
				TableColumnFactory.createTextColumn(getText("UI_STOCK_SECURITY_NAME"), SecurityDetails::name, 150, 210),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_ISIN"), SecurityDetails::isin, 105, 125),
				TableColumnFactory.createTextColumn(getText("UI_STOCK_COLUMN_WKN"), SecurityDetails::wkn, 75, 90));
	}

	@Override
	protected boolean matchesFilter(SecurityDetails security, String filter) {
		return matchesAny(filter, security.name(), security.issuer(), security.isin(), security.wkn(), security.ticker(),
				security.marketIdentifierCode(), security.domicileCountry(),
				security.securityType() != null ? security.securityType().toString() : null,
				security.securityState() != null ? security.securityState().toString() : null);
	}

	public void reload(Integer securityIdToSelect) {
		replaceItems(service.getSecurities(GuiContext.isOnlySecuritiesWithHoldingsVisible()));
		updateTitle();
		SecurityDetails security = securityIdToSelect != null ? masterData.stream()
				.filter(candidate -> candidate.securityId() == securityIdToSelect).findFirst().orElse(null) : null;
		if (security != null) {
			tableView.getSelectionModel().select(security);
			selectionHandler.accept(security);
		} else {
			tableView.getSelectionModel().clearSelection();
		}
	}

	private void updateTitle() {
		setPanelTitle(getText("UI_PANEL_STOCK_SECURITY_LIST") + " (" + masterData.size() + ")");
	}
}
