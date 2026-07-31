package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.enu.PageContext;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

class GuiLayoutStateTest {

	@AfterEach
	void resetLayoutStateAndWindows() {
		GuiLayoutState.initialize(new HashMap<>());
		JavaFxTestSupport.runFx(() -> java.util.List.copyOf(javafx.stage.Window.getWindows()).forEach(window -> {
			if (window instanceof Stage stage) {
				stage.close();
			}
		}));
	}

	@Test
	void configureTableShouldRestoreAndTrackWidthsAndSortOrder() {
		Map<String, String> options = new HashMap<>();
		options.put("layout.table.bookings.widths", "125.5000,240.2500");
		options.put("layout.table.bookings.sort", "1:D,0:A");
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			TableView<String> table = new TableView<>();
			TableColumn<String, String> first = new TableColumn<>("First");
			TableColumn<String, String> second = new TableColumn<>("Second");
			table.getColumns().setAll(List.of(first, second));

			GuiLayoutState.configureTable(table, "bookings");

			assertEquals(125.5, first.getWidth(), 0.0001);
			assertEquals(240.25, second.getWidth(), 0.0001);
			assertEquals(2, table.getSortOrder().size());
			assertSame(second, table.getSortOrder().get(0));
			assertEquals(TableColumn.SortType.DESCENDING, second.getSortType());

			first.setPrefWidth(175d);
			first.setSortType(TableColumn.SortType.DESCENDING);
			table.getSortOrder().setAll(List.of(first));
		});

		assertTrue(options.get("layout.table.bookings.widths").startsWith("175.0000,"));
		assertEquals("0:D", options.get("layout.table.bookings.sort"));
	}

	@Test
	void configureTableShouldIgnoreStoredSortForNonSortableColumns() {
		Map<String, String> options = new HashMap<>();
		options.put("layout.table.bookings.sort", "0:A,1:D");
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			TableView<String> table = new TableView<>();
			TableColumn<String, String> selectionColumn = new TableColumn<>();
			selectionColumn.setSortable(false);
			TableColumn<String, String> dateColumn = new TableColumn<>("Date");
			table.getColumns().setAll(List.of(selectionColumn, dateColumn));

			GuiLayoutState.configureTable(table, "bookings");

			assertEquals(1, table.getSortOrder().size());
			assertSame(dateColumn, table.getSortOrder().get(0));
		});
	}

	@Test
	void configureSplitAndTabPaneShouldRestoreAndTrackSelections() {
		Map<String, String> options = new HashMap<>();
		options.put("layout.split.main", "0.6500");
		options.put("layout.tab.main", "1");
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			SplitPane splitPane = new SplitPane(new Label("Left"), new Label("Right"));
			GuiLayoutState.configureSplitPane(splitPane, "main");
			assertEquals(0.65, splitPane.getDividerPositions()[0], 0.0001);
			splitPane.setDividerPositions(0.4);

			TabPane tabPane = new TabPane(new Tab("First"), new Tab("Second"));
			GuiLayoutState.configureTabPane(tabPane, "main");
			assertEquals(1, tabPane.getSelectionModel().getSelectedIndex());
			tabPane.getSelectionModel().select(0);
		});

		assertEquals("0.4000", options.get("layout.split.main"));
		assertEquals("0", options.get("layout.tab.main"));
	}

	@Test
	void malformedTableStateShouldKeepDefaults() {
		Map<String, String> options = new HashMap<>();
		options.put("layout.table.bookings.widths", "invalid");
		options.put("layout.table.bookings.sort", "99:X");
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			TableView<String> table = new TableView<>();
			TableColumn<String, String> column = new TableColumn<>("Column");
			column.setPrefWidth(180d);
			column.setSortType(TableColumn.SortType.DESCENDING);
			table.getColumns().setAll(List.of(column));
			table.getSortOrder().setAll(List.of(column));

			GuiLayoutState.configureTable(table, "bookings");

			assertEquals(180d, column.getWidth(), 0.0001);
			assertEquals(1, table.getSortOrder().size());
			assertSame(column, table.getSortOrder().get(0));
		});
	}

	@Test
	void windowStateShouldBeCapturedAndRestored() {
		Map<String, String> options = new HashMap<>();
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			Stage source = new Stage();
			source.setScene(new Scene(new VBox(), 800, 600));
			source.setWidth(920d);
			source.setHeight(710d);
			source.setX(100d);
			source.setY(120d);
			GuiLayoutState.captureWindow(source);

			Stage restored = new Stage();
			restored.setScene(new Scene(new VBox(), 640, 480));
			GuiLayoutState.restoreWindow(restored);

			assertEquals(920d, restored.getWidth(), 0.0001);
			assertEquals(710d, restored.getHeight(), 0.0001);
			assertEquals(100d, restored.getX(), 0.0001);
			assertEquals(120d, restored.getY(), 0.0001);
		});
	}

	@Test
	void restoreHandlerShouldRoundTripOnlyLayoutOptions() {
		Map<String, String> source = new HashMap<>();
		source.put("layout.table.bookings.sort", "");
		source.put("layout.tab.main", "1");
		source.put("unrelated", "ignored");
		Properties properties = new Properties();

		RestoreHandler.storeLayoutOptions(properties, source);

		assertEquals("", properties.getProperty("layout.table.bookings.sort"));
		assertEquals("1", properties.getProperty("layout.tab.main"));
		assertFalse(properties.containsKey("unrelated"));

		Map<String, String> restored = new HashMap<>();
		RestoreHandler.restoreLayoutOptions(properties, restored);
		assertEquals(Map.of("layout.table.bookings.sort", "", "layout.tab.main", "1"), restored);
	}

	@Test
	void applicationStartPageShouldRemainAccountsTransactions() {
		assertEquals(PageContext.ACCOUNTS_TRANSACTIONS, GBankingGui.START_PAGE);
	}
}
