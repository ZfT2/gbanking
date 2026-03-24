package de.gbanking.db.dao;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.StandingorderMode;

public class MoneyTransfer extends Dao {

	private int accountId;
	private OrderType orderType;
	private int recipientId;
	private String purpose;
	private BigDecimal amount;
	private LocalDate executionDate;
	private MoneyTransferStatus moneytransferStatus;
	private StandingorderMode standingorderMode;
	private Integer historyorderId;
	
	private Recipient recipient;
	
	public MoneyTransfer() {
		super();
	}
	
	public MoneyTransfer(int accountId, OrderType orderType, int recipientId, String purpose, BigDecimal amount, LocalDate executionDate, MoneyTransferStatus moneytransferStatus) {
		super();
		this.accountId = accountId;
		this.orderType = orderType;
		this.setRecipientId(recipientId);
		this.purpose = purpose;
		this.amount = amount;
		this.executionDate = executionDate;
		this.moneytransferStatus = moneytransferStatus;
	}

	public int getAccountId() {
		return accountId;
	}

	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public void setOrderType(OrderType orderType) {
		this.orderType = orderType;
	}

	public int getRecipientId() {
		return recipientId;
	}

	public void setRecipientId(int recipientId) {
		this.recipientId = recipientId;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public LocalDate getExecutionDate() {
		return executionDate;
	}

	public void setExecutionDate(LocalDate executionDate) {
		this.executionDate = executionDate;
	}

	public MoneyTransferStatus getMoneytransferStatus() {
		return moneytransferStatus;
	}

	public void setMoneytransferStatus(MoneyTransferStatus moneytransferStatus) {
		this.moneytransferStatus = moneytransferStatus;
	}

	public StandingorderMode getStandingorderMode() {
		return standingorderMode;
	}

	public void setStandingorderMode(StandingorderMode standingorderMode) {
		this.standingorderMode = standingorderMode;
	}

	public Integer getHistoryorderId() {
		return historyorderId;
	}

	public void setHistoryorderId(Integer historyorderId) {
		this.historyorderId = historyorderId;
	}

	public Recipient getRecipient() {
		return recipient;
	}

	public void setRecipient(Recipient recipient) {
		this.recipient = recipient;
	}
}
