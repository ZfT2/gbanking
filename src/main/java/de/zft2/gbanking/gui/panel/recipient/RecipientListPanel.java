package de.zft2.gbanking.gui.panel.recipient;

import java.util.List;

import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.zft2.gbanking.gui.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.zft2.gbanking.gui.panel.overview.RecipientOverviewPanel;
import de.zft2.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class RecipientListPanel extends AbstractFilterableTablePanel<Recipient> {

	private static final String TITLE_KEY = "UI_PANEL_RECIPIENTS_BOOK";

	private final Object parentPanel;
	private final PageContext pageContext;

	public RecipientListPanel(Object parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		this.pageContext = parentPanel instanceof MoneyTransferDetailListTabPanel ? PageContext.ACCOUNTS_MONEYTRANSFERS : PageContext.RECIPIENTS;
		createInnerRecipientListPanel();
	}

	private void createInnerRecipientListPanel() {
		setPanelTitleByKey(TITLE_KEY);
		setColumns(createColumns());
		configureTableLayout("recipients." + pageContext.name());
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<Recipient, ?>> createColumns() {
		boolean compact = pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS;
		TableColumn<Recipient, Boolean> selectedCol = createSelectAllSelectionColumn(
				recipient -> recipient.isSelected(), (recipient, selected) -> recipient.setSelected(selected));
		TableColumn<Recipient, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_NAME"), Recipient::getName, 160, 200);
		TableColumn<Recipient, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_LABEL_IBAN_OR_ACCOUNT_NUMBER"),
				RecipientListPanel::recipientAccountIdentifier, compact ? 150 : 220, compact ? 170 : 240);
		TableColumn<Recipient, String> bankCodeCol = TableColumnFactory.createFixedTextColumn(getText("UI_LABEL_BIC_OR_BLZ"),
				RecipientListPanel::recipientBankCode, compact ? 90 : 110);

		if (compact) {
			TableColumn<Recipient, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), Recipient::getBank, 130, 160);
			return List.of(selectedCol, nameCol, accountCol, bankCodeCol, bankCol);
		}

		TableColumn<Recipient, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), Recipient::getBank, 150, 180);
		TableColumn<Recipient, java.time.LocalDate> updatedCol = TableColumnFactory.createUpdatedAtColumn(getText("UI_TABLE_UPDATED_AT"),
				Recipient::getUpdatedAt, 90);

		return List.of(selectedCol, nameCol, accountCol, bankCodeCol, bankCol, updatedCol);
	}

	static String recipientAccountIdentifier(Recipient recipient) {
		return firstNonBlank(recipient.getIban(), recipient.getAccountNumber());
	}

	static String recipientBankCode(Recipient recipient) {
		return firstNonBlank(recipient.getBic(), recipient.getBlz());
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void handleSelection(Recipient selectedRecipient) {
		if (pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS) {
			MoneyTransferDetailListTabPanel parent = (MoneyTransferDetailListTabPanel) parentPanel;
			MoneyTransferInputBasePanel moneyTransferInputPanel = parent.getMoneyTransferInputPanel();
			moneyTransferInputPanel.updatePanelFieldValues(selectedRecipient);
			return;
		}

		RecipientOverviewPanel parent = (RecipientOverviewPanel) parentPanel;
		parent.getRecipientDetailPanel().updatePanelFieldValues(selectedRecipient);
	}

	@Override
	protected boolean matchesFilter(Recipient recipient, String filter) {
		return matchesAny(filter, recipient.getName(), recipient.getIban(), recipient.getBank(), recipient.getBic(), recipient.getAccountNumber(),
				recipient.getBlz());
	}

	public void reload() {
		List<Recipient> recipients = dbController.getAll(Recipient.class, "SQL_SELECT_ALL_RECIPIENTS_ORDERED_MT");
		replaceItems(recipients);
		updatePanelTitle(recipients.size());
	}

	private void updatePanelTitle(int recipientCount) {
		setPanelTitle(getText(TITLE_KEY) + " (" + recipientCount + ")");
	}


	public void refresh() {
		reload();
	}
}
