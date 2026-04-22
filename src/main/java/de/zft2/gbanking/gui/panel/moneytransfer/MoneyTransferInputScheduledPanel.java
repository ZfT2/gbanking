package de.zft2.gbanking.gui.panel.moneytransfer;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import javafx.scene.control.DatePicker;

public class MoneyTransferInputScheduledPanel extends MoneyTransferInputBasePanel {

	private final DatePicker executionDatePicker = new DatePicker();

	public MoneyTransferInputScheduledPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_SCHEDULED_MONEYTRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
		addFieldAbove("UI_LABEL_EXECUTION_DATE", executionDatePicker, 2, 6);
	}

	@Override
	public OrderType getOrderType() {
		return OrderType.SCHEDULED_TRANSFER;
	}

	@Override
	protected LocalDate getExecutionDate() {
		return executionDatePicker.getValue();
	}

	@Override
	protected boolean validateSpecificInput() {
		return executionDatePicker.getValue() != null;
	}

	@Override
	protected void resetSpecificFields() {
		executionDatePicker.setValue(null);
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		executionDatePicker.setValue(selectedMoneytransfer.getExecutionDate());
	}
}
