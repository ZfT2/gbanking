package de.zft2.gbanking.gui.panel;

import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public abstract class AbstractTitledFormPanel extends BasePanel {

	protected final GridPane formGrid = FormGridHelper.createDefaultGrid();
	private final VBox contentBox = new VBox(8);
	private final TitledPane titledPane = new TitledPane();
	private final Label titleLabel = new Label();
	private final HBox titleRow = new HBox(8);

	private String titleKey;

	protected AbstractTitledFormPanel(String titleKey) {
		contentBox.setPadding(new Insets(6));
		contentBox.setFillWidth(true);
		contentBox.getChildren().add(formGrid);
		VBox.setVgrow(formGrid, Priority.NEVER);

		this.titleKey = titleKey;
		setTitle(getText(titleKey));
		titledPane.setCollapsible(false);
		titledPane.setContent(contentBox);
		titledPane.setMaxWidth(Double.MAX_VALUE);

		FormStyleUtils.styleFormPanel(contentBox);
		FormStyleUtils.styleTitledPane(titledPane);

		getChildren().setAll(titledPane);
		setFillWidth(true);
		setMaxWidth(Double.MAX_VALUE);
	}

	protected final void addFieldAbove(String key, Node field, int col, int rowGroup) {
		FormGridHelper.addFieldAbove(formGrid, getText(key), field, col, rowGroup);
	}

	protected final void addFieldAbove(String key, Node field, int col, int rowGroup, int colspan) {
		FormGridHelper.addFieldAbove(formGrid, getText(key), field, col, rowGroup, colspan);
	}

	protected final void addFieldInline(String key, Node field, int col, int row) {
		FormGridHelper.addFieldInline(formGrid, getText(key), field, col, row);
	}

	protected final void addFieldInline(String key, Node field, int col, int row, int colspan) {
		FormGridHelper.addFieldInline(formGrid, getText(key), field, col, row, colspan);
	}

	protected final void addContentNode(Node node) {
		contentBox.getChildren().add(node);
	}

	protected final void setTitleRightNode(Node node) {
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(titleLabel, Priority.ALWAYS);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		titleRow.getChildren().setAll(titleLabel, node);
		titleRow.prefWidthProperty().bind(titledPane.widthProperty().subtract(36));
		titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		titledPane.setGraphic(titleRow);
	}

	protected final void updateTitle(String additionalTitle) {
		setTitle(getText(titleKey) + " - " + additionalTitle);
	}

	protected final void resetTitle() {
		setTitle(getText(titleKey));
	}

	private void setTitle(String title) {
		titleLabel.setText(title);
		titledPane.setText(title);
	}
}
