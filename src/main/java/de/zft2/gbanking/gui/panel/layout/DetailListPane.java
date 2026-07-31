package de.zft2.gbanking.gui.panel.layout;

import java.util.Objects;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

public class DetailListPane extends BorderPane {

	private static final Insets LIST_MARGIN = new Insets(8, 0, 0, 0);

	protected DetailListPane() {
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public DetailListPane(Node detailNode, Node listNode) {
		this();
		setDetailAndList(detailNode, listNode);
	}

	public final void setDetail(Node detailNode) {
		Node detail = Objects.requireNonNull(detailNode, "detailNode");
		if (detail instanceof Region region) {
			region.setMinHeight(Region.USE_PREF_SIZE);
			region.setPrefHeight(Region.USE_COMPUTED_SIZE);
			region.setMaxWidth(Double.MAX_VALUE);
		}
		setTop(detail);
	}

	protected final void setDetailAndList(Node detailNode, Node listNode) {
		setDetail(detailNode);

		Node list = Objects.requireNonNull(listNode, "listNode");
		if (list instanceof Region region) {
			region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		setCenter(list);
		BorderPane.setMargin(list, LIST_MARGIN);
	}
}
