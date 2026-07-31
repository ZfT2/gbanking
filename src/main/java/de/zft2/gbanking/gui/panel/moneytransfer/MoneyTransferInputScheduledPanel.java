package de.zft2.gbanking.gui.panel.moneytransfer;

import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import javafx.scene.Node;
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
		addFieldAbove("UI_LABEL_EXECUTION_DATE", executionDatePicker, 0, 5, 2);
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
	protected String validateSpecificInput() {
		return executionDatePicker.getValue() != null ? null : requiredFieldMissingMessage("UI_LABEL_EXECUTION_DATE");
	}

	@Override
	protected void resetSpecificFields() {
		executionDatePicker.setValue(null);
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		executionDatePicker.setValue(selectedMoneytransfer.getExecutionDate());
	}

	@Override
	protected List<Node> getSpecificCapabilityNodes() {
		return List.of(executionDatePicker);
	}
}
