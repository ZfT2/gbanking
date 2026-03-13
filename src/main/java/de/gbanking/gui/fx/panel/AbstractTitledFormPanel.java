// src/main/java/de/gbanking/gui/fx/panel/AbstractTitledFormPanel.java
package de.gbanking.gui.fx.panel;

import de.gbanking.gui.fx.util.FormGridHelper;
import de.gbanking.gui.fx.util.FormStyleUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public abstract class AbstractTitledFormPanel extends BasePanelHolder {

	protected final GridPane formGrid = FormGridHelper.createDefaultGrid();
	private final VBox contentBox = new VBox(8);
	private final TitledPane titledPane = new TitledPane();

	protected AbstractTitledFormPanel(String titleKey) {
		contentBox.setPadding(new Insets(6));
		contentBox.setFillWidth(true);
		contentBox.getChildren().add(formGrid);
		VBox.setVgrow(formGrid, Priority.NEVER);

		titledPane.setText(getText(titleKey));
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

	protected final void addContentNode(Node node) {
		contentBox.getChildren().add(node);
	}

	protected final void setTitleKey(String titleKey) {
		titledPane.setText(getText(titleKey));
	}
}