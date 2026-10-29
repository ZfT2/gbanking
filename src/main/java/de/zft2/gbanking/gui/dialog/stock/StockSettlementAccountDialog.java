package de.zft2.gbanking.gui.dialog.stock;

import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.util.FormControlUtils;
import de.zft2.gbanking.gui.util.FormGridHelper;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.util.AccountDisplay;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class StockSettlementAccountDialog extends AbstractStockDialog {

	private final PortfolioSummary portfolio;
	private final ChangeHandler changeHandler;
	private final Runnable savedHandler;
	private final ComboBox<BankAccount> accountField;
	private final DatePicker validFromField;

	public StockSettlementAccountDialog(Window owner, PortfolioSummary portfolio, List<BankAccount> accounts,
			ChangeHandler changeHandler, Runnable savedHandler) {
		super(owner, "UI_STOCK_SETTLEMENT_DIALOG_TITLE");
		this.portfolio = portfolio;
		this.changeHandler = changeHandler;
		this.savedHandler = savedHandler;
		accountField = new ComboBox<>(FXCollections.observableArrayList(accounts));
		validFromField = new DatePicker(defaultValidFrom(portfolio));
		initialize();
	}

	private void initialize() {
		accountField.setConverter(FormControlUtils.displayConverter(account -> AccountDisplay.label(account)));
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

		buildScene(getText("UI_STOCK_SETTLEMENT_DIALOG_HEADER", portfolio.displayName()), form,
				createActionButton("UI_BUTTON_SAVE", () -> save()), createCancelButton(), 590, 230);
	}

	private void save() {
		applyAndClose(() -> {
			changeHandler.change(accountField.getValue(), validFromField.getValue());
			savedHandler.run();
			return true;
		});
	}

	private static LocalDate defaultValidFrom(PortfolioSummary portfolio) {
		LocalDate today = LocalDate.now();
		return portfolio.openedAt() != null && portfolio.openedAt().isAfter(today) ? portfolio.openedAt() : today;
	}

	@FunctionalInterface
	public interface ChangeHandler {

		void change(BankAccount account, LocalDate validFrom);
	}
}
