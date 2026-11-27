package de.zft2.gbanking.gui.panel.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

class LayoutPaneTest {

	@BeforeEach
	void resetLayoutState() {
		GuiLayoutState.initialize(new HashMap<>());
	}

	@Test
	void detailListPaneShouldArrangeContentAndAllowDetailReplacement() {
		JavaFxTestSupport.runFx(() -> {
			StackPane firstDetail = new StackPane();
			StackPane secondDetail = new StackPane();
			StackPane list = new StackPane();
			DetailListPane pane = new DetailListPane(firstDetail, list);

			assertSame(firstDetail, pane.getTop());
			assertSame(list, pane.getCenter());
			assertEquals(new Insets(8, 0, 0, 0), BorderPane.getMargin(list));
			assertEquals(Region.USE_PREF_SIZE, firstDetail.getMinHeight());
			assertEquals(Region.USE_COMPUTED_SIZE, firstDetail.getPrefHeight());

			pane.setDetail(secondDetail);

			assertSame(secondDetail, pane.getTop());
			assertSame(list, pane.getCenter());
			assertEquals(Region.USE_PREF_SIZE, secondDetail.getMinHeight());
		});
	}

	@Test
	void flexibleDetailShouldGrowOnlyIntoSpaceNotNeededByTheList() {
		JavaFxTestSupport.runFx(() -> {
			StackPane detail = new StackPane();
			double originalMaxHeight = detail.getMaxHeight();
			StackPane list = new StackPane();
			list.setMinHeight(120);
			DetailListPane pane = new DetailListPane(detail, list);
			pane.setFlexibleDetail(detail);
			detail.setPrefHeight(800);
			new Scene(pane, 400, 500);
			pane.resize(400, 500);
			pane.applyCss();
			pane.layout();

			assertSame(detail, pane.getTop());
			assertEquals(372, detail.getHeight(), 0.1);
			assertTrue(list.getHeight() >= list.minHeight(list.getWidth()));

			detail.setPrefHeight(80);
			pane.layout();
			assertEquals(80, detail.getHeight(), 0.1);

			StackPane replacement = new StackPane();
			pane.setDetail(replacement);
			assertEquals(originalMaxHeight, detail.getMaxHeight());
			assertEquals(Region.USE_PREF_SIZE, replacement.getMinHeight());
		});
	}

	@Test
	void masterContentPaneShouldRestoreAndTrackDividerPosition() {
		Map<String, String> options = new HashMap<>();
		options.put("layout.split.test.master", "0.6500");
		GuiLayoutState.initialize(options);

		JavaFxTestSupport.runFx(() -> {
			StackPane master = new StackPane();
			StackPane content = new StackPane();
			MasterContentPane pane = new MasterContentPane(master, content, "test.master", 0.2d);

			assertSame(master, pane.getItems().get(0));
			assertSame(content, pane.getItems().get(1));
			assertEquals(0.65d, pane.getDividerPositions()[0], 0.0001d);

			pane.setDividerPositions(0.4d);
		});

		assertEquals("0.4000", options.get("layout.split.test.master"));
	}
}
