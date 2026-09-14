package de.zft2.gbanking.gui.util;

import javafx.application.Platform;

public final class FxThreadSupport {

	private FxThreadSupport() {
	}

	public static void run(Runnable action) {
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}
}
