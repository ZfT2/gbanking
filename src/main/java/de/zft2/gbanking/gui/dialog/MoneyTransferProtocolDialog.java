package de.zft2.gbanking.gui.dialog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.VopResult;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.StatusSymbols;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
			Function<MoneyTransferProtocol, LocalDateTime> valueProvider) {
		return TableColumnFactory.createTextColumn(getText(titleKey),
				protocol -> formatDateTime(valueProvider.apply(protocol)), 150);
	}

	private TableColumn<MoneyTransferProtocol, String> createStatusColumn() {
		return TableColumnFactory.createTextColumn(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_STATUS"),
				protocol -> protocol.getResultStatus() != null ? protocol.getResultStatus().toString() : "", 175);
	}

	private TableColumn<MoneyTransferProtocol, Boolean> createBooleanColumn(String titleKey,
			Predicate<MoneyTransferProtocol> valueProvider) {
		return TableColumnFactory.createBooleanSymbolColumn(getText(titleKey), valueProvider, 95,
				StatusSymbols.VALIDATION_STYLE_CLASS, StatusSymbols.VALIDATION_STYLE_CLASS_VALID,
				StatusSymbols.VALIDATION_STYLE_CLASS_INVALID);
	}

	private TableColumn<MoneyTransferProtocol, VopResult> createVopResultColumn() {
		TableColumn<MoneyTransferProtocol, VopResult> column = TableColumnFactory.createValueColumn(
				getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_VOP_RESULT"), protocol -> protocol.getVopResult(), 120);
		column.setCellFactory(ignored -> createVopResultCell());
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
		TableColumn<MoneyTransferProtocol, String> column = TableColumnFactory.createTextColumn(
				getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_RESPONSE"), protocol -> protocol.getProtocolText(), 330);
		column.setCellFactory(FxTableUtils.createWrappedTextCellFactory(Pos.TOP_LEFT));
		return column;
	}

	private String formatDateTime(LocalDateTime value) {
		return value != null ? DISPLAY_FORMAT.format(value) : "";
	}
}
