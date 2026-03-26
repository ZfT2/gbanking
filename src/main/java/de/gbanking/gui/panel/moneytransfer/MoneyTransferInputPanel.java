package de.gbanking.gui.panel.moneytransfer;

import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.OrderType;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

public class MoneyTransferInputPanel extends MoneyTransferInputBasePanel {

	private final ComboBox<OrderType> transferTypeCombo = new ComboBox<>(FXCollections.observableArrayList(OrderType.TRANSFER, OrderType.REALTIME_TRANSFER));

	public MoneyTransferInputPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		transferTypeCombo.setValue(OrderType.TRANSFER);
		transferTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue != null) {
				getParentPanel().getMoneyTransferListPanel().reload();
				if (getParentPanel().getSelectedAccount() != null) {
					getParentPanel().getMoneyTransferListPanel()
							.updatePanelBorder(newValue.getPlural() + " " + getParentPanel().getSelectedAccount().getAccountName());
				}
			}
		});
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_MONEYTRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_TRANSFER_TYPE", transferTypeCombo, 0, 5);
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
	}

	@Override
	public OrderType getOrderType() {
		return transferTypeCombo.getValue();
	}

	@Override
	protected void resetSpecificFields() {
		transferTypeCombo.setValue(OrderType.TRANSFER);
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		transferTypeCombo.setValue(selectedMoneytransfer.getOrderType());
	}
}
