package de.gbanking.gui.panel.moneytransfer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.IntStream;

import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.OrderType;
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
		buttonSubmit.setText(getText("UI_BUTTON_STANDING_ORDER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_SENDER_ACCOUNT", tfAccountSender, 0, 6);
		addFieldAbove("UI_LABEL_DAY", dayCombo, 1, 6);
		addFieldAbove("UI_LABEL_INTERVAL", cycleCombo, 0, 7);
	}

	@Override
	public OrderType getOrderType() {
		return OrderType.STANDING_ORDER;
	}

	@Override
	protected LocalDate getExecutionDate() {
		Integer executionDay = getExecutionDay();
		if (executionDay == null) {
			return null;
		}

		LocalDate today = LocalDate.now();
		int dayOfMonth = Math.min(executionDay, YearMonth.from(today).lengthOfMonth());
		LocalDate nextExecutionDate = today.withDayOfMonth(dayOfMonth);
		if (nextExecutionDate.isBefore(today)) {
			YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
			nextExecutionDate = nextMonth.atDay(Math.min(executionDay, nextMonth.lengthOfMonth()));
		}
		return nextExecutionDate;
	}

	@Override
	protected Integer getExecutionDay() {
		String selectedDay = dayCombo.getValue();
		if (selectedDay == null) {
			return null;
		}
		if (selectedDay.equals(getText("UI_LABEL_LAST_DAY_OF_MONTH"))) {
			return 31;
		}
		return Integer.valueOf(selectedDay);
	}

	@Override
	protected StandingorderMode getStandingorderMode() {
		return cycleCombo.getValue();
	}

	@Override
	protected boolean validateSpecificInput() {
		return dayCombo.getValue() != null && cycleCombo.getValue() != null;
	}

	@Override
	protected void resetSpecificFields() {
		dayCombo.getSelectionModel().clearSelection();
		cycleCombo.getSelectionModel().clearSelection();
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		cycleCombo.setValue(selectedMoneytransfer.getStandingorderMode());
		Integer executionDay = selectedMoneytransfer.getExecutionDay();
		if (executionDay == null) {
			dayCombo.getSelectionModel().clearSelection();
		} else if (executionDay >= 31) {
			dayCombo.setValue(getText("UI_LABEL_LAST_DAY_OF_MONTH"));
		} else {
			dayCombo.setValue(String.valueOf(executionDay));
		}
	}
}
