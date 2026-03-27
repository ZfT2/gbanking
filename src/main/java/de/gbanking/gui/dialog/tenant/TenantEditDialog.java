package de.gbanking.gui.dialog.tenant;

import java.util.Arrays;

import de.gbanking.gui.dialog.DialogWindowSupport;
import de.gbanking.messages.Messages;
import de.gbanking.tenant.TenantProfile;
import de.gbanking.tenant.TenantStore;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;

public class TenantEditDialog {

	private final Window parentWindow;
	private final TenantStore tenantStore;
	private final Messages messages = Messages.getInstance();

	public TenantEditDialog(Window parentWindow, TenantStore tenantStore) {
		this.parentWindow = parentWindow;
		this.tenantStore = tenantStore;
	}

	public TenantProfile showCreateDialog() {
		return showDialog(null);
	}

	public TenantProfile showEditDialog(TenantProfile tenant) {
		return showDialog(tenant);
	}

	private TenantProfile showDialog(TenantProfile tenant) {
		boolean editMode = tenant != null;
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, editMode ? "UI_DIALOG_TENANT_EDIT_TITLE" : "UI_DIALOG_TENANT_CREATE_TITLE");

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);

		TextField usernameField = new TextField(editMode ? tenant.username() : "");
		PasswordField oldPasswordField = new PasswordField();
		PasswordField passwordField = new PasswordField();
		PasswordField confirmPasswordField = new PasswordField();
		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: #b00020;");
		errorLabel.setWrapText(true);

		grid.add(new Label(getText("UI_LABEL_TENANT_USERNAME")), 0, 0);
		grid.add(usernameField, 1, 0);

		int row = 1;
		if (editMode) {
			grid.add(new Label(getText("UI_LABEL_TENANT_OLD_PASSWORD")), 0, row);
			grid.add(oldPasswordField, 1, row++);
		}

		grid.add(new Label(getText(editMode ? "UI_LABEL_TENANT_NEW_PASSWORD" : "UI_LABEL_TENANT_PASSWORD")), 0, row);
		grid.add(passwordField, 1, row++);
		grid.add(new Label(getText(editMode ? "UI_LABEL_TENANT_NEW_PASSWORD_REPEAT" : "UI_LABEL_TENANT_PASSWORD_REPEAT")), 0, row);
		grid.add(confirmPasswordField, 1, row++);
		grid.add(errorLabel, 0, row, 2, 1);

		Button saveButton = new Button(messages.getMessage("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(messages.getMessage("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);

		final TenantProfile[] result = new TenantProfile[1];

		saveButton.setOnAction(event -> {
			char[] oldPassword = oldPasswordField.getText() != null ? oldPasswordField.getText().toCharArray() : new char[0];
			char[] password = passwordField.getText() != null ? passwordField.getText().toCharArray() : new char[0];
			char[] confirmPassword = confirmPasswordField.getText() != null ? confirmPasswordField.getText().toCharArray() : new char[0];

			try {
				if (!Arrays.equals(password, confirmPassword)) {
					throw new IllegalArgumentException(getText("UI_ERROR_TENANT_PASSWORD_MISMATCH"));
				}

				if (editMode) {
					result[0] = tenantStore.updateTenant(tenant.id(), usernameField.getText(), oldPassword, password);
				} else {
					result[0] = tenantStore.createTenant(usernameField.getText(), password);
				}
				dialog.close();
			} catch (IllegalArgumentException ex) {
				errorLabel.setText(ex.getMessage());
			} finally {
				Arrays.fill(oldPassword, '\0');
				Arrays.fill(password, '\0');
				Arrays.fill(confirmPassword, '\0');
			}
		});

		cancelButton.setOnAction(event -> dialog.close());

		var buttonBar = DialogWindowSupport.createButtonBar(saveButton, cancelButton);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);

		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(grid, buttonBar), 460, editMode ? 280 : 240));
		dialog.showAndWait();
		return result[0];
	}

	private String getText(String key) {
		return messages.getMessage(key);
	}
}
