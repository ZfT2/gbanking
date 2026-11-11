package de.zft2.gbanking.db.dao;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;

public class MoneyTransfer extends Dao {

	private int accountId;
	private OrderType orderType;
	private int recipientId;
	private String purpose;
	private String purposeCode;
	private String endToEndId;
	private BigDecimal amount;
	private String currency;
	private LocalDate executionDate;
	private MoneyTransferStatus moneytransferStatus;
	private String bankOrderId;
	private SepaOrderStatus sepaOrderStatus;
	private Integer historyorderId;
	private MoneyTransferForeign foreignTransfer;
	private MoneyTransferStandingorder standingorder;
	
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

	public String getPurposeCode() {
		return purposeCode;
	}

	public void setPurposeCode(String purposeCode) {
		this.purposeCode = purposeCode;
	}

	public String getEndToEndId() {
		return endToEndId;
	}

	public void setEndToEndId(String endToEndId) {
		this.endToEndId = endToEndId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return foreignTransfer != null && foreignTransfer.getCurrency() != null ? foreignTransfer.getCurrency().name() : currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
		if (foreignTransfer != null) {
			foreignTransfer.setCurrency(Currency.forCode(currency));
		}
	}

	public LocalDate getExecutionDate() {
		return executionDate;
	}

	public void setExecutionDate(LocalDate executionDate) {
		this.executionDate = executionDate;
	}

	public Integer getExecutionDay() {
		return standingorder != null ? standingorder.getExecutionDay() : null;
	}

	public void setExecutionDay(Integer executionDay) {
		if (executionDay != null || standingorder != null) {
			standingorderDetails().setExecutionDay(executionDay);
		}
	}

	public MoneyTransferStatus getMoneytransferStatus() {
		return moneytransferStatus;
	}

	public void setMoneytransferStatus(MoneyTransferStatus moneytransferStatus) {
		this.moneytransferStatus = moneytransferStatus;
	}

	public StandingorderMode getStandingorderMode() {
		return standingorder != null ? standingorder.getStandingorderMode() : null;
	}

	public void setStandingorderMode(StandingorderMode standingorderMode) {
		if (standingorderMode != null || standingorder != null) {
			standingorderDetails().setStandingorderMode(standingorderMode);
		}
	}

	public LocalDate getExecutionDateNext() {
		return standingorder != null ? standingorder.getExecutionDateNext() : null;
	}

	public void setExecutionDateNext(LocalDate executionDateNext) {
		if (executionDateNext != null || standingorder != null) {
			standingorderDetails().setExecutionDateNext(executionDateNext);
		}
	}

	public MoneyTransferStandingorder getStandingorder() {
		return standingorder;
	}

	public void setStandingorder(MoneyTransferStandingorder standingorder) {
		this.standingorder = standingorder;
	}

	private MoneyTransferStandingorder standingorderDetails() {
		if (standingorder == null) {
			standingorder = new MoneyTransferStandingorder();
		}
		return standingorder;
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

	public Integer getHistoryorderId() {
		return historyorderId;
	}

	public void setHistoryorderId(Integer historyorderId) {
		this.historyorderId = historyorderId;
	}

	public MoneyTransferForeign getForeignTransfer() {
		return foreignTransfer;
	}

	public void setForeignTransfer(MoneyTransferForeign foreignTransfer) {
		this.foreignTransfer = foreignTransfer;
		if (foreignTransfer != null && currency != null && foreignTransfer.getCurrency() == null) {
			foreignTransfer.setCurrency(Currency.forCode(currency));
		}
	}

	public Recipient getRecipient() {
		return recipient;
	}

	public void setRecipient(Recipient recipient) {
		this.recipient = recipient;
	}
}
