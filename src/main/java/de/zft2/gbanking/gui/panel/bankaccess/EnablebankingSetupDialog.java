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
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.enablebanking.EnablebankingAspsp;
import de.zft2.gbanking.enablebanking.EnablebankingAuthMethod;
import de.zft2.gbanking.enablebanking.EnablebankingException;
import de.zft2.gbanking.enablebanking.EnablebankingSetupService;
import de.zft2.gbanking.gui.BackgroundActionCoordinator;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

final class EnablebankingSetupDialog implements BaseMessages {

	private static final double DIALOG_WIDTH = 700;
	private static final double DIALOG_HEIGHT = 520;

	private final Stage dialog;
	private final Runnable onSaved;
	private final EnablebankingSetupService setupService;
	private Psd2ClientConfiguration loadedConfiguration;
	private BankAccess authorizedAccess;
	private boolean saved;

	EnablebankingSetupDialog(Window owner, Runnable onSaved) {
		dialog = DialogWindowSupport.createModalStage(owner, "UI_ENABLEBANKING_SETUP_TITLE");
		this.onSaved = onSaved;
		this.setupService = ServiceRegistry.getService(EnablebankingSetupService.class);
	}

	void show() {
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
		ConfigurationFields configurationFields = new ConfigurationFields(applicationId, privateKeyFile, callbackUrl);

		loadInstitutions.setOnAction(event -> loadInstitutions(configurationFields, institutions, country,
				institution, authorize, loadInstitutions));
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

	private void loadInstitutions(ConfigurationFields fields, List<EnablebankingAspsp> institutions, ComboBox<String> country,
			ComboBox<EnablebankingAspsp> institution, Button authorize, Button loadButton) {
		startTask("gbanking-enablebanking-load-institutions", () -> {
			String privateKeyFile = fields.privateKeyFile().getText();
			String pem = privateKeyFile.isBlank() ? null : Files.readString(Path.of(privateKeyFile));
			return setupService.configure(fields.applicationId().getText(), pem, fields.callbackUrl().getText());
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
		ObservableList<BankAccount> accounts = FXCollections.observableArrayList(bankAccess.getAccounts());
		accounts.forEach(account -> account.setSelected(true));
		TableView<BankAccount> accountTable = createAccountTable(accounts);
		Button save = new Button(getText("UI_BUTTON_SAVE"));
		Button cancel = new Button(getText("UI_BUTTON_CANCEL"));
		save.setOnAction(event -> save(bankAccess, accounts, save, cancel));
		cancel.setOnAction(event -> close());
		Label hint = new Label(getText("UI_ENABLEBANKING_SELECT_ACCOUNTS"));
		hint.setWrapText(true);
		VBox root = DialogWindowSupport.createDialogRoot(hint, accountTable,
				DialogWindowSupport.createButtonBar(save, cancel));
		VBox.setVgrow(accountTable, Priority.ALWAYS);
		return new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	/**
	 * Creates the overview of the found accounts. The account type is shown in a dropdown, because
	 * Enablebanking often delivers a wrong or unspecific account type that can be corrected here.
	 */
	static TableView<BankAccount> createAccountTable(ObservableList<BankAccount> accounts) {
		TableView<BankAccount> accountTable = new TableView<>(accounts);
		accountTable.setEditable(true);
		accountTable.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		TableColumn<BankAccount, Boolean> selectedColumn = FxTableUtils.createSelectAllSelectionColumn(
				BaseMessages.getTextStatic("UI_TABLE_SELECT_ALL"), accounts, BankAccount::isSelected,
				BankAccount::setSelected);
		TableColumn<BankAccount, String> nameColumn = TableColumnFactory.createTextColumn(
				BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_NAME"), BankAccount::getAccountName, 180, 220);
		TableColumn<BankAccount, String> ibanColumn = TableColumnFactory.createTextColumn(
				BaseMessages.getTextStatic("UI_TABLE_IBAN"), BankAccount::getIban, 180, 220);
		TableColumn<BankAccount, AccountType> accountTypeColumn = createAccountTypeColumn();

		accountTable.getColumns().setAll(List.of(selectedColumn, nameColumn, ibanColumn, accountTypeColumn));
		GuiLayoutState.configureTable(accountTable, "dialog.enablebankingAccounts");
		return accountTable;
	}

	private static TableColumn<BankAccount, AccountType> createAccountTypeColumn() {
		TableColumn<BankAccount, AccountType> column = TableColumnFactory.createValueColumn(BaseMessages.getTextStatic("UI_TABLE_ACCOUNT_TYPE"),
				account -> account.getAccountType());
		column.setCellFactory(ignored -> new AccountTypeCell());
		FxTableUtils.setPreferredWidth(column, 140, 180);
		return column;
	}

	/**
	 * Cell that shows the account type in an always visible dropdown. The preselection is the account
	 * type that was derived from the data delivered by Enablebanking.
	 */
	private static final class AccountTypeCell extends TableCell<BankAccount, AccountType> {

		private final ComboBox<AccountType> comboBox = new ComboBox<>(
				FXCollections.observableArrayList(AccountType.values()));

		private AccountTypeCell() {
			comboBox.setMaxWidth(Double.MAX_VALUE);
			comboBox.prefWidthProperty().bind(widthProperty().subtract(16));
			comboBox.setOnAction(event -> applyAccountType());
			setAlignment(Pos.CENTER_LEFT);
		}

		@Override
		protected void updateItem(AccountType accountType, boolean empty) {
			super.updateItem(accountType, empty);
			BankAccount account = getAccount();
			if (empty || account == null) {
				setGraphic(null);
				return;
			}
			comboBox.setValue(account.getAccountType());
			setGraphic(comboBox);
		}

		private void applyAccountType() {
			BankAccount account = getAccount();
			if (account == null || comboBox.getValue() == null
					|| comboBox.getValue() == account.getAccountType()) {
				return;
			}
			EnablebankingSetupService.applyAccountType(account, comboBox.getValue());
			// The account name contains the account type, so the row has to be redrawn.
			if (getTableView() != null) {
				getTableView().refresh();
			}
		}

		private BankAccount getAccount() {
			if (getTableRow() != null && getTableRow().getItem() != null) {
				return getTableRow().getItem();
			}
			TableView<BankAccount> table = getTableView();
			if (table == null || getIndex() < 0 || getIndex() >= table.getItems().size()) {
				return null;
			}
			return table.getItems().get(getIndex());
		}
	}

	private void save(BankAccess bankAccess, List<BankAccount> accounts, Button save, Button cancel) {
		List<BankAccount> selected = accounts.stream().filter(BankAccount::isSelected).toList();
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
		Task<Void> task = BackgroundActionCoordinator.newTask(() -> {
			setupService.cancelAuthorization(accessToCancel);
			return null;
		});
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
		GridPane grid = new GridPane(10, 10);
		grid.setPadding(new Insets(4));
		return grid;
	}

	private void addRow(GridPane grid, int row, String labelKey, javafx.scene.Node field) {
		grid.add(new Label(getText(labelKey)), 0, row);
		grid.add(field, 1, row);
		GridPane.setHgrow(field, Priority.ALWAYS);
	}

	private void showFailure(Window window, Throwable throwable) {
		String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage()
				: getText("UI_ENABLEBANKING_GENERAL_ERROR");
		DialogWindowSupport.showAlert(window, AlertType.WARNING, message);
	}

	private <T> void startTask(String name, Callable<T> operation, Consumer<T> onSuccess, Runnable onFailure,
			Button... buttons) {
		setDisabled(true, buttons);
		Task<T> task = BackgroundActionCoordinator.newTask(operation);
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

	private record ConfigurationFields(TextField applicationId, TextField privateKeyFile, TextField callbackUrl) {
	}
}
