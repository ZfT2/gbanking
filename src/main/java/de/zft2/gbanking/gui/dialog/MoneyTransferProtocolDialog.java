package de.zft2.gbanking.gui.dialog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.VopResult;
import de.zft2.gbanking.gui.GuiLayoutState;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

public class MoneyTransferProtocolDialog implements BaseMessages {

	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	public void show(Window parentWindow, MoneyTransfer moneyTransfer, List<MoneyTransferProtocol> protocols) {
		Stage dialog = DialogWindowSupport.createModalStage(parentWindow, "UI_DIALOG_MONEYTRANSFER_PROTOCOL_TITLE");
		Label header = new Label(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_HEADER", moneyTransfer.getId()));
		TableView<MoneyTransferProtocol> protocolTable = createProtocolTable(protocols);

		Button closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		closeButton.setOnAction(event -> dialog.close());

		DialogWindowSupport.setVgrowAlways(protocolTable);
		Scene scene = DialogWindowSupport.createScene(
				DialogWindowSupport.createDialogRoot(header, protocolTable, DialogWindowSupport.createButtonBar(closeButton)), 1_250, 520);
		scene.getStylesheets().add(getClass().getResource("/css/gbanking-table.css").toExternalForm());
		dialog.setScene(scene);
		dialog.showAndWait();
	}

	private TableView<MoneyTransferProtocol> createProtocolTable(List<MoneyTransferProtocol> protocols) {
		TableView<MoneyTransferProtocol> table = new TableView<>(FXCollections.observableArrayList(protocols != null ? protocols : List.of()));
		table.setPlaceholder(new Label(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_EMPTY")));
		table.getColumns().setAll(List.<TableColumn<MoneyTransferProtocol, ?>>of(
				createTimeColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_START", protocol -> protocol.getTimeStart()),
				createTimeColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_FINISH", protocol -> protocol.getTimeFinish()), createStatusColumn(),
				createBooleanColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_PIN_OK", protocol -> protocol.isPinOk()),
				createBooleanColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_SCA_REQUIRED", protocol -> protocol.isScaRequired()),
				createBooleanColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_VOP_REQUIRED", protocol -> protocol.isVopRequired()), createVopResultColumn(),
				createBooleanColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_NAME_CORRECTED", protocol -> protocol.isRecipientNameCorrected()),
				createResponseColumn()));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
		GuiLayoutState.configureTable(table, "dialog.moneyTransferProtocol");
		return table;
	}

	private TableColumn<MoneyTransferProtocol, String> createTimeColumn(String titleKey,
			java.util.function.Function<MoneyTransferProtocol, LocalDateTime> valueProvider) {
		TableColumn<MoneyTransferProtocol, String> column = new TableColumn<>(getText(titleKey));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(formatDateTime(valueProvider.apply(data.getValue()))));
		column.setPrefWidth(150);
		return column;
	}

	private TableColumn<MoneyTransferProtocol, String> createStatusColumn() {
		TableColumn<MoneyTransferProtocol, String> column = new TableColumn<>(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_STATUS"));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(
				data.getValue().getResultStatus() != null ? data.getValue().getResultStatus().toString() : ""));
		column.setPrefWidth(175);
		return column;
	}

	private TableColumn<MoneyTransferProtocol, Boolean> createBooleanColumn(String titleKey,
			java.util.function.Predicate<MoneyTransferProtocol> valueProvider) {
		TableColumn<MoneyTransferProtocol, Boolean> column = new TableColumn<>(getText(titleKey));
		column.setCellValueFactory(data -> new ReadOnlyBooleanWrapper(valueProvider.test(data.getValue())));
		column.setCellFactory(ignored -> createBooleanCell());
		column.setPrefWidth(95);
		return column;
	}

	private TableCell<MoneyTransferProtocol, Boolean> createBooleanCell() {
		return new TableCell<>() {
			private final Label indicator = new Label();

			{
				setAlignment(Pos.CENTER);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
					return;
				}
				indicator.setText(item ? "\u2713" : "\u2717");
				indicator.getStyleClass().setAll("iban-validation-status", item ? "iban-valid" : "iban-invalid");
				setGraphic(indicator);
			}
		};
	}

	private TableColumn<MoneyTransferProtocol, VopResult> createVopResultColumn() {
		TableColumn<MoneyTransferProtocol, VopResult> column = new TableColumn<>(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_VOP_RESULT"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getVopResult()));
		column.setCellFactory(ignored -> createVopResultCell());
		column.setPrefWidth(120);
		return column;
	}

	private TableCell<MoneyTransferProtocol, VopResult> createVopResultCell() {
		return new TableCell<>() {
			@Override
			protected void updateItem(VopResult item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.toString());
				setTooltip(empty || item == null ? null : createTooltip(item));
			}
		};
	}

	private Tooltip createTooltip(VopResult result) {
		Tooltip tooltip = new Tooltip(getText("TOOLTIP_MONEYTRANSFER_PROTOCOL_VOP_" + result.name()));
		tooltip.setFont(Font.getDefault());
		return tooltip;
	}

	private TableColumn<MoneyTransferProtocol, String> createResponseColumn() {
		TableColumn<MoneyTransferProtocol, String> column = new TableColumn<>(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_RESPONSE"));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getProtocolText()));
		column.setCellFactory(col -> createWrappedTextCell());
		column.setPrefWidth(330);
		return column;
	}

	private TableCell<MoneyTransferProtocol, String> createWrappedTextCell() {
		return new TableCell<>() {
			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.setAlignment(Pos.TOP_LEFT);
				label.maxWidthProperty().bind(widthProperty().subtract(12));
				setAlignment(Pos.TOP_LEFT);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || item.isBlank()) {
					setGraphic(null);
					return;
				}

				label.setText(item);
				setGraphic(label);
			}
		};
	}

	private String formatDateTime(LocalDateTime value) {
		return value != null ? DISPLAY_FORMAT.format(value) : "";
	}
}
