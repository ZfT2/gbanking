package de.zft2.gbanking.gui.panel.layout;

import java.util.Objects;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

public class DetailListPane extends BorderPane {

	private static final Insets LIST_MARGIN = new Insets(8, 0, 0, 0);

	private Region flexibleDetail;
	private double previousMaxHeight;

	protected DetailListPane() {
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public DetailListPane(Node detailNode, Node listNode) {
		this();
		setDetailAndList(detailNode, listNode);
	}

	public final void setDetail(Node detailNode) {
		Node detail = Objects.requireNonNull(detailNode, "detailNode");
		if (flexibleDetail != null) {
			flexibleDetail.setMaxHeight(previousMaxHeight);
			flexibleDetail = null;
		}
		if (detail instanceof Region region) {
			region.setMinHeight(Region.USE_PREF_SIZE);
			region.setPrefHeight(Region.USE_COMPUTED_SIZE);
			region.setMaxWidth(Double.MAX_VALUE);
		}
		setTop(detail);
	}

	/** Limits detail height to leave the list its existing minimum height. */
	public final void setFlexibleDetail(Region detailNode) {
		setDetail(detailNode);
		flexibleDetail = detailNode;
		previousMaxHeight = detailNode.getMaxHeight();
		detailNode.setMinHeight(0);
	}

	@Override
	protected double computeMinHeight(double width) {
		if (flexibleDetail == null) {
			return super.computeMinHeight(width);
		}
		// BorderPane otherwise reserves the top node's preferred height even at minimum size.
		double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
		return snappedTopInset() + snappedBottomInset() + LIST_MARGIN.getTop() + LIST_MARGIN.getBottom()
				+ getCenter().minHeight(contentWidth);
	}

	@Override
	protected void layoutChildren() {
		if (flexibleDetail != null) {
			double width = getWidth() - snappedLeftInset() - snappedRightInset();
			double height = getHeight() - snappedTopInset() - snappedBottomInset()
					- LIST_MARGIN.getTop() - LIST_MARGIN.getBottom() - getCenter().minHeight(width);
			flexibleDetail.setMaxHeight(Math.max(0, height));
		}
		super.layoutChildren();
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
