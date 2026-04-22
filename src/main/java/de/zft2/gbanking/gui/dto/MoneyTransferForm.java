package de.zft2.gbanking.gui.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;

public class MoneyTransferForm {

	private BankAccount bankAccount;
	private OrderType orderType;
	private String recipientName;
	private String iban;
	private String bic;
	private String bank;
	private BigDecimal amount;
	private String purpose;
	private LocalDate executionDate;
	private Integer executionDay;
	private StandingorderMode standingorderMode;

	public MoneyTransferForm(BankAccount bankAccount, String recipientName, String iban, String bic, String bank, BigDecimal amount, String purpose) {
		this(bankAccount, OrderType.TRANSFER, recipientName, iban, bic, bank, amount, purpose, LocalDate.now(), null, null);
	}

	public MoneyTransferForm(BankAccount bankAccount, OrderType orderType, String recipientName, String iban, String bic, String bank, BigDecimal amount,
			String purpose, LocalDate executionDate, Integer executionDay, StandingorderMode standingorderMode) {
		this.bankAccount = bankAccount;
		this.orderType = orderType;
		this.recipientName = recipientName;
		this.iban = iban;
		this.bic = bic;
		this.setBank(bank);
		this.amount = amount;
		this.purpose = purpose;
		this.executionDate = executionDate;
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

}
