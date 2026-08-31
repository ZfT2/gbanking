package de.zft2.gbanking.db.dao;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;

public class MoneyTransferProtocol extends Dao {

	private int moneyTransferId;
	private MoneyTransferStatus moneytransferStatus;
	private LocalDateTime timeStart;
	private LocalDateTime timeFinish;
	private String bankOrderId;
	private SepaOrderStatus sepaOrderStatus;
	private SepaCancellationCode sepaCancellationCode;
	private String protocolText;

	public MoneyTransferProtocol() {
	}

	public MoneyTransferProtocol(int moneyTransferId, MoneyTransferStatus moneytransferStatus, LocalDateTime timeStart,
			LocalDateTime timeFinish) {
		this.moneyTransferId = moneyTransferId;
		this.moneytransferStatus = moneytransferStatus;
		this.timeStart = timeStart;
		this.timeFinish = timeFinish;
	}

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

	public String getBankOrderId() {
		return bankOrderId;
	}

	public void setBankOrderId(String bankOrderId) {
		this.bankOrderId = bankOrderId;
	}

	public SepaOrderStatus getSepaOrderStatus() {
		return sepaOrderStatus;
	}

	public void setSepaOrderStatus(SepaOrderStatus sepaOrderStatus) {
		this.sepaOrderStatus = sepaOrderStatus;
	}

	public SepaCancellationCode getSepaCancellationCode() {
		return sepaCancellationCode;
	}

	public void setSepaCancellationCode(SepaCancellationCode sepaCancellationCode) {
		this.sepaCancellationCode = sepaCancellationCode;
	}

	public String getProtocolText() {
		return protocolText;
	}

	public void setProtocolText(String protocolText) {
		this.protocolText = protocolText;
	}
}
