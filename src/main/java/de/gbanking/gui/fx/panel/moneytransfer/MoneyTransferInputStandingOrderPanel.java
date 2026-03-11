package de.gbanking.gui.fx.panel.moneytransfer;

import java.util.stream.IntStream;

import de.gbanking.db.dao.enu.StandingorderMode;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;

public class MoneyTransferInputStandingOrderPanel extends MoneyTransferInputBasePanel {

	private final ComboBox<String> dayCombo = new ComboBox<>(FXCollections.observableArrayList(IntStream.range(1, 31).mapToObj(String::valueOf).toList()));

	private final ComboBox<StandingorderMode> cycleCombo = new ComboBox<>(FXCollections.observableArrayList(StandingorderMode.values()));

	public MoneyTransferInputStandingOrderPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		dayCombo.getItems().add(getText("UI_LABEL_LAST_DAY_OF_MONTH"));
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_SAVE_STANDING_ORDER"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
		addFieldAbove("UI_LABEL_DAY", dayCombo, 1, 6);
		addFieldAbove("UI_LABEL_INTERVAL", cycleCombo, 2, 6);
	}
}