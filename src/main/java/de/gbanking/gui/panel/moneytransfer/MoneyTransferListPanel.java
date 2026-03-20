package de.gbanking.gui.panel.moneytransfer;

import java.util.List;

import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class MoneyTransferListPanel extends AbstractFilterableTablePanel<MoneyTransfer> {

	private final MoneyTransferDetailListTabPanel parentPanel;

	public MoneyTransferListPanel(OrderType orderType, MoneyTransferDetailListTabPanel parent) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parent;
		createInnerMoneyTransfersListPanel(orderType);
	}

	private void createInnerMoneyTransfersListPanel(OrderType orderType) {
		setPanelTitle(orderType.getPlural());
		setColumns(createColumns());
		tableView.setFixedCellSize(60);
		onSelection(this::handleSelection);
		reload();
	}

	private List<TableColumn<MoneyTransfer, ?>> createColumns() {
		TableColumn<MoneyTransfer, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), MoneyTransfer::isSelected,
				MoneyTransfer::setSelected);
		TableColumn<MoneyTransfer, String> dateCol = TableColumnFactory.createCalendarDateColumn(getText("UI_TABLE_DATE"), MoneyTransfer::getExecutionDate, 95);
		TableColumn<MoneyTransfer, String> recipientCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_RECIPIENT"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getName() : "", 180, 220);
		TableColumn<MoneyTransfer, String> purposeCol = TableColumnFactory.createWrappedTextColumn(getText("UI_TABLE_PURPOSE"), MoneyTransfer::getPurpose, 260,
				420);
		TableColumn<MoneyTransfer, String> amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"),
				transfer -> transfer.getAmount() != null ? transfer.getAmount().toString() : "", 110);
		TableColumn<MoneyTransfer, String> ibanCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_IBAN"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getIban() : "", 220, 240);
		TableColumn<MoneyTransfer, String> bankCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_BANK"),
				transfer -> transfer.getRecipient() != null ? transfer.getRecipient().getBank() : "", 150, 180);

		return List.of(selectedCol, dateCol, recipientCol, purposeCol, amountCol, ibanCol, bankCol);
	}

	private void handleSelection(MoneyTransfer selected) {
		Recipient recipient = dbController.getByIdFull(Recipient.class, selected.getRecipientId());
		selected.setRecipient(recipient);
		parentPanel.getMoneyTransferInputPanel().updatePanelFieldValues(selected);
	}

	@Override
	protected boolean matchesFilter(MoneyTransfer transfer, String filter) {
		if (filter.isBlank()) {
			return true;
		}

		String recipientName = transfer.getRecipient() != null ? transfer.getRecipient().getName() : "";
		String iban = transfer.getRecipient() != null ? transfer.getRecipient().getIban() : "";
		String bank = transfer.getRecipient() != null ? transfer.getRecipient().getBank() : "";

		return contains(recipientName, filter) || contains(transfer.getPurpose(), filter) || contains(iban, filter) || contains(bank, filter);
	}

	public void updateModelMoneytransfer(List<MoneyTransfer> orderList) {
		replaceItems(orderList);
	}

	public void updatePanelBorder(String borderTitle) {
		setPanelTitle(borderTitle);
	}

	public void reload() {
		replaceItems(dbController.getAllByParent(MoneyTransfer.class, 1));
	}

	public void refresh() {
		reload();
	}
}