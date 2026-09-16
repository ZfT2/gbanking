package de.zft2.gbanking.gui.dialog.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.gui.component.GBankingTableView;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.stock.StockPortfolioService.PriceSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class SecurityPriceDialog implements BaseMessages {

	private final String securityName;
	private final Currency defaultCurrency;
	private final Supplier<List<PriceSummary>> priceLoader;
	private final PriceSaveHandler saveHandler;
	private final PriceDeleteHandler deleteHandler;
	private final Runnable changedHandler;
	private final Stage dialog;
	private final GBankingTableView<PriceSummary> table = new GBankingTableView<>();
	private final DatePicker dateField = new DatePicker(LocalDate.now());
	private final TextField priceField = new TextField();
	private final ComboBox<Currency> currencyField;
	private final Button deleteButton = new Button(getText("UI_BUTTON_DELETE"));
	private PriceSummary selectedPrice;

	public SecurityPriceDialog(Window owner, String securityName, Currency defaultCurrency,
			Supplier<List<PriceSummary>> priceLoader, PriceSaveHandler saveHandler,
			PriceDeleteHandler deleteHandler, Runnable changedHandler) {
		this.securityName = securityName;
		this.defaultCurrency = defaultCurrency != null ? defaultCurrency : Currency.EUR;
		this.priceLoader = priceLoader;
		this.saveHandler = saveHandler;
		this.deleteHandler = deleteHandler;
		this.changedHandler = changedHandler;
		currencyField = StockDialogSupport.createCurrencyCombo(this.defaultCurrency);
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_PRICE_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		reload();
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_PRICE_DIALOG_HEADER", securityName));
		header.getStyleClass().add("gbanking-form-section-title");
		configureTable();

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 3);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_DATE"), dateField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_PRICE"), priceField, 1, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_CURRENCY"), currencyField, 2, 0);

		Button newButton = new Button(getText("UI_BUTTON_NEW"));
		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button closeButton = new Button(getText("UI_BUTTON_CLOSE"));
		deleteButton.setDisable(true);
		newButton.setOnAction(event -> clearSelection());
		saveButton.setOnAction(event -> save());
		deleteButton.setOnAction(event -> delete());
		closeButton.setOnAction(event -> dialog.close());

		VBox root = DialogWindowSupport.createDialogRoot(header, table, form,
				DialogWindowSupport.createButtonBar(newButton, saveButton, deleteButton, closeButton));
		VBox.setVgrow(table, Priority.ALWAYS);
		dialog.setScene(DialogWindowSupport.createScene(root, 720, 480));
	}

	private void configureTable() {
		table.getColumns().add(TableColumnFactory.createCalendarDateColumn(getText("UI_STOCK_FIELD_DATE"),
				PriceSummary::date, 105));
		table.getColumns().add(TableColumnFactory.createAmountColumn(getText("UI_STOCK_FIELD_PRICE"),
				PriceSummary::price));
		table.getColumns().add(TableColumnFactory.createFixedTextColumn(getText("UI_STOCK_FIELD_CURRENCY"),
				summary -> summary.currency().name(), 85));
		table.getColumns().add(TableColumnFactory.createTextColumn(getText("UI_STOCK_FIELD_SOURCE"),
				PriceSummary::sourceName, 120, 180));
		table.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> selectPrice(selected));
	}

	private void reload() {
		table.setItems(FXCollections.observableArrayList(priceLoader.get()));
	}

	private void selectPrice(PriceSummary selected) {
		selectedPrice = selected;
		deleteButton.setDisable(selected == null);
		if (selected == null) {
			return;
		}
		dateField.setValue(selected.date());
		priceField.setText(StockDialogSupport.formatDecimal(selected.price()));
		currencyField.setValue(selected.currency());
	}

	private void clearSelection() {
		selectedPrice = null;
		deleteButton.setDisable(true);
		table.getSelectionModel().clearSelection();
		dateField.setValue(LocalDate.now());
		priceField.clear();
		currencyField.setValue(defaultCurrency);
		priceField.requestFocus();
	}

	private void save() {
		try {
			BigDecimal price = StockDialogSupport.parseRequiredDecimal(priceField, getText("UI_STOCK_FIELD_PRICE"));
			boolean protectedChangeConfirmed = confirmProtectedChange("UI_STOCK_PRICE_CHANGE_CONFIRM_TITLE",
					"UI_STOCK_PRICE_CHANGE_CONFIRM_HEADER", "UI_STOCK_PRICE_CHANGE_CONFIRM_TEXT", "UI_BUTTON_EDIT");
			if (selectedPrice != null && selectedPrice.requiresExplicitConfirmation() && !protectedChangeConfirmed) {
				return;
			}
			saveHandler.save(selectedPrice != null ? selectedPrice.priceId() : null, dateField.getValue(),
					price, currencyField.getValue(), protectedChangeConfirmed);
			clearSelection();
			reload();
			changedHandler.run();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private void delete() {
		if (selectedPrice == null) {
			return;
		}
		boolean protectedPrice = selectedPrice.requiresExplicitConfirmation();
		String textKey = protectedPrice ? "UI_STOCK_PRICE_DELETE_IMPORTED_TEXT" : "UI_STOCK_PRICE_DELETE_MANUAL_TEXT";
		ButtonType delete = new ButtonType(getText("UI_BUTTON_DELETE"), ButtonBar.ButtonData.OK_DONE);
		if (!DialogWindowSupport.showConfirmation(dialog, Alert.AlertType.CONFIRMATION,
				getText("UI_STOCK_PRICE_DELETE_CONFIRM_TITLE"), getText("UI_STOCK_PRICE_DELETE_CONFIRM_HEADER"),
				getText(textKey, selectedPrice.sourceName()), delete, ButtonType.CANCEL)) {
			return;
		}
		try {
			deleteHandler.delete(selectedPrice.priceId(), protectedPrice);
			clearSelection();
			reload();
			changedHandler.run();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private boolean confirmProtectedChange(String titleKey, String headerKey, String textKey, String confirmButtonKey) {
		if (selectedPrice == null || !selectedPrice.requiresExplicitConfirmation()) {
			return false;
		}
		ButtonType confirm = new ButtonType(getText(confirmButtonKey), ButtonBar.ButtonData.OK_DONE);
		return DialogWindowSupport.showConfirmation(dialog, Alert.AlertType.CONFIRMATION,
				getText(titleKey), getText(headerKey), getText(textKey, selectedPrice.sourceName()),
				confirm, ButtonType.CANCEL);
	}

	@FunctionalInterface
	public interface PriceSaveHandler {

		void save(Integer correctedPriceId, LocalDate date, BigDecimal price, Currency currency,
				boolean protectedChangeConfirmed);
	}

	@FunctionalInterface
	public interface PriceDeleteHandler {

		void delete(int priceId, boolean protectedChangeConfirmed);
	}
}
