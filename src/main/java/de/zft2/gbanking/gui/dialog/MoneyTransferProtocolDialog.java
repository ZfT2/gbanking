package de.zft2.gbanking.gui.dialog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.gui.GuiLayoutState;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
		dialog.setScene(DialogWindowSupport.createScene(
				DialogWindowSupport.createDialogRoot(header, protocolTable, DialogWindowSupport.createButtonBar(closeButton)), 800, 480));
		dialog.showAndWait();
	}

	private TableView<MoneyTransferProtocol> createProtocolTable(List<MoneyTransferProtocol> protocols) {
		TableView<MoneyTransferProtocol> table = new TableView<>(FXCollections.observableArrayList(protocols != null ? protocols : List.of()));
		table.setPlaceholder(new Label(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_EMPTY")));
		table.getColumns().setAll(List.<TableColumn<MoneyTransferProtocol, ?>>of(
				createTimeColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_START", MoneyTransferProtocol::getTimeStart),
				createTimeColumn("UI_DIALOG_MONEYTRANSFER_PROTOCOL_FINISH", MoneyTransferProtocol::getTimeFinish), createStatusColumn(), createResponseColumn()));
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
				data.getValue().getMoneytransferStatus() != null ? data.getValue().getMoneytransferStatus().toString() : ""));
		column.setPrefWidth(110);
		return column;
	}

	private TableColumn<MoneyTransferProtocol, String> createResponseColumn() {
		TableColumn<MoneyTransferProtocol, String> column = new TableColumn<>(getText("UI_DIALOG_MONEYTRANSFER_PROTOCOL_RESPONSE"));
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getProtocolText()));
		column.setCellFactory(col -> createWrappedTextCell());
		column.setPrefWidth(390);
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
