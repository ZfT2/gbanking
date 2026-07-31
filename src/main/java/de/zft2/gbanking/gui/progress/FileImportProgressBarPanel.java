package de.zft2.gbanking.gui.progress;

import java.util.List;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.file.imp.CreditcardCsvImportBean.RejectedRow;
import de.zft2.gbanking.file.imp.FileImportBean.ImportAccountStatistics;
import de.zft2.gbanking.file.imp.FileImportTask;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;

public class FileImportProgressBarPanel extends BaseFileProgressBarPanel {

	private final BankAccount contextAccount;
	private final Runnable successCallback;

	public FileImportProgressBarPanel(Window parent) {
		this(parent, null, null);
	}

	public FileImportProgressBarPanel(Window parent, BankAccount contextAccount, Runnable successCallback) {
		super(parent);
		this.contextAccount = contextAccount;
		this.successCallback = successCallback;
	}

	@Override
	protected void onTaskSucceeded() {
		if (accountListPanel != null) {
			accountListPanel.refreshModelAccount();
		}
		if (successCallback != null) {
			successCallback.run();
		}
		if (task instanceof FileImportTask importTask) {
			addImportSummaryTable(importTask.getImportStatistics());
			showRejectedRows(importTask.getRejectedRows());
		}
	}

	@Override
	protected boolean keepDialogOpenOnSuccess() {
		return true;
	}

	@Override
	public void startTask(String fileName, ExportType exportType, AccountListPanel accountListPanel) {
		task = new FileImportTask(fileName, exportType, contextAccount);
		super.startTask(accountListPanel);
	}

	private void addImportSummaryTable(List<ImportAccountStatistics> statistics) {
		if (statistics == null || statistics.isEmpty()) {
			return;
		}

		Label summaryLabel = new Label(getText("UI_IMPORT_SUMMARY_HEADER"));
		TableView<ImportAccountStatistics> summaryTable = new TableView<>(FXCollections.observableArrayList(statistics));
		summaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
		summaryTable.setPrefHeight(150);
		summaryTable.setMinHeight(120);
		summaryTable.getColumns().setAll(List.<TableColumn<ImportAccountStatistics, ?>>of(createAccountColumn(),
				createNumberColumn("UI_IMPORT_SUMMARY_EXISTING_BOOKINGS", ImportAccountStatistics::getExistingBookings),
				createNumberColumn("UI_IMPORT_SUMMARY_ADDED_BOOKINGS", ImportAccountStatistics::getAddedBookings),
				createNumberColumn("UI_IMPORT_SUMMARY_UPDATED_BOOKINGS", ImportAccountStatistics::getUpdatedBookings),
				createNumberColumn("UI_IMPORT_SUMMARY_SKIPPED_BOOKINGS", ImportAccountStatistics::getSkippedBookings),
				createNumberColumn("UI_IMPORT_SUMMARY_TOTAL_BOOKINGS", ImportAccountStatistics::getTotalBookings)));
		GuiLayoutState.configureTable(summaryTable, "dialog.fileImportSummary");
		contentBox.getChildren().addAll(summaryLabel, summaryTable);
	}

	private TableColumn<ImportAccountStatistics, String> createAccountColumn() {
		TableColumn<ImportAccountStatistics, String> column = new TableColumn<>(getText("UI_IMPORT_SUMMARY_ACCOUNT_COLUMN"));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getAccountName()));
		column.setPrefWidth(180);
		return column;
	}

	private TableColumn<ImportAccountStatistics, Integer> createNumberColumn(String titleKey,
			java.util.function.ToIntFunction<ImportAccountStatistics> valueProvider) {
		TableColumn<ImportAccountStatistics, Integer> column = new TableColumn<>(getText(titleKey));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.applyAsInt(data.getValue())));
		column.setPrefWidth(90);
		return column;
	}

	private void showRejectedRows(List<RejectedRow> rejectedRows) {
		if (rejectedRows == null || rejectedRows.isEmpty()) {
			return;
		}
		String details = rejectedRows.stream()
				.map(row -> getText("UI_CREDITCARD_IMPORT_REJECTED_ROW", row.lineNumber(), row.reason()))
				.collect(Collectors.joining(System.lineSeparator()));
		DialogWindowSupport.showAlert(dialogStage, Alert.AlertType.WARNING, getText("UI_MENU_FILE_IMPORT_CREDITCARD"),
				getText("UI_CREDITCARD_IMPORT_REJECTED_HEADER", rejectedRows.size()), details);
	}
}
