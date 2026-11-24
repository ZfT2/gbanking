package de.zft2.gbanking.gui.panel.stock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import de.zft2.gbanking.gui.GuiContext;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityDetails;
import de.zft2.gbanking.service.stock.StockSecurityAdministrationService.SecurityUsage;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;

public class StockSecurityListPanel extends AbstractFilterableTablePanel<SecurityDetails> {

	private static final double LIST_MIN_WIDTH = 400;
	private static final double LIST_PREF_WIDTH = 480;
	private static final double LIST_MAX_WIDTH = 600;

	private final StockSecurityAdministrationService service;
	private final StockPortfolioService portfolioService;
	private final Consumer<SecurityDetails> selectionHandler;
	private final ComboBox<SecurityFilter> filter = new ComboBox<>();
	private SecurityUsage usage = new SecurityUsage(Set.of(), Map.of());
	private boolean holdingsOnly = GuiContext.isOnlySecuritiesWithHoldingsVisible();
	private boolean reloading;

	private record SecurityFilter(String key, Integer portfolioId, String label) {
		@Override
		public String toString() { return label; }
	}

	public StockSecurityListPanel(StockSecurityAdministrationService service, StockPortfolioService portfolioService,
			Consumer<SecurityDetails> selectionHandler) {
		super(FXCollections.observableArrayList());
		this.service = service;
		this.portfolioService = portfolioService;
		this.selectionHandler = selectionHandler;
		setMinWidth(LIST_MIN_WIDTH);
		setPrefWidth(LIST_PREF_WIDTH);
		setMaxWidth(LIST_MAX_WIDTH);
		setColumns(createColumns());
		configureTableLayout("stockSecurities.ALL_SECURITIES");
		onSelection(selectionHandler);
		tableView.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
			if (selected == null) { selectionHandler.accept(null); }
		});
		filter.setId("security-filter");
		filter.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(SecurityFilter item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? StockSecurityListPanel.this.getText("UI_STOCK_SECURITY_FILTER") : item.label());
			}
		});
		filter.getStyleClass().addAll("account-grouping-choice", "security-filter-choice");
		filter.setMaxWidth(Region.USE_PREF_SIZE);
		filter.setAccessibleText(getText("UI_STOCK_SECURITY_FILTER"));
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(4, titleLabel, spacer, filter);
		header.setAlignment(Pos.CENTER_LEFT);
		setTop(header);
		filter.valueProperty().addListener((observable, previous, selected) -> {
			if (!reloading && selected != null) {
				filter.setTooltip(new Tooltip(selected.label()));
				applyFilter();
			}
		});
		filteredData.addListener((javafx.collections.ListChangeListener<SecurityDetails>) change -> {
			updateTitle();
		});
		updateTitle();
	}

	private List<TableColumn<SecurityDetails, ?>> createColumns() {
		return List.of(
				TableColumnFactory.createTextColumn(getText("UI_STOCK_SECURITY_NAME"), SecurityDetails::name, 150, 210),
				TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_ISIN"), SecurityDetails::isin, 115),
				TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_COLUMN_WKN"), SecurityDetails::wkn, 85));
	}

	@Override
	protected boolean matchesFilter(SecurityDetails security, String filter) {
		return matchesUsage(security.securityId()) && matchesAny(filter, security.name(), security.issuer(), security.isin(), security.wkn(), security.ticker(),
				security.marketIdentifierCode(), security.domicileCountry(),
				security.securityType() != null ? security.securityType().toString() : null,
				security.securityState() != null ? security.securityState().toString() : null);
	}

	public void reload(Integer securityIdToSelect) {
		reloading = true;
		try {
			SecurityFilter selected = filter.getValue();
			boolean currentHoldingsOnly = GuiContext.isOnlySecuritiesWithHoldingsVisible();
			if (selected == null || currentHoldingsOnly != holdingsOnly) {
				selected = choice(selected == null || currentHoldingsOnly ? "HOLDINGS" : "ALL");
			}
			holdingsOnly = currentHoldingsOnly;
			usage = service.getSecurityUsage();
			filter.getItems().setAll(List.of(choice("ALL"), choice("REFERENCED"), choice("HOLDINGS")));
			for (var portfolio : portfolioService.getPortfolios()) {
				filter.getItems().add(new SecurityFilter("PORTFOLIO", portfolio.portfolioId(),
						getText("UI_STOCK_SECURITY_FILTER_PORTFOLIO", portfolio.displayName())));
			}
			filter.getItems().add(choice("UNREFERENCED"));
			SecurityFilter previous = selected;
			filter.getSelectionModel().select(filter.getItems().stream().filter(item -> item.key().equals(previous.key())
					&& java.util.Objects.equals(item.portfolioId(), previous.portfolioId())).findFirst().orElse(filter.getItems().get(0)));
			filter.setTooltip(new Tooltip(filter.getValue().label()));
			replaceItems(service.getSecurities(false));
		} finally {
			reloading = false;
		}
		applyFilter();
		updateTitle();
		SecurityDetails security = securityIdToSelect != null ? filteredData.stream()
				.filter(candidate -> candidate.securityId() == securityIdToSelect).findFirst().orElse(null) : null;
		if (security != null) {
			tableView.getSelectionModel().select(security);
			selectionHandler.accept(security);
		} else {
			tableView.getSelectionModel().clearSelection();
		}
	}

	private void updateTitle() {
		setPanelTitle(getText("UI_PANEL_STOCK_SECURITY_LIST") + " (" + filteredData.size() + ")");
	}

	private SecurityFilter choice(String key) {
		return new SecurityFilter(key, null, getText("UI_STOCK_SECURITY_FILTER_" + key));
	}

	private boolean matchesUsage(int securityId) {
		SecurityFilter selected = filter.getValue();
		if (selected == null || selected.key().equals("ALL")) { return true; }
		return switch (selected.key()) {
			case "REFERENCED" -> usage.referenced().contains(securityId);
			case "UNREFERENCED" -> !usage.referenced().contains(securityId);
			case "PORTFOLIO" -> usage.holdingsByPortfolio().getOrDefault(selected.portfolioId(), Set.of()).contains(securityId);
			case "HOLDINGS" -> usage.holdingsByPortfolio().values().stream().anyMatch(ids -> ids.contains(securityId));
			default -> throw new IllegalStateException("Unknown security filter: " + selected.key());
		};
	}
}
