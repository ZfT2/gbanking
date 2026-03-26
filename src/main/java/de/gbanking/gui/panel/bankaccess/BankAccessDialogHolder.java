package de.gbanking.gui.panel.bankaccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.enu.TanProcedure;
import de.gbanking.gui.enu.ButtonContext;
import de.gbanking.gui.panel.BasePanelHolder;
import de.gbanking.gui.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BankAccessDialogHolder extends BasePanelHolder {

	private static final double STEP1_WIDTH = 420;
	private static final double STEP1_HEIGHT = 240;
	private static final double STEP2_WIDTH = 900;
	private static final double STEP2_HEIGHT = 600;
	private static final double DIALOG_SPACING = 10;

	private final ButtonContext buttonContext;
	private final BankAccessOverviewPanel overviewPanel;
	private BankAccess currentBankAccess;

	public BankAccessDialogHolder(ButtonContext buttonContext, BankAccessOverviewPanel overviewPanel) {
		this.buttonContext = buttonContext;
		this.overviewPanel = overviewPanel;
		this.currentBankAccess = overviewPanel.getCurrentBankAccess();
	}

	public void showDialog() {
		switch (buttonContext) {
		case BUTTON_NEW, BUTTON_EDIT -> showWizardDialog();
		case BUTTON_DELETE -> showDeleteConfirmationDialog();
		default -> throw new IllegalStateException("Unsupported buttonContext: " + buttonContext);
		}
	}

	public boolean showManualEditConfirmationDialog() {
		Optional<ButtonType> result = createWarningConfirmationAlert(getText("UI_BUTTON_BANK_ACCESS_EDIT"), getText("UI_WARNING_BANK_ACCESS_EDIT_MANUAL"),
				new Label(getText("UI_QUESTION_BANK_ACCESS_EDIT_MANUAL")), ButtonType.OK, ButtonType.CANCEL).showAndWait();

		return result.filter(ButtonType.OK::equals).isPresent();
	}

	public void showRequiredFieldsWarningDialog() {
		showWarningMessageDialog("UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_TITLE", "UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_HEADER",
				"UI_WARNING_BANK_ACCESS_REQUIRED_FIELDS_TEXT");
	}

	public void showInvalidPortWarningDialog() {
		showWarningMessageDialog("UI_WARNING_BANK_ACCESS_INVALID_PORT_TITLE", "UI_WARNING_BANK_ACCESS_INVALID_PORT_HEADER",
				"UI_WARNING_BANK_ACCESS_INVALID_PORT_TEXT");
	}

	private void showWizardDialog() {
		Stage dialog = createDialogStage(buttonContext.getHeadline());
		dialog.setScene(new Scene(createStep1(dialog), STEP1_WIDTH, STEP1_HEIGHT));
		dialog.showAndWait();
	}

	private Stage createDialogStage(String title) {
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle(title);
		return dialog;
	}

	private VBox createStep1(Stage dialog) {
		TextField blzText = new TextField("30530500");
		TextField userNameText = new TextField("");
		PasswordField pinText = new PasswordField();

		if (buttonContext == ButtonContext.BUTTON_EDIT && currentBankAccess != null) {
			blzText.setText(currentBankAccess.getBlz());
			userNameText.setText(currentBankAccess.getUserId());
		}

		GridPane grid = createDefaultGrid();
		grid.add(new Label(getText("UI_LABEL_BANK")), 0, 0);
		grid.add(blzText, 1, 0);
		grid.add(new Label(getText("UI_LABEL_USER")), 0, 1);
		grid.add(userNameText, 1, 1);
		grid.add(new Label(getText("UI_LABEL_PIN")), 0, 2);
		grid.add(pinText, 1, 2);

		Button okButton = new Button(getText("UI_BUTTON_OK"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));

		okButton.setOnAction(e -> {
			BankAccess bankAccess = new BankAccess();
			bankAccess.setBlz(blzText.getText());
			bankAccess.setUserId(userNameText.getText());
			bankAccess.setPin(pinText.getText().toCharArray());
			bankAccess.setTanProcedure(TanProcedure.APP_TAN);

			okButton.setDisable(true);
			cancelButton.setDisable(true);

			Task<Boolean> loadBankAccessTask = new Task<>() {
				@Override
				protected Boolean call() {
					return bean.addNewBankAccess(bankAccess);
				}
			};
			loadBankAccessTask.setOnSucceeded(event -> {
				okButton.setDisable(false);
				cancelButton.setDisable(false);
				if (Boolean.TRUE.equals(loadBankAccessTask.getValue())) {
					dialog.setScene(new Scene(createStep2(dialog, bankAccess), STEP2_WIDTH, STEP2_HEIGHT));
				}
			});
			loadBankAccessTask.setOnFailed(event -> {
				okButton.setDisable(false);
				cancelButton.setDisable(false);
			});

			Thread thread = new Thread(loadBankAccessTask, "gbanking-hbci-add-bank-access");
			thread.setDaemon(true);
			thread.start();
		});

		cancelButton.setOnAction(e -> dialog.close());

		return createDialogRoot(getText("UI_BANK_ACCESS_DIALOG_DATA"), grid, createButtonBar(okButton, cancelButton));
	}

	private VBox createStep2(Stage dialog, BankAccess bankAccess) {
		Label accountsLabel = new Label(getText("UI_BANK_ACCESS_DIALOG_ACCOUNTS_LABEL"));

		ObservableList<BankAccount> accountItems = FXCollections.observableArrayList(getAccounts(bankAccess));
		accountItems.forEach(account -> account.setSelected(true));

		TableView<BankAccount> accountTable = new TableView<>(accountItems);
		accountTable.setEditable(true);
		accountTable.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		TableColumn<BankAccount, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), BankAccount::isSelected,
				BankAccount::setSelected);
		TableColumn<BankAccount, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"), BankAccount::getAccountName, 180, 220);
		TableColumn<BankAccount, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"), BankAccount::getIban, 220, 260);
		TableColumn<BankAccount, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), BankAccount::getBankName, 140, 180);
		TableColumn<BankAccount, String> currencyCol = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_CURRENCY"),
				account -> account.getCurrency() != null ? account.getCurrency() : "", 80);

		accountTable.getColumns().setAll(List.of(selectedCol, nameCol, ibanCol, bankCol, currencyCol));

		Button okButton = new Button(getText("UI_BUTTON_OK"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));

		okButton.setOnAction(e -> {
			List<BankAccount> selectedAccounts = accountItems.stream().filter(BankAccount::isSelected).toList();
			bankAccess.setAccounts(new ArrayList<>(selectedAccounts));

			if (bean.saveBankAccessAccountsToDB(bankAccess)) {
				overviewPanel.getBankAccessListPanel().refreshModelBankAccess();
				dialog.close();
			} else {
				accountsLabel.setText(getText("ERROR_BANK_ACCESS_SAVE"));
			}
		});

		cancelButton.setOnAction(e -> dialog.close());

		VBox root = createDialogRoot(getText("UI_BANK_ACCESS_DIALOG_ACCOUNTS_TITLE"), accountsLabel, accountTable, createButtonBar(okButton, cancelButton));
		VBox.setVgrow(accountTable, Priority.ALWAYS);
		return root;
	}

	private VBox createDialogRoot(String titleKey, Node... content) {
		VBox root = new VBox(DIALOG_SPACING);
		root.setPadding(new Insets(10));
		root.getChildren().add(new Label(titleKey));
		root.getChildren().addAll(content);
		return root;
	}

	private HBox createButtonBar(Button... buttons) {
		return new HBox(DIALOG_SPACING, buttons);
	}

	private void showDeleteConfirmationDialog() {
		Alert alert = createWarningConfirmationAlert(buttonContext.getHeadline(), getText("UI_WARNING_BANK_ACCESS_DELETE"), createDeleteDialogContent(),
				ButtonType.OK, ButtonType.CANCEL);

		alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> deleteCurrentBankAccess());
	}

	private Node createDeleteDialogContent() {
		VBox content = new VBox(DIALOG_SPACING, createBankAccessInfoGrid(), new Label(getText("UI_QUESTION_BANK_ACCESS_DELETE")));
		content.setPadding(new Insets(5, 0, 0, 0));
		return content;
	}

	private GridPane createBankAccessInfoGrid() {
		Label blzValue = new Label(currentBankAccess != null ? nullToEmpty(currentBankAccess.getBlz()) : "");
		Label userValue = new Label(currentBankAccess != null ? nullToEmpty(currentBankAccess.getUserId()) : "");

		GridPane grid = createDefaultGrid();
		grid.setPadding(Insets.EMPTY);
		grid.add(new Label(getText("UI_LABEL_BANK")), 0, 0);
		grid.add(blzValue, 1, 0);
		grid.add(new Label(getText("UI_LABEL_USER")), 0, 1);
		grid.add(userValue, 1, 1);
		return grid;
	}

	private GridPane createDefaultGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(6);
		grid.setPadding(new Insets(10));
		return grid;
	}

	private Alert createWarningConfirmationAlert(String title, String header, Node content, ButtonType... buttonTypes) {
		Alert alert = new Alert(AlertType.WARNING);
		alert.setTitle(title);
		alert.setHeaderText(header);
		alert.getButtonTypes().setAll(buttonTypes);
		alert.getDialogPane().setContent(content);
		return alert;
	}

	private void showWarningMessageDialog(String titleKey, String headerKey, String contentKey) {
		Alert alert = new Alert(AlertType.WARNING);
		alert.setTitle(getText(titleKey));
		alert.setHeaderText(getText(headerKey));
		alert.setContentText(getText(contentKey));
		alert.showAndWait();
	}

	private void deleteCurrentBankAccess() {
		if (currentBankAccess == null) {
			return;
		}

		if (bean.deleteBankAccessFromDB(currentBankAccess)) {
			currentBankAccess = null;
			overviewPanel.setCurrentBankAccess(null);
			overviewPanel.getBankAccessListPanel().refreshModelBankAccess();
			showWarningMessageDialog("UI_BUTTON_BANK_ACCESS_DELETE", "UI_WARNING_BANK_ACCESS_DELETE", "UI_INFO_BANK_ACCESS_DELETE_SUCCESS");
		} else {
			showWarningMessageDialog("UI_BUTTON_BANK_ACCESS_DELETE", "UI_WARNING_BANK_ACCESS_DELETE", "ERROR_BANK_ACCESS_DELETE");
		}
	}

	private List<BankAccount> getAccounts(BankAccess bankAccess) {
		if (bankAccess.getAccounts() == null) {
			return List.of();
		}
		return bankAccess.getAccounts();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}