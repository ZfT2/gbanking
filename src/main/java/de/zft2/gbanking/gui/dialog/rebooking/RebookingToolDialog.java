package de.zft2.gbanking.gui.dialog.rebooking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.BaseGui;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.rebooking.MissingRebookingCreationSummary;
import de.zft2.gbanking.rebooking.MissingRebookingRouteSummary;
import de.zft2.gbanking.rebooking.RebookingAccountSummary;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.ServiceRegistry;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class RebookingToolDialog implements BaseGui {

	private static final Logger log = LogManager.getLogger(RebookingToolDialog.class);

	AccountTransactionService accountTransactionService = ServiceRegistry.getService(AccountTransactionService.class);

	private Stage stage;
	private GBankingService bean;

	List<BankAccount> selectedAccounts;

	private record RebookingAssignmentRequest(LocalDate dateFrom, LocalDate dateTo, boolean allAccounts) {
	}

	private record MissingRebookingSummaryRow(String sourceAccountName, String targetAccountName, int missingRebookings) {
	}

	public RebookingToolDialog(Stage stage, List<BankAccount> selectedAccounts) {
		this.stage = stage;
		this.selectedAccounts = selectedAccounts;
		bean = ServiceRegistry.getService(GBankingService.class);
	}

	public void assignRebookings() {
		showRebookingAssignmentRequestDialog(getText("UI_DIALOG_REBOOKING_ASSIGN_TITLE"), getText("UI_DIALOG_REBOOKING_ASSIGN_HEADER"))
				.ifPresent(this::startRebookingAssignmentDetection);
	}

	public void createRebookings() {
		showRebookingAssignmentRequestDialog(getText("UI_DIALOG_REBOOKING_CREATE_TITLE"), getText("UI_DIALOG_REBOOKING_ASSIGN_HEADER"))
				.ifPresent(this::startMissingRebookingDetection);
	}

	private Optional<RebookingAssignmentRequest> showRebookingAssignmentRequestDialog(String title, String header) {
		Dialog<RebookingAssignmentRequest> dialog = new Dialog<>();
		dialog.initOwner(stage);
		dialog.setTitle(title);
		dialog.setHeaderText(header);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

		DatePicker dateFromPicker = new DatePicker(LocalDate.now(ZoneId.systemDefault()).minusMonths(1));
		DatePicker dateToPicker = new DatePicker(LocalDate.now(ZoneId.systemDefault()));
		ToggleGroup accountScopeGroup = new ToggleGroup();
		RadioButton allAccountsButton = new RadioButton(getText("UI_DIALOG_REBOOKING_ASSIGN_ALL_ACCOUNTS"));
		allAccountsButton.setToggleGroup(accountScopeGroup);
		allAccountsButton.setSelected(true);
		RadioButton selectedAccountsButton = new RadioButton(getText("UI_DIALOG_REBOOKING_ASSIGN_SELECTED_ACCOUNTS"));
		selectedAccountsButton.setToggleGroup(accountScopeGroup);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(8));
		grid.add(new Label(getText("UI_LABEL_DATE_FROM")), 0, 0);
		grid.add(dateFromPicker, 1, 0);
		grid.add(new Label(getText("UI_LABEL_DATE_TO")), 0, 1);
		grid.add(dateToPicker, 1, 1);
		grid.add(new Label(getText("UI_DIALOG_REBOOKING_ASSIGN_ACCOUNT_SCOPE")), 0, 2);
		VBox accountScopeBox = new VBox(6, allAccountsButton, selectedAccountsButton);
		grid.add(accountScopeBox, 1, 2);
		dialog.getDialogPane().setContent(grid);

		Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(ActionEvent.ACTION, event -> {
			LocalDate dateFrom = dateFromPicker.getValue();
			LocalDate dateTo = dateToPicker.getValue();
			if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
				showWarning(stage, getText("ALERT_REBOOKING_ASSIGN_INVALID_DATE_RANGE"));
				event.consume();
				return;
			}
			if (selectedAccountsButton.isSelected() && selectedAccounts.isEmpty()) {
				showWarning(stage, getText(ALERT_REBOOKING_ASSIGN_NO_SELECTED_ACCOUNTS));
				event.consume();
			}
		});

		dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK
				? new RebookingAssignmentRequest(dateFromPicker.getValue(), dateToPicker.getValue(), allAccountsButton.isSelected())
				: null);
		return dialog.showAndWait();
	}

	private void startRebookingAssignmentDetection(RebookingAssignmentRequest request) {
		List<BankAccount> anchorAccounts = request.allAccounts() ? bean.getAllAccounts() : selectedAccounts;
		if (anchorAccounts.isEmpty()) {
			showWarning(stage, getText(request.allAccounts() ? "ERROR_NO_ACCOUNTS_FOUND" : ALERT_REBOOKING_ASSIGN_NO_SELECTED_ACCOUNTS));
			return;
		}

		Task<RebookingAssignmentSummary> detectionTask = new Task<>() {
			@Override
			protected RebookingAssignmentSummary call() {
				log.info("Starting manual rebooking assignment detection. from={}, to={}, allAccounts={}, anchorAccounts={}", request.dateFrom(),
						request.dateTo(), request.allAccounts(), anchorAccounts.size());
				return accountTransactionService.detectRebookings(request.dateFrom(), request.dateTo(), anchorAccounts);
			}
		};
		detectionTask.setOnSucceeded(event -> handleRebookingAssignmentSummary(detectionTask.getValue()));
		detectionTask.setOnFailed(event -> {
			log.error("Error assigning rebookings", detectionTask.getException());
			showWarning(stage, getText("ALERT_REBOOKING_ASSIGN_ERROR"));
		});
		startBackgroundTask(detectionTask, "gbanking-assign-rebookings-detect");
	}

	private void startMissingRebookingDetection(RebookingAssignmentRequest request) {
		List<BankAccount> anchorAccounts = request.allAccounts() ? bean.getAllAccounts() : selectedAccounts;
		if (anchorAccounts.isEmpty()) {
			showWarning(stage, getText(request.allAccounts() ? "ERROR_NO_ACCOUNTS_FOUND" : ALERT_REBOOKING_ASSIGN_NO_SELECTED_ACCOUNTS));
			return;
		}

		Task<MissingRebookingCreationSummary> detectionTask = new Task<>() {
			@Override
			protected MissingRebookingCreationSummary call() {
				log.info("Starting missing rebooking detection. from={}, to={}, allAccounts={}, anchorAccounts={}", request.dateFrom(), request.dateTo(),
						request.allAccounts(), anchorAccounts.size());
				return accountTransactionService.detectMissingRebookings(request.dateFrom(), request.dateTo(), anchorAccounts);
			}
		};
		detectionTask.setOnSucceeded(event -> handleMissingRebookingSummary(detectionTask.getValue()));
		detectionTask.setOnFailed(event -> {
			log.error("Error detecting missing rebookings", detectionTask.getException());
			showWarning(stage, getText("ALERT_REBOOKING_CREATE_ERROR"));
		});
		startBackgroundTask(detectionTask, "gbanking-create-rebookings-detect");
	}

	private void handleRebookingAssignmentSummary(RebookingAssignmentSummary summary) {
		if (summary == null || summary.isEmpty()) {
			showInfo(stage, getText("UI_DIALOG_REBOOKING_ASSIGN_NO_MATCHES"));
			return;
		}
		if (showRebookingAssignmentSummary(summary)) {
			saveDetectedRebookings(summary);
		}
	}

	private void handleMissingRebookingSummary(MissingRebookingCreationSummary summary) {
		if (summary == null || summary.isEmpty()) {
			showInfo(stage, getText("UI_DIALOG_REBOOKING_CREATE_NO_MATCHES"));
			return;
		}
		if (showMissingRebookingSummary(summary)) {
			createMissingRebookings(summary);
		}
	}

	private boolean showRebookingAssignmentSummary(RebookingAssignmentSummary summary) {
		Label summaryLabel = new Label(getText("UI_DIALOG_REBOOKING_ASSIGN_SUMMARY_TEXT", summary.pairCount()));
		summaryLabel.setWrapText(true);

		TableView<RebookingAccountSummary> summaryTable = new TableView<>(FXCollections.observableArrayList(summary.accountSummaries()));
		TableColumn<RebookingAccountSummary, String> accountColumn = new TableColumn<>(getText("UI_TABLE_ACCOUNT"));
		accountColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().accountName()));
		accountColumn.setPrefWidth(280);
		TableColumn<RebookingAccountSummary, Integer> foundColumn = new TableColumn<>(getText("UI_TABLE_REBOOKING_FOUND"));
		foundColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().foundRebookings()));
		foundColumn.setPrefWidth(150);
		summaryTable.getColumns().addAll(Arrays.asList(accountColumn, foundColumn));
		GuiLayoutState.configureTable(summaryTable, "dialog.rebookingAssignment");
		summaryTable.setPrefWidth(450);
		summaryTable.setPrefHeight(Math.min(280, 48 + summary.accountSummaries().size() * 30));

		VBox content = new VBox(10, summaryLabel, summaryTable);
		content.setPrefWidth(470);
		ButtonType assignButton = new ButtonType(getText("UI_DIALOG_REBOOKING_ASSIGN_CONFIRM"), ButtonBar.ButtonData.OK_DONE);
		return DialogWindowSupport.showConfirmation(stage, Alert.AlertType.CONFIRMATION, getText("UI_DIALOG_REBOOKING_ASSIGN_TITLE"),
				getText("UI_DIALOG_REBOOKING_ASSIGN_SUMMARY_HEADER"), content, assignButton, ButtonType.CANCEL);
	}

	private boolean showMissingRebookingSummary(MissingRebookingCreationSummary summary) {
		Label summaryLabel = new Label(getText("UI_DIALOG_REBOOKING_CREATE_SUMMARY_TEXT", summary.candidateCount()));
		summaryLabel.setWrapText(true);

		TableView<MissingRebookingSummaryRow> summaryTable = new TableView<>(
				FXCollections.observableArrayList(createMissingRebookingSummaryRows(summary.routeSummaries())));
		TableColumn<MissingRebookingSummaryRow, String> sourceColumn = new TableColumn<>(getText("UI_TABLE_SOURCE_ACCOUNT"));
		sourceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().sourceAccountName()));
		sourceColumn.setPrefWidth(190);
		sourceColumn.setSortable(false);
		TableColumn<MissingRebookingSummaryRow, String> targetColumn = new TableColumn<>(getText("UI_TABLE_TARGET_ACCOUNT"));
		targetColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().targetAccountName()));
		targetColumn.setPrefWidth(190);
		targetColumn.setSortable(false);
		TableColumn<MissingRebookingSummaryRow, Integer> missingColumn = new TableColumn<>(getText("UI_TABLE_REBOOKING_MISSING"));
		missingColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().missingRebookings()));
		missingColumn.setPrefWidth(140);
		missingColumn.setSortable(false);
		summaryTable.getColumns().addAll(Arrays.asList(sourceColumn, targetColumn, missingColumn));
		GuiLayoutState.configureTable(summaryTable, "dialog.missingRebookings");
		summaryTable.setPrefWidth(540);
		summaryTable.setPrefHeight(Math.min(320, 48 + summary.routeSummaries().size() * 30));

		VBox content = new VBox(10, summaryLabel, summaryTable);
		content.setPrefWidth(560);
		ButtonType createButton = new ButtonType(getText("UI_DIALOG_REBOOKING_CREATE_CONFIRM"), ButtonBar.ButtonData.OK_DONE);
		return DialogWindowSupport.showConfirmation(stage, Alert.AlertType.CONFIRMATION, getText("UI_DIALOG_REBOOKING_CREATE_TITLE"),
				getText("UI_DIALOG_REBOOKING_CREATE_SUMMARY_HEADER"), content, createButton, ButtonType.CANCEL);
	}

	private List<MissingRebookingSummaryRow> createMissingRebookingSummaryRows(List<MissingRebookingRouteSummary> routeSummaries) {
		List<MissingRebookingSummaryRow> rows = new ArrayList<>();
		Integer previousSourceAccountId = null;
		for (MissingRebookingRouteSummary routeSummary : routeSummaries) {
			boolean sameSourceAccount = previousSourceAccountId != null && previousSourceAccountId == routeSummary.sourceAccountId();
			rows.add(new MissingRebookingSummaryRow(sameSourceAccount ? "" : routeSummary.sourceAccountName(), routeSummary.targetAccountName(),
					routeSummary.missingRebookings()));
			previousSourceAccountId = routeSummary.sourceAccountId();
		}
		return rows;
	}

	private void saveDetectedRebookings(RebookingAssignmentSummary summary) {
		Task<Integer> saveTask = new Task<>() {
			@Override
			protected Integer call() {
				return accountTransactionService.persistDetectedRebookingLinks(summary);
			}
		};
		saveTask.setOnSucceeded(event -> showInfo(stage, getText("UI_DIALOG_REBOOKING_ASSIGN_SAVED", saveTask.getValue())));
		saveTask.setOnFailed(event -> {
			log.error("Error saving assigned rebookings", saveTask.getException());
			showWarning(stage, getText("ALERT_REBOOKING_ASSIGN_ERROR"));
		});
		startBackgroundTask(saveTask, "gbanking-assign-rebookings-save");
	}

	private void createMissingRebookings(MissingRebookingCreationSummary summary) {
		Task<Integer> createTask = new Task<>() {
			@Override
			protected Integer call() {
				return accountTransactionService.createMissingRebookings(summary);
			}
		};
		createTask.setOnSucceeded(event -> showInfo(stage, getText("UI_DIALOG_REBOOKING_CREATE_SAVED", createTask.getValue())));
		createTask.setOnFailed(event -> {
			log.error("Error creating missing rebookings", createTask.getException());
			showWarning(stage, getText("ALERT_REBOOKING_CREATE_ERROR"));
		});
		startBackgroundTask(createTask, "gbanking-create-rebookings-save");
	}
}
