package de.zft2.gbanking.gui.panel;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;

import de.zft2.gbanking.gui.util.FormStyleUtils;

public abstract class AbstractReadonlyDetailPanel extends AbstractTitledFormPanel {

	protected AbstractReadonlyDetailPanel(String titleKey) {
		super(titleKey);
	}

	protected final void makeReadOnly(TextInputControl... controls) {
		for (TextInputControl control : controls) {
			control.setEditable(false);
		}
		FormStyleUtils.setReadOnlyStyle(true, controls);
	}

	protected final void disable(CheckBox... boxes) {
		for (CheckBox box : boxes) {
			box.setDisable(true);
		}
	}

	protected final void disable(ComboBox<?>... combos) {
		for (ComboBox<?> combo : combos) {
			combo.setDisable(true);
			combo.setMaxWidth(Double.MAX_VALUE);
		}
	}
}
