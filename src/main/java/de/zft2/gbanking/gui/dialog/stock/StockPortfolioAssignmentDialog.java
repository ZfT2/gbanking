package de.zft2.gbanking.gui.dialog.stock;

import java.util.ArrayList;
import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.mapper.HbciMapper;
import de.zft2.gbanking.service.stock.StockPortfolioFinTsService;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class StockPortfolioAssignmentDialog implements BaseMessages {

	private final List<BankAccount> portfolioAccounts;
	private final List<BankAccount> settlementAccounts;
	private final List<ComboBox<BankAccount>> settlementFields = new ArrayList<>();
	private final Stage dialog;
	private List<AccountAssignment> result;

	StockPortfolioAssignmentDialog(Window owner, List<BankAccount> portfolioAccounts,
			List<BankAccount> settlementAccounts) {
		this.portfolioAccounts = List.copyOf(portfolioAccounts);
		this.settlementAccounts = List.copyOf(settlementAccounts);
		dialog = DialogWindowSupport.createModalStage(owner, "UI_STOCK_ASSIGNMENT_DIALOG_TITLE");
		initialize();
	}

	List<AccountAssignment> showAndWait() {
		dialog.showAndWait();
		return result;
	}

	private void initialize() {
		Label header = new Label(getText("UI_STOCK_ASSIGNMENT_DIALOG_HEADER"));
		header.getStyleClass().add("gbanking-form-section-title");
		GridPane form = createForm();

		Button acceptButton = new Button(getText("UI_BUTTON_OK"));
		Button cancelButton = new Button(getText("UI_BUTTON_CANCEL"));
		acceptButton.setDefaultButton(true);
		cancelButton.setCancelButton(true);
		acceptButton.setOnAction(event -> accept());
		cancelButton.setOnAction(event -> dialog.close());
		VBox root = DialogWindowSupport.createDialogRoot(header, form,
				DialogWindowSupport.createButtonBar(acceptButton, cancelButton));
		dialog.setScene(DialogWindowSupport.createScene(root, 760, Math.max(230, 150 + portfolioAccounts.size() * 55)));
	}

	private GridPane createForm() {
		GridPane form = new GridPane();
		form.setHgap(12);
		form.setVgap(8);
		form.add(new Label(getText("UI_STOCK_FIELD_PORTFOLIO")), 0, 0);
		form.add(new Label(getText("UI_STOCK_FIELD_SETTLEMENT_ACCOUNT")), 1, 0);
		for (int index = 0; index < portfolioAccounts.size(); index++) {
			BankAccount portfolioAccount = portfolioAccounts.get(index);
			ComboBox<BankAccount> settlementField = createAccountCombo();
			preselectSettlementAccount(settlementField, portfolioAccount);
			settlementFields.add(settlementField);
			form.add(new Label(StockPortfolioFinTsService.accountDisplayName(portfolioAccount)), 0, index + 1);
			form.add(settlementField, 1, index + 1);
		}
		return form;
	}

	private ComboBox<BankAccount> createAccountCombo() {
		ComboBox<BankAccount> resultField = new ComboBox<>(FXCollections.observableArrayList(settlementAccounts));
		resultField.setMaxWidth(Double.MAX_VALUE);
		resultField.setConverter(new StringConverter<>() {
			@Override
			public String toString(BankAccount account) {
				return StockPortfolioFinTsService.accountDisplayName(account);
			}

			@Override
			public BankAccount fromString(String value) {
				return null;
			}
		});
		return resultField;
	}

	private void preselectSettlementAccount(ComboBox<BankAccount> field, BankAccount portfolioAccount) {
		String referenceKey = portfolioAccount.getProviderReferenceAccountKey();
		if (referenceKey != null) {
			List<BankAccount> matches = settlementAccounts.stream()
					.filter(account -> referenceKey.equals(HbciMapper.accountReferenceKey(account))).toList();
			if (matches.size() == 1) {
				field.setValue(matches.get(0));
				return;
			}
		}
		if (settlementAccounts.size() == 1) {
			field.getSelectionModel().selectFirst();
		}
	}

	private void accept() {
		if (settlementFields.stream().anyMatch(field -> field.getValue() == null)) {
			DialogWindowSupport.showAlert(dialog, AlertType.WARNING, getText("ALERT_STOCK_ASSIGNMENT_REQUIRED"));
			return;
		}
		List<AccountAssignment> assignments = new ArrayList<>(portfolioAccounts.size());
		for (int portfolioIndex = 0; portfolioIndex < portfolioAccounts.size(); portfolioIndex++) {
			int settlementIndex = settlementAccounts.indexOf(settlementFields.get(portfolioIndex).getValue());
			assignments.add(new AccountAssignment(portfolioIndex, settlementIndex));
		}
		result = List.copyOf(assignments);
		dialog.close();
	}

	record AccountAssignment(int portfolioIndex, int settlementIndex) {
	}
}
