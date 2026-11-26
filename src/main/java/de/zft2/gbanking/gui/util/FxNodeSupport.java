package de.zft2.gbanking.gui.util;

import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

public final class FxNodeSupport {

	private FxNodeSupport() {
	}

	public static void setVisibleManaged(Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}

	public static ToggleButton createDisclosureToggle(Node target, String showText, String hideText) {
		ToggleButton toggle = new ToggleButton("\u25bc");
		Tooltip tooltip = new Tooltip(showText);
		toggle.setTooltip(tooltip);
		toggle.setAccessibleText(showText);
		toggle.selectedProperty().addListener((obs, wasExpanded, isExpanded) -> {
			boolean expanded = Boolean.TRUE.equals(isExpanded);
			setVisibleManaged(target, expanded);
			toggle.setText(expanded ? "\u25b2" : "\u25bc");
			String tooltipText = expanded ? hideText : showText;
			tooltip.setText(tooltipText);
			toggle.setAccessibleText(tooltipText);
		});
		return toggle;
	}
}
