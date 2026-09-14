package de.zft2.gbanking.gui.util;

import javafx.scene.Node;

public final class FxNodeSupport {

	private FxNodeSupport() {
	}

	public static void setVisibleManaged(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}
}
