package de.zft2.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccountClosureService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class MissingBankAccountsPane extends VBox implements BaseMessages {

	private final List<Row> rows;

	public MissingBankAccountsPane(BankAccess access) {
		this(ServiceRegistry.getService(BankAccountClosureService.class).findMissingAccounts(access));
	}

	MissingBankAccountsPane(List<BankAccount> missingAccounts) {
		super(6);
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		rows = missingAccounts.stream().map(account -> new Row(account,
				new SimpleStringProperty(DateFormatUtils.formatLong(account.getClosedAt() != null ? account.getClosedAt() : today)))).toList();
		Label hint = new Label(getText("UI_BANK_ACCOUNTS_MISSING_HINT"));
		hint.setWrapText(true);
		TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.setId("missing-bank-accounts");
		table.setPlaceholder(new Label(getText("UI_BANK_ACCOUNTS_MISSING_EMPTY")));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		TableColumn<Row, Row> dateColumn = TableColumnFactory.createValueColumn(getText("UI_LABEL_ACCOUNT_CLOSED_AT"), row -> row);
		dateColumn.setCellFactory(ignored -> new ClosureDateCell());
		dateColumn.setMinWidth(150);
		table.getColumns().setAll(List.of(
				TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_NAME"), row -> row.account().getAccountName(), 180, 220),
				TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"), row -> row.account().getIban(), 200, 240),
				TableColumnFactory.createTextColumn(getText("UI_LABEL_ACCOUNT_NUMBER"), row -> row.account().getNumber(), 100, 130),
				dateColumn));
		table.setPrefHeight(rows.isEmpty() ? 80 : 160);
		table.setMinHeight(70);
		GuiLayoutState.configureTable(table, "dialog.missingBankAccounts");
		getChildren().addAll(new Label(getText("UI_BANK_ACCOUNTS_MISSING")), hint, table);
	}

	public Map<Integer, LocalDate> getClosureDates() {
		Map<Integer, LocalDate> result = new LinkedHashMap<>();
		try {
			for (Row row : rows) {
				result.put(row.account().getId(), DateFormatUtils.parseLong(row.date().get()));
			}
		} catch (DateTimeParseException exception) {
			throw new GBankingException(getText("ALERT_ACCOUNT_CLOSED_AT_INVALID"), exception);
		}
		return result;
	}

	private record Row(BankAccount account, StringProperty date) {
	}

	private static final class ClosureDateCell extends TableCell<Row, Row> {

		private final TextField dateField = new TextField();
		private Row boundRow;

		private ClosureDateCell() {
			dateField.setPromptText("dd.MM.yyyy");
			dateField.setMaxWidth(Double.MAX_VALUE);
		}

		@Override
		protected void updateItem(Row row, boolean empty) {
			super.updateItem(row, empty);
			if (boundRow != null) {
				dateField.textProperty().unbindBidirectional(boundRow.date());
			}
			boundRow = empty ? null : row;
			if (boundRow != null) {
				dateField.textProperty().bindBidirectional(boundRow.date());
			}
			setGraphic(boundRow != null ? dateField : null);
		}
	}
}
