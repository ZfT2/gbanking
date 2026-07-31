package de.zft2.gbanking.gui.dialog.tenant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.demo.DemoTenantService;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.tenant.TenantProfile;
import de.zft2.gbanking.tenant.TenantSession;
import de.zft2.gbanking.tenant.TenantStore;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class TenantSelectionDialog implements BaseMessages {

	private static final Logger log = LogManager.getLogger(TenantSelectionDialog.class);

	public record TenantLoginResult(TenantSession session, String lastSelectedTenantId, String languageCode, boolean demoInitializationRequired) {

		public TenantProfile tenant() {
			return session.profile();
		}
	}

	private record DialogControls(GridPane grid, ComboBox<LanguageOption> languageBox, ComboBox<TenantProfile> tenantBox, PasswordField passwordField,
			Label hintLabel, Label errorLabel, Label languageLabel, Label tenantLabel, Label passwordLabel, Button loginButton, Button newButton, Button editButton,
			Button deleteButton, Button cancelButton, Button demoButton) {
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
	private final DemoTenantService demoTenantService;
	private String selectedLanguageCode = Messages.toLanguageCode(Messages.getLocale());

	public TenantSelectionDialog(Window parentWindow, TenantStore tenantStore) {
		this.parentWindow = parentWindow;
		this.tenantStore = tenantStore;
		demoTenantService = new DemoTenantService(tenantStore);
	}

	public Optional<TenantLoginResult> showAndWait(String lastSelectedTenantId, String initialLanguageCode) {
		applyLanguage(initialLanguageCode);
		DialogState state = createDialogState();
		initializeDialogState(state, lastSelectedTenantId);
		showDialog(state);
		log.info("Tenant selection dialog closed. loginSelected={}", state.result[0] != null);
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

	private void showDeleteError(Stage dialog, String message) {
		DialogWindowSupport.showAlert(parentWindow != null ? parentWindow : dialog, javafx.scene.control.Alert.AlertType.ERROR, message);
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
		controls.demoButton().setText(getText("UI_BUTTON_DEMO"));
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
			Optional<TenantSession> authenticatedSession = tenantStore.authenticateSession(selectedTenant != null ? selectedTenant.id() : null, password);
			if (authenticatedSession.isEmpty()) {
				log.info("Tenant login rejected. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(selectedTenant != null ? selectedTenant.id() : null));
				state.controls.errorLabel().setText(getText("UI_ERROR_TENANT_LOGIN_FAILED"));
				return;
			}

			TenantSession session = authenticatedSession.get();
			log.info("Tenant login accepted. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(session.profile().id()));
			state.result[0] = new TenantLoginResult(session, session.profile().id(), selectedLanguageCode, false);
			state.dialog.close();
		} catch (IllegalStateException e) {
			log.error("Could not unlock tenant encryption key", e);
			state.controls.errorLabel().setText(getText("UI_ERROR_TENANT_ENCRYPTION_KEY"));
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private void configureNewAction(DialogState state, Runnable refreshTenants) {
		state.controls.newButton().setOnAction(event -> {
			TenantProfile newTenant = state.tenantEditDialog.showCreateDialog();
			if (newTenant != null) {
				log.info("Tenant created from selection dialog. tenantId={}", SensitiveDataMasker.maskIdentifier(newTenant.id()));
				refreshTenants.run();
				state.controls.tenantBox().getSelectionModel().select(newTenant);
				state.controls.passwordField().requestFocus();
			}
		});
	}

	private void configureDemoAction(DialogState state, Runnable refreshTenants) {
		state.controls.demoButton().setOnAction(event -> {
			boolean resetExistingDemo = demoTenantService.demoTenantExists();
			if (resetExistingDemo && !confirmDemoReset(state.dialog)) {
				return;
			}

			try {
				TenantSession session = demoTenantService.createFreshDemoSession();
				state.result[0] = new TenantLoginResult(session, session.profile().id(), selectedLanguageCode, true);
				state.dialog.close();
			} catch (IllegalArgumentException | IllegalStateException exception) {
				log.error("Could not prepare demo tenant", exception);
				refreshTenants.run();
				state.controls.errorLabel().setText(getText(resetExistingDemo ? "UI_ERROR_DEMO_RESET_FAILED" : "UI_ERROR_DEMO_CREATE_FAILED"));
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
				log.info("Tenant edited from selection dialog. tenantId={}", SensitiveDataMasker.maskIdentifier(updatedTenant.id()));
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

			Optional<char[]> deletePassword = requestDeletePassword(state.dialog, selectedTenant);
			if (deletePassword.isEmpty()) {
				return;
			}

			char[] password = deletePassword.get();
			try {
				tenantStore.deleteTenantAndData(selectedTenant.id(), password);
				log.info("Tenant deleted from selection dialog. tenantId={}", SensitiveDataMasker.maskIdentifier(selectedTenant.id()));
				refreshTenants.run();
			} catch (IllegalArgumentException | IllegalStateException ex) {
				showDeleteError(state.dialog, ex.getMessage());
			} finally {
				Arrays.fill(password, '\0');
			}
		});
	}

	private Optional<char[]> requestDeletePassword(Stage owner, TenantProfile selectedTenant) {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow != null ? parentWindow : owner, "UI_DIALOG_TENANT_DELETE_PASSWORD_TITLE");
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);

		Label infoLabel = new Label(getText("UI_INFO_TENANT_DELETE_PASSWORD", selectedTenant.username()));
		infoLabel.setWrapText(true);
		PasswordField passwordField = new PasswordField();
		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: #b00020;");
		errorLabel.setWrapText(true);

		grid.add(infoLabel, 0, 0, 2, 1);
		grid.add(new Label(getText("UI_LABEL_TENANT_PASSWORD")), 0, 1);
		grid.add(passwordField, 1, 1);
		grid.add(errorLabel, 0, 2, 2, 1);
		GridPane.setHgrow(passwordField, Priority.ALWAYS);

		Button deleteButton = new Button(getText("UI_BUTTON_DELETE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		deleteButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);

		char[][] result = new char[1][];
		deleteButton.setOnAction(event -> handleDeletePasswordConfirmation(dialog, selectedTenant, passwordField, errorLabel, result));
		cancelButton.setOnAction(event -> dialog.close());

		var buttonBar = DialogWindowSupport.createButtonBar(deleteButton, cancelButton);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);
		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(grid, buttonBar), 460, 210));
		dialog.showAndWait();
		return Optional.ofNullable(result[0]);
	}

	private void handleDeletePasswordConfirmation(Stage dialog, TenantProfile selectedTenant, PasswordField passwordField, Label errorLabel, char[][] result) {
		char[] password = passwordField.getText() != null ? passwordField.getText().toCharArray() : new char[0];
		if (tenantStore.authenticate(selectedTenant.id(), password).isEmpty()) {
			Arrays.fill(password, '\0');
			passwordField.clear();
			errorLabel.setText(getText("UI_ERROR_TENANT_DELETE_PASSWORD_WRONG"));
			return;
		}

		result[0] = password;
		dialog.close();
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
		configureDemoAction(state, refreshTenants);
		configureNewAction(state, refreshTenants);
		configureEditAction(state, refreshTenants);
		configureDeleteAction(state, refreshTenants);
		state.controls.cancelButton().setOnAction(event -> state.dialog.close());
	}

	private void showDialog(DialogState state) {
		VBox messageBox = new VBox(4, state.controls.hintLabel(), state.controls.errorLabel());
		messageBox.visibleProperty().bind(state.controls.hintLabel().visibleProperty().or(state.controls.errorLabel().visibleProperty()));
		messageBox.managedProperty().bind(messageBox.visibleProperty());

		var demoButtonBar = DialogWindowSupport.createButtonBar(state.controls.demoButton());
		var buttonBar = DialogWindowSupport.createButtonBar(state.controls.loginButton(), state.controls.newButton(), state.controls.editButton(),
				state.controls.deleteButton(), state.controls.cancelButton());
		buttonBar.setAlignment(Pos.CENTER_RIGHT);
		VBox buttonArea = new VBox(12, demoButtonBar, buttonBar);
		state.dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(state.controls.grid(), messageBox, buttonArea), 520, 285));
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
		hintLabel.visibleProperty().bind(hintLabel.textProperty().isNotEmpty());
		hintLabel.managedProperty().bind(hintLabel.visibleProperty());
		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: #b00020;");
		errorLabel.setWrapText(true);
		errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
		errorLabel.managedProperty().bind(errorLabel.visibleProperty());
		Label languageLabel = new Label();
		Label tenantLabel = new Label();
		Label passwordLabel = new Label();
		Button demoButton = new Button();
		demoButton.setStyle("-fx-background-color: #f4e6a2; -fx-border-color: #d0bb63; -fx-font-size: 10px; -fx-padding: 3px 7px;");

		grid.add(languageLabel, 0, 0);
		grid.add(languageBox, 1, 0);
		grid.add(tenantLabel, 0, 1);
		grid.add(tenantBox, 1, 1);
		grid.add(passwordLabel, 0, 2);
		grid.add(passwordField, 1, 2);
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
				newButton, editButton, deleteButton, cancelButton, demoButton);
	}

	private boolean confirmDelete(Stage dialog, TenantProfile selectedTenant) {
		return DialogWindowSupport.showConfirmation(parentWindow != null ? parentWindow : dialog, getText("UI_QUESTION_TENANT_DELETE",
				selectedTenant.username()), ButtonType.OK, ButtonType.CANCEL);
	}

	private boolean confirmDemoReset(Stage dialog) {
		return DialogWindowSupport.showConfirmation(parentWindow != null ? parentWindow : dialog, getText("UI_QUESTION_DEMO_RESET"), ButtonType.OK,
				ButtonType.CANCEL);
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

}
