package de.gbanking.gui.fx.panel.overview;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.BasePanelHolder;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public abstract class OverviewBasePanel extends BasePanelHolder {

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

		VBox root = new VBox(8, title, content);
		root.setPadding(new Insets(5));
		root.setFillWidth(true);
		root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		getChildren().setAll(root);
		VBox.setVgrow(root, Priority.ALWAYS);

		setFillWidth(true);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setDisable(!show);
	}

	public abstract void createOverallPanel(boolean show);
}