package de.zft2.gbanking.db.dao;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;

public class MoneyTransferProtocol extends Dao {

	private int moneyTransferId;
	private MoneyTransferStatus moneytransferStatus;
	private LocalDateTime timeStart;
	private LocalDateTime timeFinish;
	private String protocolText;

	public int getMoneyTransferId() {
		return moneyTransferId;
	}

	public void setMoneyTransferId(int moneyTransferId) {
		this.moneyTransferId = moneyTransferId;
	}

	public MoneyTransferStatus getMoneytransferStatus() {
		return moneytransferStatus;
	}

	public void setMoneytransferStatus(MoneyTransferStatus moneytransferStatus) {
		this.moneytransferStatus = moneytransferStatus;
	}

	public LocalDateTime getTimeStart() {
		return timeStart;
	}

	public void setTimeStart(LocalDateTime timeStart) {
		this.timeStart = timeStart;
	}

	public LocalDateTime getTimeFinish() {
		return timeFinish;
	}

	public void setTimeFinish(LocalDateTime timeFinish) {
		this.timeFinish = timeFinish;
	}

	public String getProtocolText() {
		return protocolText;
	}

	public void setProtocolText(String protocolText) {
		this.protocolText = protocolText;
	}
}
