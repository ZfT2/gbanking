package de.zft2.gbanking.db.dao;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.MoneyTransferProtocolResultStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.db.dao.enu.VopResult;

public class MoneyTransferProtocol extends Dao {

	private int moneyTransferId;
	private MoneyTransferStatus moneytransferStatus;
	private LocalDateTime timeStart;
	private LocalDateTime timeFinish;
	private String bankOrderId;
	private SepaOrderStatus sepaOrderStatus;
	private SepaCancellationCode sepaCancellationCode;
	private MoneyTransferProtocolResultStatus resultStatus = MoneyTransferProtocolResultStatus.UNKNOWN;
	private boolean pinOk;
	private boolean scaRequired;
	private boolean vopRequired;
	private VopResult vopResult;
	private boolean recipientNameCorrected;
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

	public MoneyTransferProtocolResultStatus getResultStatus() {
		return resultStatus;
	}

	public void setResultStatus(MoneyTransferProtocolResultStatus resultStatus) {
		this.resultStatus = resultStatus;
	}

	public boolean isPinOk() {
		return pinOk;
	}

	public void setPinOk(boolean pinOk) {
		this.pinOk = pinOk;
	}

	public boolean isScaRequired() {
		return scaRequired;
	}

	public void setScaRequired(boolean scaRequired) {
		this.scaRequired = scaRequired;
	}

	public boolean isVopRequired() {
		return vopRequired;
	}

	public void setVopRequired(boolean vopRequired) {
		this.vopRequired = vopRequired;
	}

	public VopResult getVopResult() {
		return vopResult;
	}

	public void setVopResult(VopResult vopResult) {
		this.vopResult = vopResult;
	}

	public boolean isRecipientNameCorrected() {
		return recipientNameCorrected;
	}

	public void setRecipientNameCorrected(boolean recipientNameCorrected) {
		this.recipientNameCorrected = recipientNameCorrected;
	}

	public String getProtocolText() {
		return protocolText;
	}

	public void setProtocolText(String protocolText) {
		this.protocolText = protocolText;
	}
}
