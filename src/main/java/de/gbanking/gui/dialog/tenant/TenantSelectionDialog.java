package de.gbanking.gui.dialog.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.gbanking.gui.dialog.DialogWindowSupport;
import de.gbanking.messages.Messages;
import de.gbanking.tenant.TenantProfile;
import de.gbanking.tenant.TenantStore;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

public class TenantSelectionDialog {

	public record TenantLoginResult(TenantProfile tenant, String lastSelectedTenantId, String languageCode) {
	}

	private record LanguageOption(String code, String label) {
		@Override
		public String toString() {
			return label;
		}
	}

	private final Window parentWindow;
	private final TenantStore tenantStore;
	private final Messages messages = Messages.getInstance();
	private String selectedLanguageCode = Messages.toLanguageCode(Messages.getLocale());

	public TenantSelectionDialog(Window parentWindow, TenantStore tenantStore) {
		this.parentWindow = parentWindow;
		this.tenantStore = tenantStore;
	}

	public Optional<TenantLoginResult> showAndWait(String lastSelectedTenantId, String initialLanguageCode) {
		applyLanguage(initialLanguageCode);

		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_MENU_FILE_OPEN");
		dialog.setTitle(getText("UI_DIALOG_TENANT_SELECTION_TITLE"));

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);

		ComboBox<LanguageOption> languageBox = new ComboBox<>();
		languageBox.setMaxWidth(Double.MAX_VALUE);

		ComboBox<TenantProfile> tenantBox = new ComboBox<>();
		tenantBox.setMaxWidth(Double.MAX_VALUE);
		tenantBox.setCellFactory(listView -> new TenantListCell());
		tenantBox.setButtonCell(new TenantListCell());

		PasswordField passwordField = new PasswordField();
		Label hintLabel = new Label();
		hintLabel.setWrapText(true);
		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: #b00020;");
		errorLabel.setWrapText(true);
		Label languageLabel = new Label();
		Label tenantLabel = new Label();
		Label passwordLabel = new Label();

		grid.add(languageLabel, 0, 0);
		grid.add(languageBox, 1, 0);
		grid.add(tenantLabel, 0, 1);
		grid.add(tenantBox, 1, 1);
		grid.add(passwordLabel, 0, 2);
		grid.add(passwordField, 1, 2);
		grid.add(hintLabel, 0, 3, 2, 1);
		grid.add(errorLabel, 0, 4, 2, 1);
		GridPane.setHgrow(languageBox, Priority.ALWAYS);
		GridPane.setHgrow(tenantBox, Priority.ALWAYS);
		GridPane.setHgrow(passwordField, Priority.ALWAYS);

		Button loginButton = new Button();
		Button newButton = new Button();
		Button editButton = new Button();
		Button deleteButton = new Button();
		Button cancelButton = new Button();

		loginButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);

		TenantEditDialog tenantEditDialog = new TenantEditDialog(dialog, tenantStore);
		final TenantLoginResult[] result = new TenantLoginResult[1];

		Runnable refreshTenants = () -> {
			List<TenantProfile> tenants = new ArrayList<>(tenantStore.getTenants());
			tenantBox.setItems(FXCollections.observableArrayList(tenants));

			TenantProfile selectedTenant = selectTenant(tenantBox, tenants, lastSelectedTenantId);
			boolean hasSelection = selectedTenant != null;
			loginButton.setDisable(!hasSelection);
			editButton.setDisable(!hasSelection);
			deleteButton.setDisable(!hasSelection);
			hintLabel.setText(hasSelection ? "" : getText("UI_INFO_TENANT_CREATE_FIRST"));
			errorLabel.setText("");
			passwordField.clear();
		};

		Runnable refreshTexts = () -> {
			refreshLanguageOptions(languageBox);
			dialog.setTitle(getText("UI_DIALOG_TENANT_SELECTION_TITLE"));
			languageLabel.setText(getText("UI_LABEL_LANGUAGE"));
			tenantLabel.setText(getText("UI_LABEL_TENANT"));
			passwordLabel.setText(getText("UI_LABEL_TENANT_PASSWORD"));
			loginButton.setText(getText("UI_BUTTON_LOGIN"));
			newButton.setText(getText("UI_BUTTON_NEW"));
			editButton.setText(getText("UI_BUTTON_EDIT"));
			deleteButton.setText(getText("UI_BUTTON_DELETE"));
			cancelButton.setText(getText("UI_BUTTON_CANCEL"));
			if (tenantBox.getValue() == null) {
				hintLabel.setText(getText("UI_INFO_TENANT_CREATE_FIRST"));
			}
		};

		languageBox.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue == null) {
				return;
			}
			applyLanguage(newValue.code());
			refreshTexts.run();
			errorLabel.setText("");
		});

		refreshTexts.run();
		languageBox.getSelectionModel().select(selectLanguageOption(languageBox, selectedLanguageCode));
		refreshTenants.run();

		tenantBox.valueProperty().addListener((obs, oldValue, newValue) -> {
			loginButton.setDisable(newValue == null);
			editButton.setDisable(newValue == null);
			deleteButton.setDisable(newValue == null);
			errorLabel.setText("");
			passwordField.clear();
		});

		loginButton.setOnAction(event -> {
			TenantProfile selectedTenant = tenantBox.getValue();
			char[] password = passwordField.getText() != null ? passwordField.getText().toCharArray() : new char[0];

			try {
				Optional<TenantProfile> authenticatedTenant = tenantStore.authenticate(selectedTenant != null ? selectedTenant.id() : null, password);
				if (authenticatedTenant.isEmpty()) {
					errorLabel.setText(getText("UI_ERROR_TENANT_LOGIN_FAILED"));
					return;
				}

				result[0] = new TenantLoginResult(authenticatedTenant.get(), authenticatedTenant.get().id(), selectedLanguageCode);
				dialog.close();
			} finally {
				Arrays.fill(password, '\0');
			}
		});

		newButton.setOnAction(event -> {
			TenantProfile newTenant = tenantEditDialog.showCreateDialog();
			if (newTenant != null) {
				refreshTenants.run();
				tenantBox.getSelectionModel().select(newTenant);
				passwordField.requestFocus();
			}
		});

		editButton.setOnAction(event -> {
			TenantProfile selectedTenant = tenantBox.getValue();
			if (selectedTenant == null) {
				return;
			}

			TenantProfile updatedTenant = tenantEditDialog.showEditDialog(selectedTenant);
			if (updatedTenant != null) {
				refreshTenants.run();
				tenantBox.getSelectionModel().select(updatedTenant);
			}
		});

		deleteButton.setOnAction(event -> {
			TenantProfile selectedTenant = tenantBox.getValue();
			if (selectedTenant == null) {
				return;
			}

			Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, getText("UI_QUESTION_TENANT_DELETE", selectedTenant.username()), ButtonType.OK,
					ButtonType.CANCEL);
			confirmation.initOwner(parentWindow != null ? parentWindow : dialog);
			confirmation.setHeaderText(null);
			confirmation.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

			if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
				return;
			}

			try {
				deleteDirectory(tenantStore.getTenantDirectory(selectedTenant.id()));
				tenantStore.deleteTenant(selectedTenant.id());
				refreshTenants.run();
			} catch (IllegalStateException ex) {
				showDeleteError(dialog, ex.getMessage());
			}
		});

		cancelButton.setOnAction(event -> dialog.close());

		var buttonBar = DialogWindowSupport.createButtonBar(loginButton, newButton, editButton, deleteButton, cancelButton);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);

		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(grid, buttonBar), 520, 240));
		dialog.showAndWait();
		return Optional.ofNullable(result[0]);
	}

	public String getSelectedLanguageCode() {
		return selectedLanguageCode;
	}

	private TenantProfile selectTenant(ComboBox<TenantProfile> tenantBox, List<TenantProfile> tenants, String lastSelectedTenantId) {
		if (tenants.isEmpty()) {
			tenantBox.getSelectionModel().clearSelection();
			return null;
		}

		TenantProfile selectedTenant = tenants.stream().filter(tenant -> tenant.id().equals(lastSelectedTenantId)).findFirst().orElse(tenants.get(0));
		tenantBox.getSelectionModel().select(selectedTenant);
		return selectedTenant;
	}

	private void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}

		try {
			Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new IllegalStateException(getText("UI_ERROR_TENANT_DELETE_DIRECTORY"), e);
				}
			});
		} catch (IOException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_DELETE_DIRECTORY"), e);
		}
	}

	private void showDeleteError(Stage dialog, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.initOwner(parentWindow != null ? parentWindow : dialog);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		alert.showAndWait();
	}

	private String getText(String key) {
		return messages.getMessage(key);
	}

	private String getText(String key, Object... values) {
		return messages.getFormattedMessage(key, values);
	}

	private void applyLanguage(String languageCode) {
		selectedLanguageCode = Messages.toLanguageCode(Messages.localeFromCode(languageCode));
		Messages.setLocale(Messages.localeFromCode(selectedLanguageCode));
	}

	private LanguageOption selectLanguageOption(ComboBox<LanguageOption> languageBox, String languageCode) {
		String effectiveCode = Messages.toLanguageCode(Messages.localeFromCode(languageCode));
		return languageBox.getItems().stream().filter(option -> option.code().equals(effectiveCode)).findFirst().orElse(languageBox.getItems().get(0));
	}

	private void refreshLanguageOptions(ComboBox<LanguageOption> languageBox) {
		String currentCode = languageBox.getValue() != null ? languageBox.getValue().code() : selectedLanguageCode;
		languageBox.setItems(FXCollections.observableArrayList(new LanguageOption("de", getText("UI_LANGUAGE_GERMAN")),
				new LanguageOption("en", getText("UI_LANGUAGE_ENGLISH"))));
		languageBox.getSelectionModel().select(selectLanguageOption(languageBox, currentCode));
	}

	private static final class TenantListCell extends ListCell<TenantProfile> {

		@Override
		protected void updateItem(TenantProfile item, boolean empty) {
			super.updateItem(item, empty);
			setText(empty || item == null ? null : item.username());
		}
	}
}
