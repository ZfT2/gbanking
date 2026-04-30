package de.zft2.gbanking.gui.panel.about;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.messages.Messages;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AboutPanel {

	private static final double DIALOG_WIDTH = 680;
	private static final double DIALOG_HEIGHT = 700;
	private static final double LOGO_FIT_WIDTH = 220;
	private static final Path LICENSE_PATH = Path.of("LICENSE");
	private static final String LOGO_RESOURCE = "/logo/GBankingLogo.png";

	private final Window parentWindow;
	private final Messages messages = Messages.getInstance();

	public AboutPanel(Window parentWindow) {
		this.parentWindow = parentWindow;
	}

	public Stage createNewAboutWindow() {
		Stage dialog = new Stage();
		dialog.initOwner(parentWindow);
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(getText("UI_DIALOG_ABOUT_TITLE"));

		Label descriptionLabel = createWrappedLabel(getText("UI_DIALOG_ABOUT_DESCRIPTION"));
		Label versionLabel = createWrappedLabel(messages.getFormattedMessage("UI_DIALOG_ABOUT_VERSION", BuildInfo.getProgramVersion()));
		Label javaVersionLabel = createWrappedLabel(messages.getFormattedMessage("UI_DIALOG_ABOUT_JAVA_VERSION", BuildInfo.getJavaVersion()));
		Label disclaimerTitleLabel = createWrappedLabel(getText("UI_DIALOG_ABOUT_DISCLAIMER_TITLE"));
		Label disclaimerLabel = createWrappedLabel(getText("UI_DIALOG_ABOUT_DISCLAIMER"));
		Label licenseTitleLabel = createWrappedLabel(getText("UI_DIALOG_ABOUT_LICENSE_TITLE"));
		ImageView logoImageView = createLogoImageView();
		VBox infoBox = new VBox(10, versionLabel, javaVersionLabel, disclaimerTitleLabel, disclaimerLabel);
		HBox summaryBox = new HBox(16, infoBox, logoImageView);
		TextArea licenseTextArea = createLicenseTextArea();

		Button closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		closeButton.setOnAction(e -> dialog.close());

		VBox root = new VBox(10, descriptionLabel, summaryBox, licenseTitleLabel, licenseTextArea, closeButton);
		root.setPadding(new Insets(12));
		HBox.setHgrow(infoBox, Priority.ALWAYS);
		VBox.setVgrow(licenseTextArea, Priority.ALWAYS);

		dialog.setScene(new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT));
		return dialog;
	}

	private Label createWrappedLabel(String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		return label;
	}

	private TextArea createLicenseTextArea() {
		TextArea licenseTextArea = new TextArea(loadLicenseText());
		licenseTextArea.setEditable(false);
		licenseTextArea.setWrapText(true);
		licenseTextArea.setPrefRowCount(20);
		return licenseTextArea;
	}

	private ImageView createLogoImageView() {
		try (InputStream in = AboutPanel.class.getResourceAsStream(LOGO_RESOURCE)) {
			if (in == null) {
				return new ImageView();
			}
			ImageView imageView = new ImageView(new Image(in));
			imageView.setPreserveRatio(true);
			imageView.setFitWidth(LOGO_FIT_WIDTH);
			imageView.setSmooth(true);
			return imageView;
		} catch (IOException e) {
			return new ImageView();
		}
	}

	private String loadLicenseText() {
		try {
			return Files.readString(LICENSE_PATH, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return getText("UI_DIALOG_ABOUT_LICENSE_LOAD_ERROR");
		}
	}

	private String getText(String key) {
		return messages.getMessage(key);
	}
}
