package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.BasePanelHolder;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public abstract class OverviewBasePanel extends BasePanelHolder {

	private static final Insets DEFAULT_ROOT_PADDING = new Insets(5);
	private static final Insets DEFAULT_CENTER_MARGIN = new Insets(8, 0, 0, 0);
	private static final double DEFAULT_TITLE_SPACING = 8.0;

	private PageContext pageContext;

	public PageContext getPageContext() {
		return pageContext;
	}

	public void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	protected Label createOverviewTitle(String key) {
		Label title = new Label(getText(key));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
		return title;
	}

	protected void setOverviewContent(String titleKey, Node content, boolean show) {
		Label title = createOverviewTitle(titleKey);

		if (content instanceof Region region) {
			region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			VBox.setVgrow(region, Priority.ALWAYS);
		}

		VBox root = new VBox(DEFAULT_TITLE_SPACING, title, content);
		root.setPadding(DEFAULT_ROOT_PADDING);
		root.setFillWidth(true);
		root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		getChildren().setAll(root);
		VBox.setVgrow(root, Priority.ALWAYS);

		setFillWidth(true);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setDisable(!show);
	}

	protected BorderPane createTopCenterLayout(Node top, Node center) {
		BorderPane pane = new BorderPane();
		pane.setTop(top);
		pane.setCenter(center);
		pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		BorderPane.setMargin(center, DEFAULT_CENTER_MARGIN);
		return pane;
	}

	protected SplitPane createSplitPane(double dividerPosition, Node... nodes) {
		SplitPane splitPane = new SplitPane(nodes);
		splitPane.setDividerPositions(dividerPosition);
		splitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		return splitPane;
	}

	protected void prepareDetailPanel(Region detailPanel) {
		detailPanel.setMinHeight(Region.USE_PREF_SIZE);
		detailPanel.setPrefHeight(Region.USE_COMPUTED_SIZE);
		detailPanel.setMaxWidth(Double.MAX_VALUE);
	}

	public abstract void createOverallPanel(boolean show);

	public void refreshOnShow() {
		// default: nothing
	}
}