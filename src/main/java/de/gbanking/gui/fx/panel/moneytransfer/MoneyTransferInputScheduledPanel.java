package de.gbanking.gui.fx.panel.moneytransfer;

import javafx.scene.control.TextField;

public class MoneyTransferInputScheduledPanel extends MoneyTransferInputBasePanel {

	private final TextField tfExecutionDate = new TextField();

	public MoneyTransferInputScheduledPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_SCHEDULED_MONEYTRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
		addFieldAbove("UI_LABEL_EXECUTION_DATE", tfExecutionDate, 2, 6);
	}
}