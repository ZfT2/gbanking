package de.zft2.gbanking.gui;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.ActionScope;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import javafx.concurrent.Task;
import javafx.stage.Stage;

public interface BaseGui extends BaseMessages {

	static final String LANGUAGE = "language";

	static final String ALERT_ACCOUNT_NO_SELECTION = "ALERT_ACCOUNT_NO_SELECTION";
	static final String ALERT_REBOOKING_ASSIGN_NO_SELECTED_ACCOUNTS = "ALERT_REBOOKING_ASSIGN_NO_SELECTED_ACCOUNTS";

	default void showInfo(Stage stage, String text) {
		DialogWindowSupport.showAlert(stage, javafx.scene.control.Alert.AlertType.INFORMATION, text);
	}

	default void showWarning(Stage stage, String text) {
		DialogWindowSupport.showAlert(stage, javafx.scene.control.Alert.AlertType.WARNING, text);
	}

	default void startBackgroundTask(Task<?> task, String threadName) {
		BackgroundActionCoordinator.getInstance().start(task, threadName);
	}

	default void startBackgroundTask(Task<?> task, String threadName, ActionScope scope) {
		BackgroundActionCoordinator.getInstance().start(task, threadName, scope);
	}

}
