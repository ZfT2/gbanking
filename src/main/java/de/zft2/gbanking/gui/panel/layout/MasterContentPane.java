package de.zft2.gbanking.gui.panel.layout;

import java.util.Objects;

import de.zft2.gbanking.gui.GuiLayoutState;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

public final class MasterContentPane extends SplitPane {

	public MasterContentPane(Node masterNode, Node contentNode, String layoutKey, double dividerPosition) {
		super(Objects.requireNonNull(masterNode, "masterNode"), Objects.requireNonNull(contentNode, "contentNode"));
		setOrientation(Orientation.HORIZONTAL);
		setDividerPositions(requireValidDividerPosition(dividerPosition));
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		GuiLayoutState.configureSplitPane(this, layoutKey);
	}

	private static double requireValidDividerPosition(double dividerPosition) {
		if (!Double.isFinite(dividerPosition) || dividerPosition < 0d || dividerPosition > 1d) {
			throw new IllegalArgumentException("Divider position must be between 0 and 1");
		}
		return dividerPosition;
	}
}
