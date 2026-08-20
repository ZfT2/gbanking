package de.zft2.gbanking.gui.panel.bankaccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.enablebanking.EnablebankingAspsp;
import de.zft2.gbanking.enablebanking.EnablebankingAuthMethod;
import de.zft2.gbanking.enablebanking.EnablebankingException;
import de.zft2.gbanking.enablebanking.EnablebankingSetupService;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

public class EnablebankingSetupDialog implements BaseMessages {

	private static final double DIALOG_WIDTH = 700;
	private static final double DIALOG_HEIGHT = 520;

	private final Stage dialog;
	private final Runnable onSaved;
	private final EnablebankingSetupService setupService;
	private Psd2ClientConfiguration loadedConfiguration;
	private BankAccess authorizedAccess;
	private boolean saved;

	public EnablebankingSetupDialog(Window owner, Runnable onSaved) {
		dialog = DialogWindowSupport.createModalStage(owner, "UI_ENABLEBANKING_SETUP_TITLE");
		this.onSaved = onSaved;
		this.setupService = ServiceRegistry.getService(EnablebankingSetupService.class);
	}

	public void show() {
		dialog.setScene(createConfigurationScene());
		dialog.setOnCloseRequest(event -> cancelAuthorization());
		dialog.showAndWait();
	}

