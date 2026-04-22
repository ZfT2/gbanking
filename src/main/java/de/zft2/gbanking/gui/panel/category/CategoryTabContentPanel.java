package de.zft2.gbanking.gui.panel.category;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class CategoryTabContentPanel extends BorderPane {

	public CategoryTabContentPanel(Node inputPanel, Node listPanel) {
		setTop(inputPanel);
		setCenter(listPanel);
		setPadding(new Insets(5));
		BorderPane.setMargin(listPanel, new Insets(8, 0, 0, 0));
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
	}
}
