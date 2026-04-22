package de.zft2.gbanking.gui.panel;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public abstract class AbstractReadonlyDetailPanel extends AbstractTitledFormPanel {

	protected AbstractReadonlyDetailPanel(String titleKey) {
		super(titleKey);
	}

	protected final void makeReadOnly(TextField... fields) {
		for (TextField field : fields) {
			field.setEditable(false);
		}
	}

	protected final void makeReadOnly(TextArea... areas) {
		for (TextArea area : areas) {
			area.setEditable(false);
		}
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