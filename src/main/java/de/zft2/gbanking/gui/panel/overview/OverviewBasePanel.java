package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.BasePanel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public abstract class OverviewBasePanel extends BasePanel {

	private static final Insets DEFAULT_ROOT_PADDING = new Insets(5);
	private static final double DEFAULT_TITLE_SPACING = 8.0;

	private PageContext pageContext;

	public PageContext getPageContext() {
		return pageContext;
	}

	protected final void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	private Label createOverviewTitle(String key) {
		Label title = new Label(getText(key));
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
		return title;
	}

	protected final void setOverviewContent(String titleKey, Node content) {
		Label title = createOverviewTitle(titleKey);

		if (content instanceof Region region) {
			region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			VBox.setVgrow(region, Priority.ALWAYS);
		}

		setSpacing(DEFAULT_TITLE_SPACING);
		setPadding(DEFAULT_ROOT_PADDING);
		setFillWidth(true);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		getChildren().setAll(title, content);
	}

	public void refreshOnShow() {
		// default: nothing
	}
}
