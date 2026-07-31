package de.zft2.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

public class BankMessageListPanel extends AbstractFilterableTablePanel<BankMessage> {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private final Consumer<BankMessage> selectionHandler;
	private TableColumn<BankMessage, LocalDate> versionDateCol;
	private TableColumn<BankMessage, LocalDateTime> retrievedAtCol;

	public BankMessageListPanel(Consumer<BankMessage> selectionHandler) {
		super(FXCollections.observableArrayList());
		this.selectionHandler = selectionHandler;
		createPanel();
	}

	private void createPanel() {
		setPanelTitleByKey("UI_PANEL_BANK_MESSAGES_LIST");
		setColumns(createColumns());
		configureDefaultSorting();
		configureTableLayout("bankMessages");
		onSelection(selectionHandler);
	}

	private List<TableColumn<BankMessage, ?>> createColumns() {
		versionDateCol = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_BANK_MESSAGE_VERSION_DATE"),
				message -> message.getVersionDate(), 110);
		TableColumn<BankMessage, String> codeCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK_MESSAGE_CODE"),
				message -> message.getCode(), 100);
		TableColumn<BankMessage, String> typeCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK_MESSAGE_TYPE"),
				message -> formatType(message.getType()), 110);
		TableColumn<BankMessage, String> formatCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BANK_MESSAGE_FORMAT"),
				message -> message.getFormat(), 90);
		TableColumn<BankMessage, String> descriptionCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK_MESSAGE_DESCRIPTION"),
				message -> message.getDescription(), 220, 320);
		retrievedAtCol = createDateTimeColumn(getText("UI_TABLE_BANK_MESSAGE_RETRIEVED_AT"), message -> message.getRetrievedAt(), 145);

		return List.of(versionDateCol, codeCol, typeCol, formatCol, descriptionCol, retrievedAtCol);
	}

	private TableColumn<BankMessage, LocalDateTime> createDateTimeColumn(String title,
			java.util.function.Function<BankMessage, LocalDateTime> valueProvider, double width) {
		TableColumn<BankMessage, LocalDateTime> column = new TableColumn<>(title);
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
		versionDateCol.setSortType(TableColumn.SortType.DESCENDING);
		retrievedAtCol.setSortType(TableColumn.SortType.DESCENDING);
		tableView.getSortOrder().setAll(Arrays.asList(versionDateCol, retrievedAtCol));
		tableView.sort();
	}

	public void updateModelMessages(List<BankMessage> messages) {
		replaceItems(messages != null ? messages : List.of());
		tableView.getSelectionModel().clearSelection();
	}

	@Override
	protected boolean matchesFilter(BankMessage message, String filter) {
		return matchesAny(filter, message.getBankName(), message.getCode(), formatType(message.getType()), message.getFormat(),
				message.getDescription(), message.getComments(), message.getMessage(), formatDate(message.getVersionDate()),
				formatDateTime(message.getRetrievedAt()));
	}

	private String formatDate(LocalDate date) {
		return date != null ? DateFormatUtils.formatLong(date) : "";
	}

	private String formatDateTime(LocalDateTime dateTime) {
		return dateTime != null ? DATE_TIME_FORMAT.format(dateTime) : "";
	}

	private String formatType(String type) {
		if (type == null || type.isBlank()) {
			return "";
		}
		return switch (type.trim().toUpperCase(Locale.ROOT)) {
		case "F" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_FREE_TEXT");
		case "D" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_FILE");
		case "S" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_DOCUMENT");
		case "T" -> getText("UI_LABEL_BANK_MESSAGE_TYPE_TOPIC");
		default -> type.trim();
		};
	}
}
