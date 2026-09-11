package de.zft2.gbanking.gui.dialog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.ImportedBankNameCorrectionHandler;
import de.zft2.gbanking.file.imp.ImportedBankNameFinding;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.util.FxTableUtils;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ImportedBankNameValidationDialog implements BaseMessages, ImportedBankNameCorrectionHandler {

	private final Window owner;

	public ImportedBankNameValidationDialog(Window owner) {
		this.owner = owner;
	}

	@Override
	public Map<Integer, String> selectCorrections(List<ImportedBankNameFinding> findings) {
		if (Platform.isFxApplicationThread()) {
			return showDialog(findings);
		}
		FutureTask<Map<Integer, String>> dialogTask = new FutureTask<>(() -> showDialog(findings));
		Platform.runLater(dialogTask);
		try {
			return dialogTask.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GBankingException("Bank name validation was interrupted.", exception);
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof CancellationException cancellationException) {
				throw cancellationException;
			}
			throw new GBankingException("Bank name validation dialog failed.", exception.getCause());
		}
	}

	private Map<Integer, String> showDialog(List<ImportedBankNameFinding> findings) {
		if (findings == null || findings.isEmpty()) {
			return Map.of();
		}
		Stage dialog = DialogWindowSupport.createModalStage(owner, "UI_DIALOG_IMPORT_BANK_NAME_TITLE");
		List<DialogRow> rows = findings.stream().map(this::toDialogRow).toList();
		TableView<DialogRow> table = createTable(rows);
		DialogResult result = new DialogResult();

		Button correctButton = new Button(getText("UI_BUTTON_CORRECT"));
		correctButton.setTooltip(new Tooltip(getText("UI_TOOLTIP_IMPORT_BANK_NAME_CORRECT")));
		correctButton.setDefaultButton(true);
		correctButton.setOnAction(event -> {
			result.continueImport = true;
			result.corrections = rows.stream().filter(row -> row.choice().bankName() != null)
					.collect(Collectors.toMap(row -> row.finding().bookingId(), row -> row.choice().bankName()));
			dialog.close();
		});
		Button skipButton = new Button(getText("UI_BUTTON_SKIP"));
		skipButton.setTooltip(new Tooltip(getText("UI_TOOLTIP_IMPORT_BANK_NAME_SKIP")));
		skipButton.setOnAction(event -> {
			result.continueImport = true;
			dialog.close();
		});
		Button cancelImportButton = new Button(getText("UI_BUTTON_CANCEL_IMPORT"));
		cancelImportButton.setTooltip(new Tooltip(getText("UI_TOOLTIP_IMPORT_BANK_NAME_CANCEL")));
		cancelImportButton.setCancelButton(true);
		cancelImportButton.setOnAction(event -> dialog.close());

		Label header = new Label(getText("UI_DIALOG_IMPORT_BANK_NAME_HEADER", findings.size()));
		header.setWrapText(true);
		DialogWindowSupport.setVgrowAlways(table);
		dialog.setScene(DialogWindowSupport.createScene(DialogWindowSupport.createDialogRoot(header, table,
				DialogWindowSupport.createButtonBar(correctButton, skipButton, cancelImportButton)), 1450, 620));
		dialog.setMinWidth(1000);
		dialog.setMinHeight(460);
		dialog.showAndWait();
		if (!result.continueImport) {
			throw new CancellationException("Booking import cancelled by user.");
		}
		return result.corrections;
	}

	private TableView<DialogRow> createTable(List<DialogRow> rows) {
		TableView<DialogRow> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.setPlaceholder(new Label(getText("UI_DIALOG_IMPORT_BANK_NAME_EMPTY")));
		table.getColumns().setAll(List.<TableColumn<DialogRow, ?>>of(
				createDateColumn(), createTextColumn("UI_TABLE_ACCOUNT", row -> row.finding().accountName(), 150, false),
				createTextColumn("UI_DIALOG_IMPORT_BANK_NAME_PURPOSE", row -> row.finding().purpose(), 230, true),
				createAmountColumn(),
				createTextColumn("UI_DIALOG_IMPORT_BANK_NAME_RECIPIENT", row -> row.finding().recipientName(), 180, false),
				createTextColumn("UI_DIALOG_IMPORT_BANK_NAME_ACCOUNT", row -> row.finding().accountIdentifier(), 190, false),
				createTextColumn("UI_DIALOG_IMPORT_BANK_NAME_BANK_CODE", row -> row.finding().bankIdentifier(), 120, false),
				createTextColumn("UI_DIALOG_IMPORT_BANK_NAME_CURRENT", row -> row.finding().currentBankName(), 220, false),
				createCorrectionColumn()));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
		GuiLayoutState.configureTable(table, "dialog.importedBankNameValidation");
		return table;
	}

	private TableColumn<DialogRow, LocalDate> createDateColumn() {
		TableColumn<DialogRow, LocalDate> column = new TableColumn<>(getText("UI_DIALOG_IMPORT_BANK_NAME_DATE"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().finding().date()));
		column.setCellFactory(FxTableUtils.createLocalDateCellFactory());
		column.setPrefWidth(95);
		return column;
	}

	private TableColumn<DialogRow, BigDecimal> createAmountColumn() {
		TableColumn<DialogRow, BigDecimal> column = new TableColumn<>(getText("UI_DIALOG_IMPORT_BANK_NAME_AMOUNT"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().finding().amount()));
		column.setCellFactory(FxTableUtils.createBigDecimalAmountCellFactory());
		column.setPrefWidth(100);
		return column;
	}

	private TableColumn<DialogRow, String> createTextColumn(String titleKey, Function<DialogRow, String> valueProvider, double width,
			boolean wrapText) {
		TableColumn<DialogRow, String> column = new TableColumn<>(getText(titleKey));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
		if (wrapText) {
			column.setCellFactory(FxTableUtils.createWrappedTextCellFactory());
		}
		column.setPrefWidth(width);
		return column;
	}

	private TableColumn<DialogRow, BankNameChoice> createCorrectionColumn() {
		TableColumn<DialogRow, BankNameChoice> column = new TableColumn<>(getText("UI_DIALOG_IMPORT_BANK_NAME_CORRECTED"));
		column.setCellValueFactory(data -> data.getValue().choiceProperty());
		column.setCellFactory(ignored -> new BankNameChoiceCell());
		column.setPrefWidth(230);
		column.setSortable(false);
		return column;
	}

	private DialogRow toDialogRow(ImportedBankNameFinding finding) {
		BankNameChoice unchanged = new BankNameChoice(null, getText("UI_DIALOG_IMPORT_BANK_NAME_UNCHANGED"));
		List<BankNameChoice> choices = new ArrayList<>();
		choices.add(unchanged);
		finding.candidateBankNames().forEach(name -> choices.add(new BankNameChoice(name, name)));
		BankNameChoice selected = choices.stream().filter(choice -> Objects.equals(choice.bankName(), finding.suggestedBankName()))
				.findFirst().orElse(unchanged);
		return new DialogRow(finding, choices, selected);
	}

	private static final class BankNameChoiceCell extends TableCell<DialogRow, BankNameChoice> {

		private final ComboBox<BankNameChoice> comboBox = new ComboBox<>();

		private BankNameChoiceCell() {
			comboBox.setMaxWidth(Double.MAX_VALUE);
			comboBox.prefWidthProperty().bind(widthProperty().subtract(16));
			comboBox.setOnAction(event -> {
				DialogRow row = getTableRow() != null ? getTableRow().getItem() : null;
				if (row != null && comboBox.getValue() != null) {
					row.setChoice(comboBox.getValue());
				}
			});
			setAlignment(Pos.CENTER_LEFT);
		}

		@Override
		protected void updateItem(BankNameChoice choice, boolean empty) {
			super.updateItem(choice, empty);
			DialogRow row = getTableRow() != null ? getTableRow().getItem() : null;
			if (empty || row == null) {
				setGraphic(null);
				return;
			}
			comboBox.setItems(FXCollections.observableArrayList(row.choices()));
			comboBox.setValue(choice);
			setGraphic(comboBox);
		}
	}

	private record BankNameChoice(String bankName, String label) {

		@Override
		public String toString() {
			return label;
		}
	}

	private record DialogRow(ImportedBankNameFinding finding, List<BankNameChoice> choices,
			ObjectProperty<BankNameChoice> choiceProperty) {

		private DialogRow(ImportedBankNameFinding finding, List<BankNameChoice> choices, BankNameChoice choice) {
			this(finding, List.copyOf(choices), new SimpleObjectProperty<>(choice));
		}

		private BankNameChoice choice() {
			return choiceProperty.get();
		}

		private void setChoice(BankNameChoice choice) {
			choiceProperty.set(choice);
		}
	}

	private static final class DialogResult {
		private boolean continueImport;
		private Map<Integer, String> corrections = Map.of();
	}
}
