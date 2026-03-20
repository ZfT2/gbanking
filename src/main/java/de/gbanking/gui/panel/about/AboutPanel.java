package de.gbanking.gui.panel.about;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AboutPanel {

	private final Window parentWindow;

	public AboutPanel(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public Stage createNewAboutWindow() {
		Stage dialog = new Stage();
		dialog.initOwner(parentWindow);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Über GBanking");

		Label info01 = new Label("Informationen ...... 1");
		Label info02 = new Label("Informationen ...... 2");
		Label info03 = new Label("Informationen ...... 3");
		Label info04 = new Label("Informationen ...... 4");

		Button closeButton = new Button("Schließen");
		closeButton.setOnAction(e -> dialog.close());

		VBox root = new VBox(10, info01, info02, info03, info04, closeButton);
		root.setPadding(new Insets(12));

		dialog.setScene(new Scene(root, 320, 220));
		return dialog;
	}
}