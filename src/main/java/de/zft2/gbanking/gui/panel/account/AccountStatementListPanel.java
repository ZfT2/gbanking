package de.zft2.gbanking.gui.panel.account;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import de.zft2.gbanking.service.account.AccountStatement;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseButton;

public class AccountStatementListPanel extends AbstractFilterableTablePanel<AccountStatement> {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private final Consumer<AccountStatement> selectionHandler;
	private final Consumer<AccountStatement> openHandler;
	private TableColumn<AccountStatement, LocalDate> statementDateCol;
	private TableColumn<AccountStatement, LocalDateTime> retrievedAtCol;

	public AccountStatementListPanel(Consumer<AccountStatement> selectionHandler, Consumer<AccountStatement> openHandler) {
		super(FXCollections.observableArrayList());
		this.selectionHandler = selectionHandler;
		this.openHandler = openHandler;
		createInnerPanel();
	}

	private void createInnerPanel() {
		setPanelTitleByKey("UI_PANEL_ACCOUNT_STATEMENTS_LIST");
		setColumns(createColumns());
		configureDefaultSorting();
		configureTableLayout("accountStatements");
		installDoubleClickHandler();
		onSelection(selectionHandler);
	}

	private List<TableColumn<AccountStatement, ?>> createColumns() {
		statementDateCol = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_DATE"),
				statement -> statement.statementDate(), 110);
		TableColumn<AccountStatement, String> periodCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_PERIOD"),
				statement -> formatPeriod(statement), 180, 220);
		TableColumn<AccountStatement, String> numberCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_NUMBER"),
				statement -> formatStatementNumber(statement), 95);
		TableColumn<AccountStatement, String> formatCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_FORMAT"),
				statement -> statement.format(), 90);
		TableColumn<AccountStatement, String> fileCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_FILE"),
				statement -> statement.fileName(), 220, 320);
		retrievedAtCol = createDateTimeColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_RETRIEVED_AT"), statement -> statement.retrievedAt(), 145);
		TableColumn<AccountStatement, String> sizeCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_STATEMENT_SIZE"),
				statement -> formatFileSize(statement.size()), 90);
		TableColumn<AccountStatement, String> acknowledgedCol = TableColumnFactory.createFixedTextColumn(
				getText("UI_TABLE_ACCOUNT_STATEMENT_ACKNOWLEDGED"), statement -> formatBoolean(statement.acknowledged()), 80);

		return List.of(statementDateCol, periodCol, numberCol, formatCol, fileCol, retrievedAtCol, sizeCol, acknowledgedCol);
	}

	private TableColumn<AccountStatement, LocalDateTime> createDateTimeColumn(String title,
			java.util.function.Function<AccountStatement, LocalDateTime> valueProvider, double width) {
		TableColumn<AccountStatement, LocalDateTime> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(valueProvider.apply(data.getValue())));
		column.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(LocalDateTime item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DATE_TIME_FORMAT.format(item));
			}
		});
		FxTableUtils.setFixedWidth(column, width);
		return column;
	}

	private void configureDefaultSorting() {
		statementDateCol.setSortType(TableColumn.SortType.DESCENDING);
		retrievedAtCol.setSortType(TableColumn.SortType.DESCENDING);
		tableView.getSortOrder().setAll(Arrays.asList(statementDateCol, retrievedAtCol));
		tableView.sort();
	}

	public void updateModelStatements(List<AccountStatement> statements) {
		replaceItems(statements != null ? statements : List.of());
		tableView.getSelectionModel().clearSelection();
	}

	public AccountStatement getSelectedStatement() {
		return getSelectedItem();
	}

	private void installDoubleClickHandler() {
		tableView.setRowFactory(table -> {
			TableRow<AccountStatement> row = tableView.createDefaultRow();
			row.setOnMouseClicked(event -> {
				if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
					openHandler.accept(row.getItem());
				}
			});
			return row;
		});
	}

	@Override
	protected boolean matchesFilter(AccountStatement statement, String filter) {
		return matchesAny(filter, statement.fileName(), statement.format(), statement.accountName(), statement.iban(), statement.bic(),
				formatStatementNumber(statement), formatPeriod(statement), formatBoolean(statement.acknowledged()));
	}

	private String formatPeriod(AccountStatement statement) {
		return formatPeriod(statement.startDate(), statement.endDate());
	}

	private String formatPeriod(LocalDate start, LocalDate end) {
		if (start == null && end == null) {
			return "";
		}
		if (start == null) {
			return DateFormatUtils.formatLong(end);
		}
		if (end == null) {
			return DateFormatUtils.formatLong(start);
		}
		return DateFormatUtils.formatLong(start) + " - " + DateFormatUtils.formatLong(end);
	}

	private String formatStatementNumber(AccountStatement statement) {
		return AccountStatementFormatUtils.formatStatementNumber(statement);
	}

	private String formatFileSize(long size) {
		if (size <= 0) {
			return "";
		}
		if (size < 1024) {
			return size + " B";
		}
		return (size / 1024) + " KB";
	}

	private String formatBoolean(boolean value) {
		return getText(value ? "UI_LABEL_BOOLEAN_TRUE" : "UI_LABEL_BOOLEAN_FALSE");
	}
}
