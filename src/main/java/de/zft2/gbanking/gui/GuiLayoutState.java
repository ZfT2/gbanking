package de.zft2.gbanking.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.collections.ListChangeListener;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class GuiLayoutState {

	static final String OPTION_PREFIX = "layout.";

	private static final String WINDOW_PREFIX = OPTION_PREFIX + "window.";
	private static final String TABLE_PREFIX = OPTION_PREFIX + "table.";
	private static final String SPLIT_PREFIX = OPTION_PREFIX + "split.";
	private static final String TAB_PREFIX = OPTION_PREFIX + "tab.";
	private static final String ATTACHED_PREFIX = GuiLayoutState.class.getName() + ".attached.";
	private static final double MIN_COLUMN_WIDTH = 20d;
	private static final double MAX_COLUMN_WIDTH = 10_000d;

	private static Map<String, String> options = new HashMap<>();

	private GuiLayoutState() {
	}

	public static void initialize(Map<String, String> optionsMap) {
		options = Objects.requireNonNull(optionsMap, "optionsMap");
	}

	public static void restoreWindow(Stage stage) {
		Objects.requireNonNull(stage, "stage");
		Double width = readPositiveDouble(WINDOW_PREFIX + "width");
		Double height = readPositiveDouble(WINDOW_PREFIX + "height");
		if (width != null) {
			stage.setWidth(width);
		}
		if (height != null) {
			stage.setHeight(height);
		}

		Double x = readDouble(WINDOW_PREFIX + "x");
		Double y = readDouble(WINDOW_PREFIX + "y");
		if (x != null && y != null && isVisibleOnAnyScreen(stage, x, y, width, height)) {
			stage.setX(x);
			stage.setY(y);
		}
		stage.setMaximized(Boolean.parseBoolean(options.get(WINDOW_PREFIX + "maximized")));
	}

	public static void captureWindow(Stage stage) {
		if (stage == null) {
			return;
		}
		if (!stage.isMaximized()) {
			putFiniteDouble(WINDOW_PREFIX + "width", stage.getWidth());
			putFiniteDouble(WINDOW_PREFIX + "height", stage.getHeight());
			putFiniteDouble(WINDOW_PREFIX + "x", stage.getX());
			putFiniteDouble(WINDOW_PREFIX + "y", stage.getY());
		}
		options.put(WINDOW_PREFIX + "maximized", Boolean.toString(stage.isMaximized()));
	}

	public static <S> void configureTable(TableView<S> table, String layoutKey) {
		Objects.requireNonNull(table, "table");
		String normalizedKey = normalizeLayoutKey(layoutKey);
		String markerKey = ATTACHED_PREFIX + "table";
		if (table.getProperties().putIfAbsent(markerKey, normalizedKey) != null) {
			return;
		}

		String widthKey = TABLE_PREFIX + normalizedKey + ".widths";
		String sortKey = TABLE_PREFIX + normalizedKey + ".sort";
		restoreColumnWidths(table, widthKey);
		restoreSortOrder(table, sortKey);

		table.getColumns().forEach(column -> {
			column.widthProperty().addListener((obs, oldWidth, newWidth) -> storeColumnWidths(table, widthKey));
			column.sortTypeProperty().addListener((obs, oldType, newType) -> storeSortOrder(table, sortKey));
		});
		table.getSortOrder().addListener((ListChangeListener<TableColumn<S, ?>>) change -> storeSortOrder(table, sortKey));
	}

	public static void configureSplitPane(SplitPane splitPane, String layoutKey) {
		Objects.requireNonNull(splitPane, "splitPane");
		String normalizedKey = normalizeLayoutKey(layoutKey);
		String markerKey = ATTACHED_PREFIX + "split";
		if (splitPane.getProperties().putIfAbsent(markerKey, normalizedKey) != null) {
			return;
		}

		String optionKey = SPLIT_PREFIX + normalizedKey;
		restoreDividerPositions(splitPane, optionKey);
		splitPane.getDividers().forEach(divider -> divider.positionProperty()
				.addListener((obs, oldPosition, newPosition) -> storeDividerPositions(splitPane, optionKey)));
	}

	public static void configureTabPane(TabPane tabPane, String layoutKey) {
		Objects.requireNonNull(tabPane, "tabPane");
		String normalizedKey = normalizeLayoutKey(layoutKey);
		String markerKey = ATTACHED_PREFIX + "tab";
		if (tabPane.getProperties().putIfAbsent(markerKey, normalizedKey) != null) {
			return;
		}

		String optionKey = TAB_PREFIX + normalizedKey;
		Integer selectedIndex = readInteger(optionKey);
		if (selectedIndex != null && selectedIndex >= 0 && selectedIndex < tabPane.getTabs().size()) {
			tabPane.getSelectionModel().select(selectedIndex);
		}
		tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> {
			if (newIndex.intValue() >= 0) {
				options.put(optionKey, Integer.toString(newIndex.intValue()));
			}
		});
	}

	private static boolean isVisibleOnAnyScreen(Stage stage, double x, double y, Double width, Double height) {
		double sceneWidth = stage.getScene() != null ? stage.getScene().getWidth() : 1d;
		double sceneHeight = stage.getScene() != null ? stage.getScene().getHeight() : 1d;
		double effectiveWidth = width != null ? width : sceneWidth;
		double effectiveHeight = height != null ? height : sceneHeight;
		return !Screen.getScreensForRectangle(x, y, Math.max(1d, effectiveWidth), Math.max(1d, effectiveHeight)).isEmpty();
	}

	private static <S> void restoreColumnWidths(TableView<S> table, String optionKey) {
		String storedWidths = options.get(optionKey);
		if (storedWidths == null) {
			return;
		}
		String[] values = storedWidths.split(",", -1);
		if (values.length != table.getColumns().size()) {
			return;
		}
		for (int index = 0; index < values.length; index++) {
			Double width = parseDouble(values[index]);
			if (width != null && width >= MIN_COLUMN_WIDTH && width <= MAX_COLUMN_WIDTH) {
				table.getColumns().get(index).setPrefWidth(width);
			}
		}
	}

	private static <S> void storeColumnWidths(TableView<S> table, String optionKey) {
		String widths = table.getColumns().stream()
				.map(column -> formatDouble(column.getWidth()))
				.collect(Collectors.joining(","));
		options.put(optionKey, widths);
	}

	private static <S> void restoreSortOrder(TableView<S> table, String optionKey) {
		if (!options.containsKey(optionKey)) {
			return;
		}
		List<TableColumn<S, ?>> restoredSortOrder = new ArrayList<>();
		Set<Integer> restoredColumnIndices = new HashSet<>();
		String storedSortOrder = options.get(optionKey);
		if (storedSortOrder == null || storedSortOrder.isBlank()) {
			table.getSortOrder().clear();
			return;
		}
		for (String item : storedSortOrder.split(",")) {
			TableColumn<S, ?> column = restoreSortColumn(table, item, restoredColumnIndices);
			if (column != null) {
				restoredSortOrder.add(column);
			}
		}
		if (!restoredSortOrder.isEmpty()) {
			table.getSortOrder().setAll(restoredSortOrder);
		}
	}

	private static <S> TableColumn<S, ?> restoreSortColumn(TableView<S> table, String item, Set<Integer> restoredColumnIndices) {
		String[] parts = item.split(":", -1);
		Integer columnIndex = parts.length == 2 ? parseInteger(parts[0]) : null;
		if (columnIndex == null || columnIndex < 0 || columnIndex >= table.getColumns().size()) {
			return null;
		}
		if (!("A".equals(parts[1]) || "D".equals(parts[1])) || !restoredColumnIndices.add(columnIndex)) {
			return null;
		}
		TableColumn<S, ?> column = table.getColumns().get(columnIndex);
		if (!column.isSortable()) {
			return null;
		}
		column.setSortType("D".equals(parts[1]) ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
		return column;
	}

	private static <S> void storeSortOrder(TableView<S> table, String optionKey) {
		String sortOrder = table.getSortOrder().stream()
				.map(column -> table.getColumns().indexOf(column) + ":"
						+ (column.getSortType() == TableColumn.SortType.DESCENDING ? "D" : "A"))
				.collect(Collectors.joining(","));
		options.put(optionKey, sortOrder);
	}

	private static void restoreDividerPositions(SplitPane splitPane, String optionKey) {
		String value = options.get(optionKey);
		if (value == null) {
			return;
		}
		String[] parts = value.split(",", -1);
		if (parts.length != splitPane.getDividers().size()) {
			return;
		}
		double[] positions = new double[parts.length];
		for (int index = 0; index < parts.length; index++) {
			Double position = parseDouble(parts[index]);
			if (position == null || position < 0d || position > 1d) {
				return;
			}
			positions[index] = position;
		}
		splitPane.setDividerPositions(positions);
	}

	private static void storeDividerPositions(SplitPane splitPane, String optionKey) {
		String positions = splitPane.getDividers().stream()
				.map(divider -> formatDouble(divider.getPosition()))
				.collect(Collectors.joining(","));
		options.put(optionKey, positions);
	}

	private static Double readPositiveDouble(String key) {
		Double value = readDouble(key);
		return value != null && value > 0d ? value : null;
	}

	private static Double readDouble(String key) {
		return parseDouble(options.get(key));
	}

	private static Integer readInteger(String key) {
		return parseInteger(options.get(key));
	}

	private static Double parseDouble(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			double parsed = Double.parseDouble(value);
			return Double.isFinite(parsed) ? parsed : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer parseInteger(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static void putFiniteDouble(String key, double value) {
		if (Double.isFinite(value)) {
			options.put(key, formatDouble(value));
		}
	}

	private static String formatDouble(double value) {
		return String.format(Locale.ROOT, "%.4f", value);
	}

	private static String normalizeLayoutKey(String layoutKey) {
		String value = Objects.requireNonNull(layoutKey, "layoutKey").trim();
		if (value.isEmpty() || !value.matches("[A-Za-z0-9._-]+")) {
			throw new IllegalArgumentException("Invalid layout key: " + layoutKey);
		}
		return value;
	}
}
