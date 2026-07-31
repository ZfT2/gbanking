package de.zft2.gbanking.gui.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import java.time.ZoneId;

public class MoneyTransferForm {

	private BankAccount bankAccount;
	private OrderType orderType;
	private String recipientName;
	private String iban;
	private String bic;
	private String recipientBlz;
	private String bank;
	private BigDecimal amount;
	private String currency;
	private String purpose;
	private LocalDate executionDate;
	private Integer executionDay;
	private StandingorderMode standingorderMode;
	private MoneyTransferForeign foreignTransfer;

	public MoneyTransferForm(BankAccount bankAccount, OrderType orderType, Recipient recipient, BigDecimal amount, String purpose) {
		this.bankAccount = bankAccount;
		this.orderType = orderType;
		this.recipientName = recipient.getName();
		this.iban = recipient.getIban();
		this.bic = recipient.getBic();
		this.bank = recipient.getBank();
		this.amount = amount;
		this.purpose = purpose;
		this.currency = "EUR";
		this.executionDate = LocalDate.now(ZoneId.systemDefault());
	}

	public MoneyTransferForm(BankAccount bankAccount, OrderType orderType, Recipient recipient, BigDecimal amount, String purpose, LocalDate executionDate) {
		this(bankAccount, orderType, recipient, amount, purpose);
		this.executionDate = executionDate;
	}

	public MoneyTransferForm(BankAccount bankAccount, OrderType orderType, Recipient recipient, BigDecimal amount, String purpose, LocalDate executionDate,
			MoneyTransferForeign foreignTransfer) {
		this(bankAccount, orderType, recipient, amount, purpose, executionDate);
		this.foreignTransfer = foreignTransfer;
		this.recipientBlz = foreignTransfer != null ? foreignTransfer.getRecipientBankCode() : null;
		if (this.foreignTransfer != null) {
			if (this.foreignTransfer.getCurrency() == null) {
				this.foreignTransfer.setCurrency(currency);
			} else {
				this.currency = this.foreignTransfer.getCurrency();
			}
		}
	}

	public void setStandingorderInfo(Integer executionDay, StandingorderMode standingorderMode) {
		this.executionDay = executionDay;
		this.standingorderMode = standingorderMode;
	}

	public BankAccount getBankAccount() {
		return bankAccount;
	}

	public void setBankAccount(BankAccount bankAccount) {
		this.bankAccount = bankAccount;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public void setOrderType(OrderType orderType) {
		this.orderType = orderType;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public void setRecipientName(String recipientName) {
		this.recipientName = recipientName;
	}

	public String getIban() {
		return iban;
	}

	public void setIban(String iban) {
		this.iban = iban;
	}

	public String getBic() {
		return bic;
	}

	public void setBic(String bic) {
		this.bic = bic;
	}

	public String getRecipientBlz() {
		return recipientBlz;
	}

	public void setRecipientBlz(String recipientBlz) {
		this.recipientBlz = recipientBlz;
	}

	public String getBank() {
		return bank;
	}

	public void setBank(String bank) {
		this.bank = bank;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return foreignTransfer != null && foreignTransfer.getCurrency() != null ? foreignTransfer.getCurrency() : currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
		if (foreignTransfer != null) {
			foreignTransfer.setCurrency(currency);
		}
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public LocalDate getExecutionDate() {
		return executionDate;
	}

	public void setExecutionDate(LocalDate executionDate) {
		this.executionDate = executionDate;
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

	public MoneyTransferForeign getForeignTransfer() {
		return foreignTransfer;
	}

	public void setForeignTransfer(MoneyTransferForeign foreignTransfer) {
		this.foreignTransfer = foreignTransfer;
		if (foreignTransfer != null && currency != null && foreignTransfer.getCurrency() == null) {
			foreignTransfer.setCurrency(currency);
		}
	}

}
