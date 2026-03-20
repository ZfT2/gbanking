package de.gbanking.gui.panel.recipient;

import java.util.List;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.gbanking.gui.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.gbanking.gui.panel.overview.RecipientOverviewPanel;
import de.gbanking.gui.util.DateFormatUtils;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class RecipientListPanel extends AbstractFilterableTablePanel<Recipient> {

	private final Object parentPanel;
	private final PageContext pageContext;

	public RecipientListPanel(Object parentPanel) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parentPanel;
		this.pageContext = parentPanel instanceof MoneyTransferDetailListTabPanel ? PageContext.ACCOUNTS_MONEYTRANSFERS : PageContext.RECIPIENTS;
		createInnerRecipientListPanel();
	}

	private void createInnerRecipientListPanel() {
		setPanelTitleByKey("UI_PANEL_RECIPIENTS_BOOK");
		setColumns(createColumns());
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<Recipient, ?>> createColumns() {
		TableColumn<Recipient, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Recipient::isSelected,
				Recipient::setSelected);
		TableColumn<Recipient, String> nameCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_NAME"), Recipient::getName, 160, 200);
		TableColumn<Recipient, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"), Recipient::getIban, 220, 240);

		if (pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS) {
			TableColumn<Recipient, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), Recipient::getBank, 150, 180);
			return List.of(selectedCol, nameCol, ibanCol, bankCol);
		}

		TableColumn<Recipient, String> accountNoCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_ACCOUNT_NUMBER"), Recipient::getAccountNumber,
				120);
		TableColumn<Recipient, String> bicCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BIC"), Recipient::getBic, 110);
		TableColumn<Recipient, String> blzCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_BLZ"), Recipient::getBlz, 90);
		TableColumn<Recipient, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"), Recipient::getBank, 150, 180);
		TableColumn<Recipient, String> updatedCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_UPDATED_AT"),
				recipient -> DateFormatUtils.formatShort(recipient.getUpdatedAt()), 90);

		return List.of(selectedCol, nameCol, ibanCol, accountNoCol, bicCol, blzCol, bankCol, updatedCol);
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
		if (filter.isBlank()) {
			return true;
		}

		return contains(recipient.getName(), filter) || contains(recipient.getIban(), filter) || contains(recipient.getBank(), filter)
				|| contains(recipient.getBic(), filter) || contains(recipient.getAccountNumber(), filter) || contains(recipient.getBlz(), filter);
	}

	public void reload() {
		replaceItems(dbController.getAllFull(Recipient.class));
	}

	public void refresh() {
		reload();
	}
}