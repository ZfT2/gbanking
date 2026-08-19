package de.zft2.gbanking.gui.panel.overview;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.zft2.gbanking.analysis.TurnoverAnalyzer.AnalysisResult;
import de.zft2.gbanking.analysis.TurnoverAnalyzer.CategorySlice;
import de.zft2.gbanking.analysis.TurnoverAnalyzer.FlowDirection;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.gui.enu.PageContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;

public class CategoryAnalysisOverviewPanel extends AnalysisOverviewPanel {

	private static final double CHART_MIN_HEIGHT = 420;
	private static final double CATEGORY_ROW_HEIGHT = 36;
	private static final double CHART_VERTICAL_PADDING = 120;
	private static final String HIDE_EMPTY_CATEGORIES_SETTING = "hideEmptyCategories";

	private final Map<Node, Tooltip> installedTooltips = new HashMap<>();

	private StackedBarChart<Number, String> categoryChart;
	private CategoryAxis categoryAxis;
	private CheckBox hideEmptyCategoriesCheckBox;
	private Map<String, String> categoryTooltipTexts = Map.of();

	public CategoryAnalysisOverviewPanel() {
		super(PageContext.CATEGORY_ANALYSIS, "UI_PANEL_CATEGORY_ANALYSIS", "category.analysis.configuration",
				"UI_CATEGORY_ANALYSIS_SETTING_COMMENT");
	}

	@Override
	protected List<Node> createAdditionalHeaderNodes() {
		hideEmptyCategoriesCheckBox = new CheckBox(getText("UI_CATEGORY_ANALYSIS_HIDE_EMPTY_CATEGORIES"));
		hideEmptyCategoriesCheckBox.setSelected(true);
		hideEmptyCategoriesCheckBox.setWrapText(true);
		hideEmptyCategoriesCheckBox.setOnAction(event -> updateAnalysis(true));
		return List.of(hideEmptyCategoriesCheckBox);
	}

	@Override
	protected Node createMainContent() {
		NumberAxis amountAxis = new NumberAxis();
		amountAxis.setLabel(getText("UI_TABLE_AMOUNT"));
		amountAxis.setForceZeroInRange(true);
		amountAxis.setMinorTickVisible(false);
		amountAxis.setTickLabelFormatter(createAmountAxisFormatter());

		categoryAxis = new CategoryAxis();
		categoryAxis.setLabel(getText("UI_LABEL_CATEGORY"));
		categoryAxis.setAutoRanging(false);

		categoryChart = new StackedBarChart<>(amountAxis, categoryAxis);
		categoryChart.getStyleClass().add("category-analysis-chart");
		categoryChart.setTitle(getText("UI_ANALYSIS_CATEGORY_CHART_TITLE"));
		categoryChart.setAnimated(false);
		categoryChart.setCategoryGap(8);
		categoryChart.setMinHeight(CHART_MIN_HEIGHT);
		categoryChart.setMaxWidth(Double.MAX_VALUE);

		ScrollPane scrollPane = new ScrollPane(categoryChart);
		scrollPane.setFitToWidth(true);
		scrollPane.setPannable(true);
		return scrollPane;
	}

	@Override
	protected void updateView(AnalysisResult result) {
		Map<String, BigDecimal> incomeAmounts = new TreeMap<>();
		Map<String, BigDecimal> expenseAmounts = new TreeMap<>();
		Set<String> categoryNames = new TreeSet<>();
		if (!hideEmptyCategoriesCheckBox.isSelected()) {
			for (Category category : dbController.getAll(Category.class)) {
				String categoryName = category.getFullName();
				if (categoryName != null && !categoryName.isBlank()) {
					categoryNames.add(categoryName.trim());
				}
			}
		}

		for (CategorySlice slice : result.categorySlices()) {
			String categoryName = categoryName(slice);
			Map<String, BigDecimal> amounts = slice.direction() == FlowDirection.INCOME ? incomeAmounts : expenseAmounts;
			amounts.put(categoryName, slice.amount());
			categoryNames.add(categoryName);
		}

		XYChart.Series<Number, String> incomeSeries = new XYChart.Series<>();
		incomeSeries.setName(getText("UI_ANALYSIS_DIRECTION_INCOME"));
		XYChart.Series<Number, String> expenseSeries = new XYChart.Series<>();
		expenseSeries.setName(getText("UI_ANALYSIS_DIRECTION_EXPENSE"));
		Map<String, String> tooltipTexts = new TreeMap<>();
		for (String categoryName : categoryNames) {
			BigDecimal income = incomeAmounts.getOrDefault(categoryName, BigDecimal.ZERO);
			BigDecimal expense = expenseAmounts.getOrDefault(categoryName, BigDecimal.ZERO);
			String tooltipText = formatCategoryTooltip(income, expense);
			tooltipTexts.put(categoryName, tooltipText);
			incomeSeries.getData().add(new XYChart.Data<>(income, categoryName));
			expenseSeries.getData().add(new XYChart.Data<>(expense, categoryName));
		}

		categoryTooltipTexts = Map.copyOf(tooltipTexts);
		categoryAxis.setCategories(FXCollections.observableArrayList(categoryNames));
		categoryChart.getData().setAll(List.of(incomeSeries, expenseSeries));
		categoryChart.setPrefHeight(Math.max(CHART_MIN_HEIGHT, categoryNames.size() * CATEGORY_ROW_HEIGHT + CHART_VERTICAL_PADDING));
		Platform.runLater(() -> {
			categoryChart.applyCss();
			categoryChart.layout();
			installTooltips();
		});
	}

	private void installTooltips() {
		for (Map.Entry<Node, Tooltip> entry : installedTooltips.entrySet()) {
			Tooltip.uninstall(entry.getKey(), entry.getValue());
		}
		installedTooltips.clear();
		for (XYChart.Series<Number, String> series : categoryChart.getData()) {
			for (XYChart.Data<Number, String> data : series.getData()) {
				installTooltip(data.getNode(), categoryTooltipTexts.get(data.getYValue()));
			}
		}
		for (Node node : categoryAxis.getChildrenUnmodifiable()) {
			if (node instanceof Text text) {
				installTooltip(text, categoryTooltipTexts.get(text.getText()));
			}
		}
	}

	private void installTooltip(Node node, String tooltipText) {
		if (node != null && tooltipText != null) {
			Tooltip tooltip = new Tooltip(tooltipText);
			Tooltip.install(node, tooltip);
			installedTooltips.put(node, tooltip);
		}
	}

	private String formatCategoryTooltip(BigDecimal income, BigDecimal expense) {
		return "+" + formatAmount(income) + " / -" + formatAmount(expense) + " EUR";
	}

	@Override
	protected void loadAdditionalConfiguration(Map<String, String> values) {
		hideEmptyCategoriesCheckBox.setSelected(!"false".equalsIgnoreCase(values.get(HIDE_EMPTY_CATEGORIES_SETTING)));
	}

	@Override
	protected String serializeAdditionalConfiguration() {
		return ";" + HIDE_EMPTY_CATEGORIES_SETTING + "=" + hideEmptyCategoriesCheckBox.isSelected();
	}

	private String categoryName(CategorySlice slice) {
		return slice.categoryName() != null && !slice.categoryName().isBlank()
				? slice.categoryName()
				: getText("UI_ANALYSIS_CATEGORY_NONE");
	}
}
