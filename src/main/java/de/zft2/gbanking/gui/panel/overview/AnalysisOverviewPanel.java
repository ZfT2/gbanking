package de.zft2.gbanking.gui.panel.overview;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.analysis.TurnoverAnalysisService;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.AccountSelectionMode;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.AnalysisConfiguration;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.AnalysisResult;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.BalancePoint;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.CategorySlice;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.DateRange;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.FlowDirection;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.PeriodType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class AnalysisOverviewPanel extends OverviewBasePanel {

	private static final Logger log = LogManager.getLogger(AnalysisOverviewPanel.class);
	private static final String AMOUNT_NEGATIVE = "amount-negative";
	private static final String AMOUNT_NEUTRAL = "amount-neutral";
	private static final String AMOUNT_POSITIVE = "amount-positive";
	private static final DateTimeFormatter AXIS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy");
	private static final DateTimeFormatter AXIS_MONTH_FORMAT = DateTimeFormatter.ofPattern("MM.yy");
	private static final DateTimeFormatter AXIS_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
	private static final double[][] DATE_TICK_UNITS = { { 14, 1 }, { 45, 7 }, { 120, 14 }, { 240, 30 }, { 500, 60 }, { 1000, 90 },
			{ 2200, 180 } };

	private enum MetricValueStyle {
		NONE,
		POSITIVE,
		NEGATIVE,
		NEUTRAL
	}

	private record MetricRow(String metric, String value, MetricValueStyle valueStyle) {
	}

	private final TurnoverAnalysisService analysisService = new TurnoverAnalysisService();
	private final DecimalFormat amountFormat = FxTableUtils.createGermanDecimalFormat();
	private final DecimalFormat axisAmountFormat = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(java.util.Locale.GERMAN));
	private final ObservableList<MetricRow> metricRows = FXCollections.observableArrayList();
	private final Map<AccountSelectionMode, RadioMenuItem> fixedAccountItems = new EnumMap<>(AccountSelectionMode.class);
	private final Map<Integer, CheckBox> accountCheckBoxes = new HashMap<>();
	private final PageContext pageContext;
	private final String panelTitleKey;
	private final String settingAttribute;
	private final String settingCommentKey;

	private AnalysisConfiguration configuration = AnalysisConfiguration.defaultConfiguration();
	private List<BankAccount> eligibleAccounts = List.of();
	private List<Booking> allBookings = List.of();
	private boolean updatingControls;

	private MenuButton accountMenuButton;
	private ComboBox<PeriodType> periodComboBox;
	private Label sinceLabel;
	private Label fromLabel;
	private Label toLabel;
	private DatePicker sinceDatePicker;
	private DatePicker fromDatePicker;
	private DatePicker toDatePicker;
	private NumberAxis balanceXAxis;
	private NumberAxis balanceYAxis;
	private LineChart<Number, Number> balanceChart;
	private PieChart categoryChart;

	public AnalysisOverviewPanel() {
		this(PageContext.ANALYSIS, "UI_PANEL_ANALYSIS", "analysis.configuration", "UI_ANALYSIS_SETTING_COMMENT");
	}

	protected AnalysisOverviewPanel(PageContext pageContext, String panelTitleKey, String settingAttribute, String settingCommentKey) {
		this.pageContext = pageContext;
		this.panelTitleKey = panelTitleKey;
		this.settingAttribute = settingAttribute;
		this.settingCommentKey = settingCommentKey;
	}

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(pageContext);
		log.info("Creating {}", getClass().getSimpleName());

		accountMenuButton = new MenuButton();
		periodComboBox = new ComboBox<>(FXCollections.observableArrayList(PeriodType.values()));
		sinceLabel = new Label(getText("UI_ANALYSIS_LABEL_SINCE"));
		fromLabel = new Label(getText("UI_LABEL_DATE_FROM"));
		toLabel = new Label(getText("UI_LABEL_DATE_TO"));
		sinceDatePicker = new DatePicker();
		fromDatePicker = new DatePicker();
		toDatePicker = new DatePicker();
		configurePeriodComboBox();
		configureDatePickers();

		BorderPane content = new BorderPane();
		content.setTop(createHeader());
		content.setCenter(createMainContent());
		BorderPane.setMargin(content.getCenter(), new Insets(8, 0, 0, 0));

		setOverviewContent(panelTitleKey, content, show);
		refreshOnShow();
	}

	@Override
	public void refreshOnShow() {
		if (accountMenuButton == null) {
			return;
		}
		configuration = loadConfiguration();
		reloadData();
		rebuildAccountMenu();
		updatePeriodControls();
		updateAnalysis(false);
	}

	private Node createHeader() {
		Label accountLabel = new Label(getText("UI_LABEL_ANALYSIS_ACCOUNTS"));
		Label periodLabel = new Label(getText("UI_LABEL_ANALYSIS_PERIOD"));

		accountMenuButton.setMinWidth(260);
		accountMenuButton.setPrefWidth(320);
		periodComboBox.setMinWidth(180);
		periodComboBox.setPrefWidth(210);

		HBox header = new HBox(10, accountLabel, accountMenuButton, periodLabel, periodComboBox, sinceLabel, sinceDatePicker, fromLabel, fromDatePicker,
				toLabel, toDatePicker);
		header.getChildren().addAll(createAdditionalHeaderNodes());
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(4, 0, 6, 0));
		return header;
	}

	protected List<Node> createAdditionalHeaderNodes() {
		return List.of();
	}

	protected Node createMainContent() {
		GBankingTableView<MetricRow> metricTable = createMetricTable();
		balanceChart = createBalanceChart();
		categoryChart = createCategoryChart();

		VBox chartBox = new VBox(10, balanceChart, categoryChart);
		chartBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox.setVgrow(balanceChart, Priority.ALWAYS);
		VBox.setVgrow(categoryChart, Priority.ALWAYS);

		metricTable.setMinWidth(330);
		metricTable.setPrefWidth(390);
		metricTable.setMaxWidth(520);

		javafx.scene.control.SplitPane splitPane = new javafx.scene.control.SplitPane(metricTable, chartBox);
		splitPane.setOrientation(Orientation.HORIZONTAL);
		splitPane.setDividerPositions(0.30);
		splitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		GuiLayoutState.configureSplitPane(splitPane, "analysis.main");
		return splitPane;
	}

	private GBankingTableView<MetricRow> createMetricTable() {
		GBankingTableView<MetricRow> table = new GBankingTableView<>();
		table.setItems(metricRows);
		table.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setFixedCellSize(30);

		TableColumn<MetricRow, String> metricColumn = new TableColumn<>(getText("UI_TABLE_ANALYSIS_METRIC"));
		metricColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().metric()));
		metricColumn.setMinWidth(170);
		metricColumn.setPrefWidth(210);

		TableColumn<MetricRow, String> valueColumn = new TableColumn<>(getText("UI_TABLE_ANALYSIS_VALUE"));
		valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().value()));
		valueColumn.setCellFactory(column -> createMetricValueCell());
		valueColumn.setMinWidth(120);
		valueColumn.setPrefWidth(170);

		table.getColumns().setAll(Arrays.asList(metricColumn, valueColumn));
		table.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		GuiLayoutState.configureTable(table, "analysis.metrics");
		return table;
	}

	private LineChart<Number, Number> createBalanceChart() {
		balanceXAxis = new NumberAxis();
		balanceYAxis = new NumberAxis();
		balanceXAxis.setLabel(getText("UI_TABLE_DATE"));
		balanceXAxis.setMinorTickVisible(false);
		balanceYAxis.setLabel(getText("UI_TABLE_BALANCE"));
		balanceYAxis.setForceZeroInRange(false);
		balanceYAxis.setMinorTickVisible(false);
		balanceYAxis.setTickLabelFormatter(createAmountAxisFormatter());
		LineChart<Number, Number> chart = new LineChart<>(balanceXAxis, balanceYAxis);
		chart.setTitle(getText("UI_ANALYSIS_BALANCE_CHART_TITLE"));
		chart.setLegendVisible(false);
		chart.setAnimated(false);
		chart.setCreateSymbols(false);
		chart.setMinHeight(260);
		chart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		return chart;
	}

	private PieChart createCategoryChart() {
		PieChart chart = new PieChart();
		chart.setTitle(getText("UI_ANALYSIS_CATEGORY_CHART_TITLE"));
		chart.setLegendVisible(true);
		chart.setLabelsVisible(true);
		chart.setMinHeight(260);
		chart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		return chart;
	}

	private void configurePeriodComboBox() {
		periodComboBox.setCellFactory(list -> createPeriodCell());
		periodComboBox.setButtonCell(createPeriodCell());
		periodComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (updatingControls || newValue == null) {
				return;
			}
			handlePeriodChanged(newValue);
		});
	}

	private ListCell<PeriodType> createPeriodCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(PeriodType item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : periodLabel(item));
			}
		};
	}

	private void configureDatePickers() {
		sinceDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> handleDateChanged());
		fromDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> handleDateChanged());
		toDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> handleDateChanged());
	}

	private void reloadData() {
		List<BankAccount> allAccounts = bean.getAllAccounts();
		allBookings = bean.getAllBookings();
		eligibleAccounts = analysisService.getEligibleAccounts(allAccounts, allBookings);
	}

	private void rebuildAccountMenu() {
		fixedAccountItems.clear();
		accountCheckBoxes.clear();

		ToggleGroup fixedGroup = new ToggleGroup();
		List<javafx.scene.control.MenuItem> items = new ArrayList<>();
		items.add(createFixedAccountItem(AccountSelectionMode.ALL, getText("UI_ANALYSIS_ACCOUNT_ALL"), fixedGroup));
		items.add(createFixedAccountItem(AccountSelectionMode.ACTIVE, getText("UI_ANALYSIS_ACCOUNT_ACTIVE"), fixedGroup));
		items.add(createFixedAccountItem(AccountSelectionMode.ONLINE, getText("UI_ANALYSIS_ACCOUNT_ONLINE"), fixedGroup));
		items.add(createClearAccountsItem());
		items.add(new SeparatorMenuItem());

		for (BankAccount account : eligibleAccounts) {
			items.add(createAccountCheckItem(account));
		}
		accountMenuButton.getItems().setAll(items);
		updateAccountControls();
	}

	private RadioMenuItem createFixedAccountItem(AccountSelectionMode mode, String label, ToggleGroup fixedGroup) {
		RadioMenuItem item = new RadioMenuItem(label);
		item.setToggleGroup(fixedGroup);
		item.setOnAction(event -> {
			if (updatingControls) {
				return;
			}
			selectAccounts(mode, List.of());
		});
		fixedAccountItems.put(mode, item);
		return item;
	}

	private MenuItem createClearAccountsItem() {
		MenuItem item = new MenuItem(getText("UI_ANALYSIS_ACCOUNT_CLEAR"));
		item.setOnAction(event -> {
			if (updatingControls) {
				return;
			}
			selectAccounts(AccountSelectionMode.CUSTOM, List.of());
		});
		return item;
	}

	private CustomMenuItem createAccountCheckItem(BankAccount account) {
		CheckBox checkBox = new CheckBox(formatAccountName(account));
		checkBox.setMaxWidth(Double.MAX_VALUE);
		checkBox.setOnAction(event -> {
			if (updatingControls) {
				return;
			}
			handleCustomAccountSelection();
		});
		accountCheckBoxes.put(account.getId(), checkBox);

		CustomMenuItem item = new CustomMenuItem(checkBox);
		item.setHideOnClick(false);
		return item;
	}

	private void updateAccountControls() {
		updatingControls = true;
		try {
			AccountSelectionMode mode = configuration.accountSelectionMode();
			for (Map.Entry<AccountSelectionMode, RadioMenuItem> entry : fixedAccountItems.entrySet()) {
				entry.getValue().setSelected(mode == entry.getKey());
			}

			Set<Integer> selectedIds = analysisService.resolveSelectedAccounts(eligibleAccounts, configuration).stream()
					.map(BankAccount::getId)
					.collect(Collectors.toSet());
			for (Map.Entry<Integer, CheckBox> entry : accountCheckBoxes.entrySet()) {
				entry.getValue().setSelected(selectedIds.contains(entry.getKey()));
			}
			accountMenuButton.setText(accountMenuText(selectedIds));
		} finally {
			updatingControls = false;
		}
	}

	private void handleCustomAccountSelection() {
		List<Integer> selectedIds = accountCheckBoxes.entrySet().stream()
				.filter(entry -> entry.getValue().isSelected())
				.map(Map.Entry::getKey)
				.sorted()
				.toList();
		selectAccounts(AccountSelectionMode.CUSTOM, selectedIds);
	}

	private void selectAccounts(AccountSelectionMode mode, List<Integer> selectedIds) {
		configuration = new AnalysisConfiguration(mode, selectedIds, configuration.periodType(), configuration.dateFrom(), configuration.dateTo());
		updateAccountControls();
		updateAnalysis(true);
	}

	private String accountMenuText(Set<Integer> selectedIds) {
		if (configuration.accountSelectionMode() != AccountSelectionMode.CUSTOM) {
			return switch (configuration.accountSelectionMode()) {
			case ALL -> getText("UI_ANALYSIS_ACCOUNT_ALL") + " (" + selectedIds.size() + ")";
			case ACTIVE -> getText("UI_ANALYSIS_ACCOUNT_ACTIVE") + " (" + selectedIds.size() + ")";
			case ONLINE -> getText("UI_ANALYSIS_ACCOUNT_ONLINE") + " (" + selectedIds.size() + ")";
			case CUSTOM -> getText("UI_ANALYSIS_ACCOUNT_CUSTOM", selectedIds.size());
			};
		}
		if (selectedIds.isEmpty()) {
			return getText("UI_ANALYSIS_ACCOUNT_NONE");
		}

		List<String> selectedNames = eligibleAccounts.stream()
				.filter(account -> selectedIds.contains(account.getId()))
				.map(this::formatAccountName)
				.toList();
		if (selectedNames.size() <= 2) {
			return String.join(", ", selectedNames);
		}
		return getText("UI_ANALYSIS_ACCOUNT_CUSTOM", selectedNames.size());
	}

	private void handlePeriodChanged(PeriodType periodType) {
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		LocalDate dateFrom = configuration.dateFrom();
		LocalDate dateTo = configuration.dateTo();
		if (periodType == PeriodType.SINCE && dateFrom == null) {
			dateFrom = today.withDayOfYear(1);
		} else if (periodType == PeriodType.CUSTOM_RANGE) {
			if (dateFrom == null) {
				dateFrom = today.withDayOfMonth(1);
			}
			if (dateTo == null) {
				dateTo = today;
			}
		}

		configuration = new AnalysisConfiguration(configuration.accountSelectionMode(), configuration.selectedAccountIds(), periodType, dateFrom, dateTo);
		updatePeriodControls();
		updateAnalysis(true);
	}

	private void handleDateChanged() {
		if (updatingControls) {
			return;
		}
		PeriodType periodType = configuration.periodType();
		if (periodType == PeriodType.SINCE) {
			configuration = new AnalysisConfiguration(configuration.accountSelectionMode(), configuration.selectedAccountIds(), periodType,
					sinceDatePicker.getValue(), configuration.dateTo());
			updateAnalysis(true);
		} else if (periodType == PeriodType.CUSTOM_RANGE) {
			configuration = new AnalysisConfiguration(configuration.accountSelectionMode(), configuration.selectedAccountIds(), periodType,
					fromDatePicker.getValue(), toDatePicker.getValue());
			updateAnalysis(true);
		}
	}

	private void updatePeriodControls() {
		updatingControls = true;
		try {
			PeriodType periodType = configuration.periodType();
			LocalDate today = LocalDate.now(ZoneId.systemDefault());
			periodComboBox.setValue(periodType);
			sinceDatePicker.setValue(configuration.dateFrom() != null ? configuration.dateFrom() : today.withDayOfYear(1));
			fromDatePicker.setValue(configuration.dateFrom() != null ? configuration.dateFrom() : today.withDayOfMonth(1));
			toDatePicker.setValue(configuration.dateTo() != null ? configuration.dateTo() : today);

			boolean sinceVisible = periodType == PeriodType.SINCE;
			boolean rangeVisible = periodType == PeriodType.CUSTOM_RANGE;
			setVisibleManaged(sinceLabel, sinceVisible);
			setVisibleManaged(sinceDatePicker, sinceVisible);
			setVisibleManaged(fromLabel, rangeVisible);
			setVisibleManaged(fromDatePicker, rangeVisible);
			setVisibleManaged(toLabel, rangeVisible);
			setVisibleManaged(toDatePicker, rangeVisible);
		} finally {
			updatingControls = false;
		}
	}

	private void setVisibleManaged(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}

	protected final void updateAnalysis(boolean saveConfiguration) {
		AnalysisResult result = analysisService.analyze(eligibleAccounts, allBookings, configuration, LocalDate.now(ZoneId.systemDefault()));
		updateView(result);
		updateAccountControls();
		if (saveConfiguration) {
			saveConfiguration();
		}
	}

	protected void updateView(AnalysisResult result) {
		updateMetricRows(result);
		updateBalanceChart(result.balancePoints());
		updateCategoryChart(result.categorySlices());
	}

	private void updateMetricRows(AnalysisResult result) {
		var summary = result.summary();
		metricRows.setAll(
				new MetricRow(getText("UI_ANALYSIS_METRIC_PERIOD"), formatDateRange(result.dateRange()), MetricValueStyle.NONE),
				new MetricRow(getText("UI_ANALYSIS_METRIC_ACCOUNTS"), Integer.toString(summary.accountCount()), MetricValueStyle.NONE),
				new MetricRow(getText("UI_ANALYSIS_METRIC_BOOKINGS"), Integer.toString(summary.bookingCount()), MetricValueStyle.NONE),
				new MetricRow(getText("UI_ANALYSIS_METRIC_INFLOW_INCOME"), formatAmount(summary.income()), MetricValueStyle.POSITIVE),
				new MetricRow(getText("UI_ANALYSIS_METRIC_OUTFLOW_EXPENSES"), formatAmount(summary.expenses()), MetricValueStyle.NEGATIVE),
				new MetricRow(getText("UI_ANALYSIS_METRIC_NET"), formatAmount(summary.netExternalFlow()), valueStyleForSignedAmount(summary.netExternalFlow())),
				new MetricRow(getText("UI_ANALYSIS_METRIC_TURNOVER"), formatAmount(summary.turnover()), MetricValueStyle.NEUTRAL),
				new MetricRow(getText("UI_ANALYSIS_METRIC_BALANCE_CHANGE"), formatAmount(summary.selectedBalanceChange()),
						valueStyleForSignedAmount(summary.selectedBalanceChange())),
				new MetricRow(getText("UI_ANALYSIS_METRIC_NEUTRAL_TRANSFERS"), formatAmount(summary.neutralTransferAmount()), MetricValueStyle.NEUTRAL),
				new MetricRow(getText("UI_ANALYSIS_METRIC_NEUTRAL_TRANSFER_COUNT"), Integer.toString(summary.neutralTransferCount()), MetricValueStyle.NONE));
	}

	private TableCell<MetricRow, String> createMetricValueCell() {
		return new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				getStyleClass().removeAll(AMOUNT_POSITIVE, AMOUNT_NEGATIVE, AMOUNT_NEUTRAL);
				setAlignment(Pos.CENTER_RIGHT);

				if (empty || item == null) {
					setText(null);
					return;
				}

				setText(item);
				MetricRow row = getTableRow() != null ? getTableRow().getItem() : null;
				if (row != null) {
					applyMetricValueStyle(this, row.valueStyle());
				}
			}
		};
	}

	private void applyMetricValueStyle(TableCell<MetricRow, String> cell, MetricValueStyle style) {
		if (style == null || style == MetricValueStyle.NONE) {
			return;
		}
		switch (style) {
		case POSITIVE -> cell.getStyleClass().add(AMOUNT_POSITIVE);
		case NEGATIVE -> cell.getStyleClass().add(AMOUNT_NEGATIVE);
		case NEUTRAL -> cell.getStyleClass().add(AMOUNT_NEUTRAL);
		default -> throw new IllegalArgumentException("Unexpected style value: " + style);
		}
	}

	private MetricValueStyle valueStyleForSignedAmount(BigDecimal amount) {
		if (amount == null || amount.signum() == 0) {
			return MetricValueStyle.NEUTRAL;
		}
		return amount.signum() > 0 ? MetricValueStyle.POSITIVE : MetricValueStyle.NEGATIVE;
	}

	private void updateBalanceChart(List<BalancePoint> balancePoints) {
		configureBalanceAxes(balancePoints);
		XYChart.Series<Number, Number> series = new XYChart.Series<>();
		series.setName(getText("UI_ANALYSIS_BALANCE_SERIES"));
		for (BalancePoint point : balancePoints) {
			if (point.date() != null) {
				series.getData().add(new XYChart.Data<>(point.date().toEpochDay(), point.balance()));
			}
		}
		balanceChart.getData().setAll(Arrays.asList(series));
	}

	private void configureBalanceAxes(List<BalancePoint> balancePoints) {
		List<BalancePoint> points = balancePoints != null ? balancePoints.stream()
				.filter(point -> point != null && point.date() != null && point.balance() != null)
				.toList() : List.of();
		if (points.isEmpty()) {
			configureEmptyBalanceAxes();
			return;
		}

		double minX = points.stream().mapToLong(point -> point.date().toEpochDay()).min().orElse(LocalDate.now(ZoneId.systemDefault()).toEpochDay());
		double maxX = points.stream().mapToLong(point -> point.date().toEpochDay()).max().orElse(LocalDate.now(ZoneId.systemDefault()).toEpochDay());
		if (Double.compare(minX, maxX) == 0) {
			minX -= 1;
			maxX += 1;
		}
		double daySpan = Math.max(1, maxX - minX);
		double dateTickUnit = dateTickUnit(daySpan);
		balanceXAxis.setAutoRanging(false);
		balanceXAxis.setLowerBound(minX);
		balanceXAxis.setUpperBound(maxX);
		balanceXAxis.setTickUnit(dateTickUnit);
		balanceXAxis.setTickLabelFormatter(createDateAxisFormatter(daySpan));

		double minY = points.stream().map(BalancePoint::balance).mapToDouble(BigDecimal::doubleValue).min().orElse(0);
		double maxY = points.stream().map(BalancePoint::balance).mapToDouble(BigDecimal::doubleValue).max().orElse(0);
		configureBalanceYAxis(minY, maxY);
	}

	private void configureEmptyBalanceAxes() {
		long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
		balanceXAxis.setAutoRanging(false);
		balanceXAxis.setLowerBound(today - 1d);
		balanceXAxis.setUpperBound(today + 1d);
		balanceXAxis.setTickUnit(1);
		balanceXAxis.setTickLabelFormatter(createDateAxisFormatter(2));

		balanceYAxis.setAutoRanging(false);
		balanceYAxis.setLowerBound(-100);
		balanceYAxis.setUpperBound(100);
		balanceYAxis.setTickUnit(50);
	}

	private void configureBalanceYAxis(double minY, double maxY) {
		double range = maxY - minY;
		double padding = range > 0 ? Math.max(range * 0.08, 1.0) : Math.max(Math.abs(maxY) * 0.05, 100.0);
		double paddedMin = minY - padding;
		double paddedMax = maxY + padding;
		double paddedRange = paddedMax - paddedMin;
		double tickUnit = niceNumber(paddedRange / 8.0);
		double lowerBound = Math.floor(paddedMin / tickUnit) * tickUnit;
		double upperBound = Math.ceil(paddedMax / tickUnit) * tickUnit;
		if (Double.compare(lowerBound, upperBound) == 0) {
			lowerBound -= tickUnit;
			upperBound += tickUnit;
		}

		balanceYAxis.setAutoRanging(false);
		balanceYAxis.setLowerBound(lowerBound);
		balanceYAxis.setUpperBound(upperBound);
		balanceYAxis.setTickUnit(tickUnit);
	}

	private double dateTickUnit(double daySpan) {
		for (double[] tickUnit : DATE_TICK_UNITS) {
			if (daySpan <= tickUnit[0]) {
				return tickUnit[1];
			}
		}
		return 365;
	}

	private double niceNumber(double value) {
		if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
			return 1.0;
		}
		double exponent = Math.floor(Math.log10(value));
		double fraction = value / Math.pow(10, exponent);
		double niceFraction;
		if (fraction <= 1) {
			niceFraction = 1;
		} else if (fraction <= 2) {
			niceFraction = 2;
		} else if (fraction <= 5) {
			niceFraction = 5;
		} else {
			niceFraction = 10;
		}
		return niceFraction * Math.pow(10, exponent);
	}

	private StringConverter<Number> createDateAxisFormatter(double daySpan) {
		DateTimeFormatter formatter = getTimespanFormat(daySpan);
		return new StringConverter<>() {
			@Override
			public String toString(Number value) {
				if (value == null) {
					return "";
				}
				return formatter.format(LocalDate.ofEpochDay(Math.round(value.doubleValue())));
			}

			@Override
			public Number fromString(String value) {
				return 0;
			}
		};
	}

	private DateTimeFormatter getTimespanFormat(double daySpan) {
		DateTimeFormatter formatter = null;
		if (daySpan > 2200)
			formatter = AXIS_YEAR_FORMAT;
		else if (daySpan > 365)
			formatter = AXIS_MONTH_FORMAT;
		else
			formatter = AXIS_DATE_FORMAT;

		return formatter;
	}

	protected StringConverter<Number> createAmountAxisFormatter() {
		return new StringConverter<>() {
			@Override
			public String toString(Number value) {
				return value == null ? "" : axisAmountFormat.format(value.doubleValue());
			}

			@Override
			public Number fromString(String value) {
				return 0;
			}
		};
	}

	private void updateCategoryChart(List<CategorySlice> categorySlices) {
		List<PieChart.Data> data = categorySlices.stream()
				.map(slice -> new PieChart.Data(categorySliceLabel(slice), slice.amount().doubleValue()))
				.toList();
		categoryChart.setData(FXCollections.observableArrayList(data));
	}

	private AnalysisConfiguration loadConfiguration() {
		loadAdditionalConfiguration(Map.of());
		Setting setting = findConfigurationSetting();
		if (setting == null || setting.getValue() == null || setting.getValue().isBlank()) {
			return AnalysisConfiguration.defaultConfiguration();
		}

		try {
			Map<String, String> values = parseConfiguration(setting.getValue());
			loadAdditionalConfiguration(values);
			AccountSelectionMode mode = parseEnum(AccountSelectionMode.class, values.get("accountMode"), AccountSelectionMode.ALL);
			PeriodType periodType = parseEnum(PeriodType.class, values.get("period"), PeriodType.CURRENT_MONTH);
			List<Integer> accountIds = parseAccountIds(values.get("accountIds"));
			LocalDate from = parseDate(values.get("from"));
			LocalDate to = parseDate(values.get("to"));
			return new AnalysisConfiguration(mode, accountIds, periodType, from, to);
		} catch (Exception e) {
			log.warn("Could not parse analysis configuration setting.", e);
			loadAdditionalConfiguration(Map.of());
			return AnalysisConfiguration.defaultConfiguration();
		}
	}

	private void saveConfiguration() {
		Setting setting = findConfigurationSetting();
		if (setting == null) {
			setting = new Setting();
			setting.setAttribute(settingAttribute);
			setting.setDataType(DataType.STRING);
			setting.setEditable(false);
			setting.setVisible(false);
			setting.setComment(getText(settingCommentKey));
		}
		setting.setValue(serializeConfiguration(configuration));
		dbController.insertOrUpdate(setting);
	}

	private Setting findConfigurationSetting() {
		return dbController.getAll(Setting.class).stream()
				.filter(setting -> settingAttribute.equals(setting.getAttribute()))
				.findFirst()
				.orElse(null);
	}

	private String serializeConfiguration(AnalysisConfiguration config) {
		return "accountMode=" + config.accountSelectionMode().name()
				+ ";accountIds=" + config.selectedAccountIds().stream().map(String::valueOf).collect(Collectors.joining(","))
				+ ";period=" + config.periodType().name()
				+ ";from=" + formatSettingDate(config.dateFrom())
				+ ";to=" + formatSettingDate(config.dateTo())
				+ serializeAdditionalConfiguration();
	}

	protected void loadAdditionalConfiguration(Map<String, String> values) {
		// Optional configuration hook for specialized analysis views.
	}

	protected String serializeAdditionalConfiguration() {
		return "";
	}

	private Map<String, String> parseConfiguration(String value) {
		Map<String, String> values = new HashMap<>();
		for (String part : value.split(";")) {
			int separator = part.indexOf('=');
			if (separator > 0) {
				values.put(part.substring(0, separator), part.substring(separator + 1));
			}
		}
		return values;
	}

	private List<Integer> parseAccountIds(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		List<Integer> accountIds = new ArrayList<>();
		for (String part : value.split(",")) {
			if (!part.isBlank()) {
				accountIds.add(Integer.parseInt(part.trim()));
			}
		}
		return accountIds;
	}

	private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Enum.valueOf(enumType, value);
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	private LocalDate parseDate(String value) {
		return value == null || value.isBlank() ? null : LocalDate.parse(value);
	}

	private String formatSettingDate(LocalDate date) {
		return date != null ? date.toString() : "";
	}

	private String formatDateRange(DateRange dateRange) {
		if (dateRange == null || dateRange.from() == null || dateRange.to() == null) {
			return getText("UI_ANALYSIS_PERIOD_ALL_TIME");
		}
		return DateFormatUtils.formatLong(dateRange.from()) + " - " + DateFormatUtils.formatLong(dateRange.to());
	}

	protected String formatAmount(BigDecimal value) {
		return amountFormat.format(value != null ? value : BigDecimal.ZERO);
	}

	private String categorySliceLabel(CategorySlice slice) {
		String direction = slice.direction() == FlowDirection.INCOME ? getText("UI_ANALYSIS_DIRECTION_INCOME") : getText("UI_ANALYSIS_DIRECTION_EXPENSE");
		String category = slice.categoryName() != null && !slice.categoryName().isBlank()
				? slice.categoryName()
				: getText("UI_ANALYSIS_CATEGORY_NONE");
		return direction + ": " + category;
	}

	private String periodLabel(PeriodType periodType) {
		return switch (periodType) {
		case CURRENT_MONTH -> getText("UI_ANALYSIS_PERIOD_CURRENT_MONTH");
		case LAST_30_DAYS -> getText("UI_ANALYSIS_PERIOD_LAST_30_DAYS");
		case LAST_3_MONTHS -> getText("UI_ANALYSIS_PERIOD_LAST_3_MONTHS");
		case CURRENT_YEAR -> getText("UI_ANALYSIS_PERIOD_CURRENT_YEAR");
		case LAST_365_DAYS -> getText("UI_ANALYSIS_PERIOD_LAST_365_DAYS");
		case ALL_TIME -> getText("UI_ANALYSIS_PERIOD_ALL_TIME");
		case SINCE -> getText("UI_ANALYSIS_PERIOD_SINCE");
		case CUSTOM_RANGE -> getText("UI_ANALYSIS_PERIOD_CUSTOM_RANGE");
		};
	}

	private String formatAccountName(BankAccount account) {
		if (account.getAccountName() != null && !account.getAccountName().isBlank()) {
			return account.getAccountName();
		}
		if (account.getIban() != null && !account.getIban().isBlank()) {
			return account.getIban();
		}
		return account.getNumber() != null && !account.getNumber().isBlank() ? account.getNumber() : "?";
	}
}
