package de.zft2.gbanking.db.dao;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.StandingorderMode;

public class MoneyTransferStandingorder extends Dao {

	private int moneyTransferId;
	private Integer executionDay;
	private StandingorderMode standingorderMode;
	private LocalDate executionDateNext;

	public int getMoneyTransferId() {
		return moneyTransferId;
	}

	public void setMoneyTransferId(int moneyTransferId) {
		this.moneyTransferId = moneyTransferId;
	}

	public Integer getExecutionDay() {
		return executionDay;
	}

	public void setExecutionDay(Integer executionDay) {
		this.executionDay = executionDay;
	}

	public StandingorderMode getStandingorderMode() {
		return standingorderMode;
	}

	public void setStandingorderMode(StandingorderMode standingorderMode) {
		this.standingorderMode = standingorderMode;
	}

	public LocalDate getExecutionDateNext() {
		return executionDateNext;
	}

	public void setExecutionDateNext(LocalDate executionDateNext) {
		this.executionDateNext = executionDateNext;
	}
}
