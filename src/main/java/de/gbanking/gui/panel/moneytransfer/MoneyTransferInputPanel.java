package de.gbanking.gui.panel.moneytransfer;

import de.gbanking.db.dao.enu.OrderType;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

public class MoneyTransferInputPanel extends MoneyTransferInputBasePanel {

	private final ComboBox<OrderType> orderTypeCombo = new ComboBox<>(FXCollections.observableArrayList(OrderType.TRANSFER, OrderType.REALTIME_TRANSFER));

	public MoneyTransferInputPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_MONEYTRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
		addFieldAbove("UI_LABEL_TRANSFER_TYPE", orderTypeCombo, 2, 6);
	}
}