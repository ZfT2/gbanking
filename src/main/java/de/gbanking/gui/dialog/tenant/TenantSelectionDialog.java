package de.gbanking.gui.dialog.tenant;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
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

	private record DialogControls(GridPane grid, ComboBox<LanguageOption> languageBox, ComboBox<TenantProfile> tenantBox, PasswordField passwordField,
			Label hintLabel, Label errorLabel, Label languageLabel, Label tenantLabel, Label passwordLabel, Button loginButton, Button newButton, Button editButton,
			Button deleteButton, Button cancelButton) {
	}

	private static final class DialogState {
		private final Stage dialog;
		private final DialogControls controls;
		private final TenantEditDialog tenantEditDialog;
		private final TenantLoginResult[] result = new TenantLoginResult[1];

		private DialogState(Stage dialog, DialogControls controls, TenantEditDialog tenantEditDialog) {
			this.dialog = dialog;
			this.controls = controls;
			this.tenantEditDialog = tenantEditDialog;
		}
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
		DialogState state = createDialogState();
		initializeDialogState(state, lastSelectedTenantId);
		showDialog(state);
		return Optional.ofNullable(state.result[0]);
	}

	public String getSelectedLanguageCode() {
		return selectedLanguageCode;
	}

	private TenantProfile selectTenant(ComboBox<TenantProfile> tenantBox, List<TenantProfile> tenants, String lastSelectedTenantId) {
		if (tenants.isEmpty()) {
			tenantBox.getSelectionModel().clearSelection();
			return null;
		}

		TenantProfile selectedTenant = tenants.get(0);
		for (TenantProfile tenant : tenants) {
			if (tenant.id().equals(lastSelectedTenantId)) {
				selectedTenant = tenant;
				break;
			}
		}
		tenantBox.getSelectionModel().select(selectedTenant);
		return selectedTenant;
	}

	private void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}

		try {
			Files.walkFileTree(directory, new DeleteDirectoryVisitor());
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
		for (LanguageOption option : languageBox.getItems()) {
			if (option.code().equals(effectiveCode)) {
				return option;
			}
		}
		return languageBox.getItems().get(0);
	}

	private void refreshLanguageOptions(ComboBox<LanguageOption> languageBox) {
		String currentCode = languageBox.getValue() != null ? languageBox.getValue().code() : selectedLanguageCode;
		languageBox.setItems(FXCollections.observableArrayList(new LanguageOption("de", getText("UI_LANGUAGE_GERMAN")),
				new LanguageOption("en", getText("UI_LANGUAGE_ENGLISH"))));
		languageBox.getSelectionModel().select(selectLanguageOption(languageBox, currentCode));
	}

	private void refreshTenants(DialogControls controls, String lastSelectedTenantId) {
		List<TenantProfile> tenants = new ArrayList<>(tenantStore.getTenants());
		controls.tenantBox().setItems(FXCollections.observableArrayList(tenants));

		TenantProfile selectedTenant = selectTenant(controls.tenantBox(), tenants, lastSelectedTenantId);
		updateTenantActionState(selectedTenant != null, controls.loginButton(), controls.editButton(), controls.deleteButton());
		controls.hintLabel().setText(selectedTenant != null ? "" : getText("UI_INFO_TENANT_CREATE_FIRST"));
		controls.errorLabel().setText("");
		controls.passwordField().clear();
	}

	private void refreshTexts(Stage dialog, DialogControls controls) {
		refreshLanguageOptions(controls.languageBox());
		dialog.setTitle(getText("UI_DIALOG_TENANT_SELECTION_TITLE"));
		controls.languageLabel().setText(getText("UI_LABEL_LANGUAGE"));
		controls.tenantLabel().setText(getText("UI_LABEL_TENANT"));
		controls.passwordLabel().setText(getText("UI_LABEL_TENANT_PASSWORD"));
		controls.loginButton().setText(getText("UI_BUTTON_LOGIN"));
		controls.newButton().setText(getText("UI_BUTTON_NEW"));
		controls.editButton().setText(getText("UI_BUTTON_EDIT"));
		controls.deleteButton().setText(getText("UI_BUTTON_DELETE"));
		controls.cancelButton().setText(getText("UI_BUTTON_CANCEL"));
		if (controls.tenantBox().getValue() == null) {
			controls.hintLabel().setText(getText("UI_INFO_TENANT_CREATE_FIRST"));
		}
	}

	private void configureLanguageSelector(ComboBox<LanguageOption> languageBox, Label errorLabel, Runnable refreshTexts) {
		languageBox.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue == null) {
				return;
			}
			applyLanguage(newValue.code());
			refreshTexts.run();
			errorLabel.setText("");
		});
	}

	private void configureTenantSelection(DialogControls controls) {
		controls.tenantBox().valueProperty().addListener((obs, oldValue, newValue) -> {
			updateTenantActionState(newValue != null, controls.loginButton(), controls.editButton(), controls.deleteButton());
			controls.errorLabel().setText("");
			controls.passwordField().clear();
		});
	}

	private void configureLoginAction(DialogState state) {
		state.controls.loginButton().setOnAction(event -> handleLogin(state));
	}

	private void handleLogin(DialogState state) {
		TenantProfile selectedTenant = state.controls.tenantBox().getValue();
		char[] password = state.controls.passwordField().getText() != null ? state.controls.passwordField().getText().toCharArray() : new char[0];

		try {
			Optional<TenantProfile> authenticatedTenant = tenantStore.authenticate(selectedTenant != null ? selectedTenant.id() : null, password);
			if (authenticatedTenant.isEmpty()) {
				state.controls.errorLabel().setText(getText("UI_ERROR_TENANT_LOGIN_FAILED"));
				return;
			}

			state.result[0] = new TenantLoginResult(authenticatedTenant.get(), authenticatedTenant.get().id(), selectedLanguageCode);
			state.dialog.close();
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private void configureNewAction(DialogState state, Runnable refreshTenants) {
		state.controls.newButton().setOnAction(event -> {
			TenantProfile newTenant = state.tenantEditDialog.showCreateDialog();
			if (newTenant != null) {
				refreshTenants.run();
				state.controls.tenantBox().getSelectionModel().select(newTenant);
				state.controls.passwordField().requestFocus();
			}
		});
	}

	private void configureEditAction(DialogState state, Runnable refreshTenants) {
		state.controls.editButton().setOnAction(event -> {
			TenantProfile selectedTenant = state.controls.tenantBox().getValue();
			if (selectedTenant == null) {
				return;
			}

			TenantProfile updatedTenant = state.tenantEditDialog.showEditDialog(selectedTenant);
			if (updatedTenant != null) {
				refreshTenants.run();
				state.controls.tenantBox().getSelectionModel().select(updatedTenant);
			}
		});
	}

	private void configureDeleteAction(DialogState state, Runnable refreshTenants) {
		state.controls.deleteButton().setOnAction(event -> {
			TenantProfile selectedTenant = state.controls.tenantBox().getValue();
			if (selectedTenant == null || !confirmDelete(state.dialog, selectedTenant)) {
				return;
			}

			try {
				deleteDirectory(tenantStore.getTenantDirectory(selectedTenant.id()));
				tenantStore.deleteTenant(selectedTenant.id());
				refreshTenants.run();
			} catch (IllegalStateException ex) {
				showDeleteError(state.dialog, ex.getMessage());
			}
		});
	}

	private DialogState createDialogState() {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_MENU_FILE_OPEN");
		dialog.setTitle(getText("UI_DIALOG_TENANT_SELECTION_TITLE"));
		DialogControls controls = createDialogControls();
		TenantEditDialog tenantEditDialog = new TenantEditDialog(dialog, tenantStore);
		return new DialogState(dialog, controls, tenantEditDialog);
	}

	private void initializeDialogState(DialogState state, String lastSelectedTenantId) {
		Runnable refreshTenants = () -> refreshTenants(state.controls, lastSelectedTenantId);
		Runnable refreshTexts = () -> refreshTexts(state.dialog, state.controls);

		configureLanguageSelector(state.controls.languageBox(), state.controls.errorLabel(), refreshTexts);
		refreshTexts.run();
		state.controls.languageBox().getSelectionModel().select(selectLanguageOption(state.controls.languageBox(), selectedLanguageCode));
		refreshTenants.run();

		configureTenantSelection(state.controls);
		configureLoginAction(state);
		configureNewAction(state, refreshTenants);
		configureEditAction(state, refreshTenants);
		configureDeleteAction(state, refreshTenants);
		state.controls.cancelButton().setOnAction(event -> state.dialog.close());
	}

	private void showDialog(DialogState state) {
		var buttonBar = DialogWindowSupport.createButtonBar(state.controls.loginButton(), state.controls.newButton(), state.controls.editButton(),
				state.controls.deleteButton(), state.controls.cancelButton());
		buttonBar.setAlignment(Pos.CENTER_RIGHT);
		state.dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(state.controls.grid(), buttonBar), 520, 240));
		state.dialog.showAndWait();
	}

	private DialogControls createDialogControls() {
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

		return new DialogControls(grid, languageBox, tenantBox, passwordField, hintLabel, errorLabel, languageLabel, tenantLabel, passwordLabel, loginButton,
				newButton, editButton, deleteButton, cancelButton);
	}

	private boolean confirmDelete(Stage dialog, TenantProfile selectedTenant) {
		Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, getText("UI_QUESTION_TENANT_DELETE", selectedTenant.username()), ButtonType.OK,
				ButtonType.CANCEL);
		confirmation.initOwner(parentWindow != null ? parentWindow : dialog);
		confirmation.setHeaderText(null);
		confirmation.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	private void updateTenantActionState(boolean hasSelection, Button loginButton, Button editButton, Button deleteButton) {
		loginButton.setDisable(!hasSelection);
		editButton.setDisable(!hasSelection);
		deleteButton.setDisable(!hasSelection);
	}

	private static final class TenantListCell extends ListCell<TenantProfile> {

		@Override
		protected void updateItem(TenantProfile item, boolean empty) {
			super.updateItem(item, empty);
			setText(empty || item == null ? null : item.username());
		}
	}

	private final class DeleteDirectoryVisitor extends SimpleFileVisitor<Path> {

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.deleteIfExists(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			if (exc != null) {
				throw exc;
			}
			Files.deleteIfExists(dir);
			return FileVisitResult.CONTINUE;
		}
	}
}
