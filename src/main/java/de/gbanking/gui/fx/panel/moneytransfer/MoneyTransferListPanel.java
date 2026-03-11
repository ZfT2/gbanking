package de.gbanking.gui.fx.panel.moneytransfer;

import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.fx.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.fx.util.TableColumnFactory;
import de.gbanking.gui.fx.util.FxTableUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;

import java.util.List;

public class MoneyTransferListPanel extends AbstractFilterableTablePanel<MoneyTransfer> {

	private final MoneyTransferDetailListTabPanel parentPanel;
	private final ObservableList<MoneyTransfer> masterData;

	public MoneyTransferListPanel(OrderType orderType, MoneyTransferDetailListTabPanel parent) {
		this(FXCollections.observableArrayList(), orderType, parent);
	}

	private MoneyTransferListPanel(ObservableList<MoneyTransfer> data, OrderType orderType, MoneyTransferDetailListTabPanel parent) {
		super(data);
		this.masterData = data;
		this.parentPanel = parent;
		createInnerMoneyTransfersListPanel(orderType);
	}

	private void createInnerMoneyTransfersListPanel(OrderType orderType) {
		setPanelTitle(orderType.getPlural());

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

		tableView.getColumns().setAll(List.of(selectedCol, dateCol, recipientCol, purposeCol, amountCol, ibanCol, bankCol));
		tableView.setFixedCellSize(60);

		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
			if (selected != null) {
				Recipient recipient = dbController.getByIdFull(Recipient.class, selected.getRecipientId());
				selected.setRecipient(recipient);
				parentPanel.getMoneyTransferInputPanel().updatePanelFieldValues(selected);
			}
		});

		reload();
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
		masterData.setAll(orderList);
	}

	public void updatePanelBorder(String borderTitle) {
		setPanelTitle(borderTitle);
	}

	public void reload() {
		masterData.setAll(dbController.getAllByParent(MoneyTransfer.class, 1));
	}

	public void refresh() {
		reload();
	}
}