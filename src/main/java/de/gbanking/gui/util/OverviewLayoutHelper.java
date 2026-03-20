package de.gbanking.gui.util;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class OverviewLayoutHelper {

	private OverviewLayoutHelper() {
	}

	public static Label createOverviewTitle(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("overview-title");
		return label;
	}

	public static VBox createOverviewRoot(Label title, Node content) {
		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		VBox.setVgrow(content, Priority.ALWAYS);
		return root;
	}

	public static SplitPane createMainSplit(Node left, Node right, double dividerPosition, double leftMinWidth, double leftPrefWidth, double leftMaxWidth) {
		if (left instanceof Region leftRegion) {
			leftRegion.setMinWidth(leftMinWidth);
			leftRegion.setPrefWidth(leftPrefWidth);
			leftRegion.setMaxWidth(leftMaxWidth);
		}

		SplitPane splitPane = new SplitPane(left, right);
		splitPane.setDividerPositions(dividerPosition);
		return splitPane;
	}
}