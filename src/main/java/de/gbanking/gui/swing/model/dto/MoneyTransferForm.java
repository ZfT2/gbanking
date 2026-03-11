package de.gbanking.gui.swing.model.dto;

import java.math.BigDecimal;

import de.gbanking.db.dao.BankAccount;

public class MoneyTransferForm {

	private BankAccount bankAccount;
	private String recipientName;
	private String iban;
	private String bic;
	private String bank;
	private BigDecimal amount;
	private String purpose;

	public MoneyTransferForm(BankAccount bankAccount, String recipientName, String iban, String bic, String bank, BigDecimal amount, String purpose) {
		this.bankAccount = bankAccount;
		this.recipientName = recipientName;
		this.iban = iban;
		this.bic = bic;
		this.setBank(bank);
		this.amount = amount;
		this.purpose = purpose;
	}

	public BankAccount getBankAccount() {
		return bankAccount;
	}

	public void setBankAccount(BankAccount bankAccount) {
		this.bankAccount = bankAccount;
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

}
