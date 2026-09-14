package de.zft2.gbanking.gui.panel.bankaccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.util.DateFormatUtils;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

final class BankMessageListPanel extends AbstractFilterableTablePanel<BankMessage> {

	private final Consumer<BankMessage> selectionHandler;
	private TableColumn<BankMessage, LocalDate> versionDateCol;
	private TableColumn<BankMessage, LocalDateTime> retrievedAtCol;

	BankMessageListPanel(Consumer<BankMessage> selectionHandler) {
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
		retrievedAtCol = TableColumnFactory.createDateTimeColumn(getText("UI_TABLE_BANK_MESSAGE_RETRIEVED_AT"), message -> message.getRetrievedAt(), 145);

		return List.of(versionDateCol, codeCol, typeCol, formatCol, descriptionCol, retrievedAtCol);
	}

	private void configureDefaultSorting() {
		versionDateCol.setSortType(TableColumn.SortType.DESCENDING);
		retrievedAtCol.setSortType(TableColumn.SortType.DESCENDING);
		tableView.getSortOrder().setAll(Arrays.asList(versionDateCol, retrievedAtCol));
		tableView.sort();
	}

	void updateModelMessages(List<BankMessage> messages) {
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
		return DateFormatUtils.formatDateTime(dateTime);
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
