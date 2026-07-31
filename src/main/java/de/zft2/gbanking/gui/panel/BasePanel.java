package de.zft2.gbanking.gui.panel;

import de.zft2.gbanking.BaseMessagesBean;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public abstract class BasePanel extends VBox implements BaseMessagesBean {

	protected String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	protected boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	protected Integer parseAndValidatePostiveInt(String value) {
		if (isBlank(value)) {
			return null;
		}

		try {
			int port = Integer.parseInt(value.trim());
			return port > 0 ? port : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	protected Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