	private Scene createConfigurationScene() {
		Psd2ClientConfiguration existing = setupService.getPersonalConfiguration();
		TextField applicationId = new TextField(existing.getApplicationId());
		TextField privateKeyFile = new TextField();
		privateKeyFile.setEditable(false);
		TextField callbackUrl = new TextField(existing.getCallbackUrl());
		callbackUrl.setEditable(false);

		Button chooseKey = new Button(getText("UI_BUTTON_CHOOSE_FILE"));
		chooseKey.setOnAction(event -> choosePrivateKey(privateKeyFile));
		HBox keyRow = new HBox(8, privateKeyFile, chooseKey);
		HBox.setHgrow(privateKeyFile, Priority.ALWAYS);

		ComboBox<String> country = new ComboBox<>();
		TextField institutionSearch = new TextField();
		institutionSearch.setPromptText(getText("UI_ENABLEBANKING_SEARCH_PROMPT"));
		ComboBox<EnablebankingAspsp> institution = new ComboBox<>();
		ComboBox<String> psuType = new ComboBox<>();
		ComboBox<EnablebankingAuthMethod> authMethod = new ComboBox<>();
		List<EnablebankingAspsp> institutions = new ArrayList<>();

		country.setMaxWidth(Double.MAX_VALUE);
		institution.setMaxWidth(Double.MAX_VALUE);
		psuType.setMaxWidth(Double.MAX_VALUE);
		authMethod.setMaxWidth(Double.MAX_VALUE);
		country.valueProperty().addListener((observable, oldValue, newValue) ->
				updateInstitutions(institutions, newValue, institutionSearch.getText(), institution));
		institutionSearch.textProperty().addListener((observable, oldValue, newValue) ->
				updateInstitutions(institutions, country.getValue(), newValue, institution));
		institution.valueProperty().addListener((observable, oldValue, newValue) -> updatePsuTypes(newValue, psuType));
		psuType.valueProperty().addListener((observable, oldValue, newValue) -> updateAuthMethods(institution.getValue(), newValue, authMethod));

		GridPane grid = createGrid();
		addRow(grid, 0, "UI_ENABLEBANKING_APPLICATION_ID", applicationId);
		addRow(grid, 1, "UI_ENABLEBANKING_PRIVATE_KEY", keyRow);
		addRow(grid, 2, "UI_ENABLEBANKING_CALLBACK_URL", callbackUrl);
		addRow(grid, 3, "UI_ENABLEBANKING_COUNTRY", country);
		addRow(grid, 4, "UI_LABEL_SEARCH", institutionSearch);
		addRow(grid, 5, "UI_ENABLEBANKING_INSTITUTION", institution);
		addRow(grid, 6, "UI_ENABLEBANKING_PSU_TYPE", psuType);
		addRow(grid, 7, "UI_ENABLEBANKING_AUTH_METHOD", authMethod);

		Label linkedAccountsHint = new Label(getText("UI_ENABLEBANKING_LINKED_ACCOUNTS_HINT"));
		linkedAccountsHint.setWrapText(true);
		linkedAccountsHint.setStyle("-fx-font-weight: bold;");
		Label callbackHint = new Label(getText("UI_ENABLEBANKING_CALLBACK_HINT"));
		callbackHint.setWrapText(true);
		Button loadInstitutions = new Button(getText("UI_ENABLEBANKING_LOAD_INSTITUTIONS"));
		Button authorize = new Button(getText("UI_ENABLEBANKING_AUTHORIZE"));
		authorize.setDisable(true);
		Button cancel = new Button(getText("UI_BUTTON_CANCEL"));
		institution.valueProperty().addListener((observable, oldValue, newValue) -> authorize.setDisable(newValue == null));

		loadInstitutions.setOnAction(event -> loadInstitutions(applicationId, privateKeyFile, callbackUrl,
				institutions, country, institution, authorize, loadInstitutions));
		authorize.setOnAction(event -> authorize(existingConfiguration(), institution.getValue(),
				psuType.getValue(), authMethod.getValue(), authorize, cancel));
		cancel.setOnAction(event -> close());

		VBox root = DialogWindowSupport.createDialogRoot(grid, linkedAccountsHint, callbackHint,
				DialogWindowSupport.createButtonBar(loadInstitutions, authorize, cancel));
		return new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	private Psd2ClientConfiguration existingConfiguration() {
		if (loadedConfiguration == null) {
			throw new EnablebankingException("Bitte zunächst die Kreditinstitute laden.");
		}
		return loadedConfiguration;
	}

	private void loadInstitutions(TextField applicationId, TextField privateKeyFile, TextField callbackUrl,
			List<EnablebankingAspsp> institutions, ComboBox<String> country,
			ComboBox<EnablebankingAspsp> institution, Button authorize, Button loadButton) {
		startTask("gbanking-enablebanking-load-institutions", () -> {
			String pem = privateKeyFile.getText().isBlank() ? null : Files.readString(Path.of(privateKeyFile.getText()));
			return setupService.configure(applicationId.getText(), pem, callbackUrl.getText());
		}, result -> {
			loadedConfiguration = setupService.getPersonalConfiguration();
			institutions.clear();
			institutions.addAll(result);
			List<String> countries = institutions.stream().map(EnablebankingAspsp::country).distinct().sorted().toList();
			country.setItems(FXCollections.observableArrayList(countries));
			country.setValue(countries.contains("DE") ? "DE" : countries.stream().findFirst().orElse(null));
			authorize.setDisable(institution.getValue() == null);
		}, () -> authorize.setDisable(true), loadButton, authorize);
	}

	private void authorize(Psd2ClientConfiguration configuration, EnablebankingAspsp institution,
			String psuType, EnablebankingAuthMethod authMethod, Button authorize, Button cancel) {
		if (institution == null || psuType == null) {
			DialogWindowSupport.showAlert(dialog, AlertType.WARNING, getText("UI_ENABLEBANKING_REQUIRED_FIELDS"));
			return;
		}
		startTask("gbanking-enablebanking-authorization",
				() -> setupService.authorize(configuration, institution, psuType, authMethod), access -> {
			authorizedAccess = access;
			if (authorizedAccess.getAccounts().isEmpty()) {
				DialogWindowSupport.showAlert(dialog, AlertType.INFORMATION,
						getText("UI_ENABLEBANKING_NO_SELECTABLE_ACCOUNTS"));
				cancelAuthorization();
				return;
			}
			dialog.setScene(createAccountSelectionScene(authorizedAccess));
		}, () -> {
		}, authorize, cancel);
	}

	private Scene createAccountSelectionScene(BankAccess bankAccess) {
		VBox accountList = new VBox(8);
		List<CheckBox> choices = new ArrayList<>();
		for (BankAccount account : bankAccess.getAccounts()) {
			CheckBox choice = new CheckBox(account.getAccountName() + accountIdentifier(account));
			choice.setSelected(true);
			choice.setUserData(account);
			choices.add(choice);
			accountList.getChildren().add(choice);
		}
		ScrollPane scrollPane = new ScrollPane(accountList);
		scrollPane.setFitToWidth(true);
		VBox.setVgrow(scrollPane, Priority.ALWAYS);
		Button save = new Button(getText("UI_BUTTON_SAVE"));
		Button cancel = new Button(getText("UI_BUTTON_CANCEL"));
		save.setOnAction(event -> save(bankAccess, choices, save, cancel));
		cancel.setOnAction(event -> close());
		VBox root = DialogWindowSupport.createDialogRoot(new Label(getText("UI_ENABLEBANKING_SELECT_ACCOUNTS")), scrollPane,
				DialogWindowSupport.createButtonBar(save, cancel));
		return new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	private void save(BankAccess bankAccess, List<CheckBox> choices, Button save, Button cancel) {
		List<BankAccount> selected = choices.stream().filter(CheckBox::isSelected)
				.map(choice -> (BankAccount) choice.getUserData()).toList();
		if (selected.isEmpty()) {
			DialogWindowSupport.showAlert(dialog, AlertType.WARNING, getText("UI_ENABLEBANKING_SELECT_AT_LEAST_ONE"));
			return;
		}
		startTask("gbanking-enablebanking-save-access", () -> setupService.save(bankAccess, selected), success -> {
			if (Boolean.TRUE.equals(success)) {
				saved = true;
				onSaved.run();
				dialog.close();
			} else {
				DialogWindowSupport.showAlert(dialog, AlertType.WARNING, getText("UI_ENABLEBANKING_SAVE_FAILED"));
			}
		}, () -> {
		}, save, cancel);
	}

	private void cancelAuthorization() {
		if (saved || authorizedAccess == null) {
			return;
		}
		BankAccess accessToCancel = authorizedAccess;
		authorizedAccess = null;
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() {
				setupService.cancelAuthorization(accessToCancel);
				return null;
			}
		};
		BackgroundActionCoordinator.getInstance().start(task, "gbanking-enablebanking-cancel-authorization");
	}

	private void close() {
		cancelAuthorization();
		dialog.close();
	}

	private void updateInstitutions(List<EnablebankingAspsp> institutions, String country, String searchText,
			ComboBox<EnablebankingAspsp> target) {
		String search = searchText == null ? "" : searchText.strip().toLowerCase(Locale.ROOT);
		List<EnablebankingAspsp> filtered = institutions.stream()
				.filter(aspsp -> aspsp.country().equals(country)
						&& aspsp.name().toLowerCase(Locale.ROOT).contains(search))
				.toList();
		target.setItems(FXCollections.observableArrayList(filtered));
		target.setValue(filtered.stream().findFirst().orElse(null));
	}

	private void updatePsuTypes(EnablebankingAspsp institution, ComboBox<String> target) {
		List<String> types = institution != null ? institution.psuTypes() : List.of();
		target.setItems(FXCollections.observableArrayList(types));
		target.setValue(types.stream().findFirst().orElse(null));
	}

	private void updateAuthMethods(EnablebankingAspsp institution, String psuType,
			ComboBox<EnablebankingAuthMethod> target) {
		List<EnablebankingAuthMethod> methods = institution == null ? List.of() : institution.authMethods().stream()
				.filter(method -> method.psuType() == null || method.psuType().equals(psuType)).toList();
		target.setItems(FXCollections.observableArrayList(methods));
		target.setValue(methods.size() == 1 ? methods.get(0) : null);
	}

	private void choosePrivateKey(TextField privateKeyFile) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(getText("UI_ENABLEBANKING_PRIVATE_KEY"));
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM", "*.pem", "*.key"));
		java.io.File selected = chooser.showOpenDialog(dialog);
		if (selected != null) {
			privateKeyFile.setText(selected.getAbsolutePath());
		}
	}

