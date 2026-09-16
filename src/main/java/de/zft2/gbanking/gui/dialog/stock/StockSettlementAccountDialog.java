package de.zft2.gbanking.gui.dialog.stock;

import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class StockSettlementAccountDialog implements BaseMessages {

	private final PortfolioSummary portfolio;
	private final ChangeHandler changeHandler;
	private final Runnable savedHandler;
	private final Stage dialog;
	private final ComboBox<BankAccount> accountField;
	private final DatePicker validFromField;

	public StockSettlementAccountDialog(Window owner, PortfolioSummary portfolio, List<BankAccount> accounts,
			ChangeHandler changeHandler, Runnable savedHandler) {
		this.portfolio = portfolio;
		this.changeHandler = changeHandler;
		this.savedHandler = savedHandler;
		accountField = new ComboBox<>(FXCollections.observableArrayList(accounts));
		validFromField = new DatePicker(defaultValidFrom(portfolio));
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_SETTLEMENT_DIALOG_TITLE");
		initialize();
	}

	public void show() {
		dialog.showAndWait();
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_SETTLEMENT_DIALOG_HEADER", portfolio.displayName()));
		header.getStyleClass().add("gbanking-form-section-title");
		accountField.setConverter(accountConverter());
		accountField.getItems().stream()
				.filter(account -> account.getId() == portfolio.settlementAccountId())
				.findFirst().ifPresent(accountField::setValue);
		if (accountField.getValue() == null && !accountField.getItems().isEmpty()) {
			accountField.getSelectionModel().selectFirst();
		}

		GridPane form = FormGridHelper.createDefaultGrid();
		FormGridHelper.setEqualGrowColumns(form, 2);
		FormGridHelper.addFieldAbove(form, getText("UI_STOCK_FIELD_SETTLEMENT_ACCOUNT"), accountField, 0, 0);
		FormGridHelper.addFieldAbove(form, getText("UI_TABLE_VALID_FROM"), validFromField, 1, 0);

		Button saveButton = new Button(getText("UI_BUTTON_SAVE"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		saveButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		saveButton.setOnAction(event -> save());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(saveButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 590, 230));
	}

	private void save() {
		try {
			changeHandler.change(accountField.getValue(), validFromField.getValue());
			savedHandler.run();
			dialog.close();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	private static LocalDate defaultValidFrom(PortfolioSummary portfolio) {
		LocalDate today = LocalDate.now();
		return portfolio.openedAt() != null && portfolio.openedAt().isAfter(today) ? portfolio.openedAt() : today;
	}

	private static StringConverter<BankAccount> accountConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(BankAccount account) {
				if (account == null) {
					return "";
				}
				String name = account.getAccountName();
				String number = account.getIban() != null && !account.getIban().isBlank()
						? account.getIban() : account.getNumber();
				return name != null && !name.isBlank()
						? name + (number != null && !number.isBlank() ? " (" + number + ')' : "")
						: number != null ? number : "";
			}

			@Override
			public BankAccount fromString(String value) {
				return null;
			}
		};
	}

	@FunctionalInterface
	public interface ChangeHandler {

		void change(BankAccount account, LocalDate validFrom);
	}
}
