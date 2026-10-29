package de.zft2.gbanking.gui.panel.moneytransfer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.gui.util.FormControlUtils;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

final class MoneyTransferInputStandingOrderPanel extends MoneyTransferInputBasePanel {

	private static final int LAST_DAY_OF_MONTH = 31;
	private final ComboBox<Integer> dayCombo = new ComboBox<>(FXCollections.observableArrayList(IntStream.rangeClosed(1, LAST_DAY_OF_MONTH).boxed().toList()));
	private final ComboBox<StandingorderMode> cycleCombo = new ComboBox<>(FXCollections.observableArrayList(StandingorderMode.values()));

	MoneyTransferInputStandingOrderPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		dayCombo.setConverter(FormControlUtils.displayConverter(
				day -> day == LAST_DAY_OF_MONTH ? getText("UI_LABEL_LAST_DAY_OF_MONTH") : day.toString()));
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_STANDING_ORDER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_DAY", dayCombo, 0, 5);
		addFieldAbove("UI_LABEL_INTERVAL", cycleCombo, 1, 5, 2);
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

		LocalDate today = LocalDate.now(ZoneId.systemDefault());
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
		return dayCombo.getValue();
	}

	@Override
	protected StandingorderMode getStandingorderMode() {
		return cycleCombo.getValue();
	}

	@Override
	protected String validateSpecificInput() {
		if (dayCombo.getValue() == null) {
			return requiredFieldMissingMessage("UI_LABEL_DAY");
		}
		if (cycleCombo.getValue() == null) {
			return requiredFieldMissingMessage("UI_LABEL_INTERVAL");
		}
		return null;
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
		dayCombo.setValue(executionDay != null ? Math.min(executionDay, LAST_DAY_OF_MONTH) : null);
	}

	@Override
	protected List<Node> getSpecificCapabilityNodes() {
		return List.of(dayCombo, cycleCombo);
	}
}