	private GridPane createGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(4));
		return grid;
	}

	private void addRow(GridPane grid, int row, String labelKey, javafx.scene.Node field) {
		grid.add(new Label(getText(labelKey)), 0, row);
		grid.add(field, 1, row);
		GridPane.setHgrow(field, Priority.ALWAYS);
	}

	private String accountIdentifier(BankAccount account) {
		String identifier = account.getIban() != null ? account.getIban() : account.getNumber();
		return identifier != null ? " (" + identifier + ")" : "";
	}

	private void showFailure(Window window, Throwable throwable) {
		String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage()
				: getText("UI_ENABLEBANKING_GENERAL_ERROR");
		DialogWindowSupport.showAlert(window, AlertType.WARNING, message);
	}

	private <T> void startTask(String name, Callable<T> operation, Consumer<T> onSuccess, Runnable onFailure,
			Button... buttons) {
		setDisabled(true, buttons);
		Task<T> task = new Task<>() {
			@Override
			protected T call() throws Exception {
				return operation.call();
			}
		};
		task.setOnSucceeded(event -> {
			setDisabled(false, buttons);
			onSuccess.accept(task.getValue());
		});
		task.setOnFailed(event -> {
			setDisabled(false, buttons);
			onFailure.run();
			showFailure(dialog, task.getException());
		});
		BackgroundActionCoordinator.getInstance().start(task, name);
	}

	private void setDisabled(boolean disabled, Button... buttons) {
		for (Button button : buttons) {
			button.setDisable(disabled);
		}
	}
}
