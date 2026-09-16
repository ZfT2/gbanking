package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.service.stock.StockPortfolioService.PositionSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class StockPositionTransferDialog implements BaseMessages {

	private final PositionSummary position;
	private final TransferHandler transferHandler;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<PortfolioSummary> targetField;
	private final TextField quantityField = new TextField();
	private final DatePicker dateField = new DatePicker(LocalDate.now());

	public StockPositionTransferDialog(Window owner, PositionSummary position, List<PortfolioSummary> targets,
			TransferHandler transferHandler, Runnable savedHandler) {
		this.position = position;
		this.transferHandler = transferHandler;
		this.savedHandler = savedHandler;
		targetField = new ComboBox<>(FXCollections.observableArrayList(targets));
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_POSITION_TRANSFER_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_POSITION_TRANSFER_DIALOG_HEADER", position.securityName()));
		header.getStyleClass().add("gbanking-form-section-title");
		if (!targetField.getItems().isEmpty()) {
			targetField.getSelectionModel().selectFirst();
		}
		quantityField.setText(StockDialogSupport.formatDecimal(position.quantity()));

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_TARGET_PORTFOLIO"), targetField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_QUANTITY"), quantityField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_DATE"), dateField, 2, 0);

		Button transferButton = new Button(getText("UI_STOCK_ACTION_TRANSFER"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		transferButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		transferButton.setOnAction(event -> transfer());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(transferButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 700, 250));
	}

	private void transfer() {
		try {
			transferHandler.transfer(targetField.getValue(),
					StockDialogSupport.parseRequiredDecimal(quantityField, getText("UI_STOCK_FIELD_QUANTITY")), dateField.getValue());
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	@FunctionalInterface
	public interface TransferHandler {

		void transfer(PortfolioSummary target, BigDecimal quantity, LocalDate date);
	}
}
