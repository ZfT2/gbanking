package de.zft2.gbanking.db.dao;

import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;

public class MoneyTransferForeign extends Dao {

	private int moneyTransferId;
	private String currency;
	private String recipientCountry;
	private String recipientAccountNumber;
	private String recipientBankCode;
	private String recipientSubAccount;
	private String recipientAddressLine1;
	private String recipientAddressLine2;
	private String recipientBankCountry;
	private String recipientBankAddressLine1;
	private String recipientBankAddressLine2;
	private ForeignChargeBearer chargeBearer;
	private String regulatoryReporting;
	private String endToEndReference;

	public int getMoneyTransferId() {
		return moneyTransferId;
	}

	public void setMoneyTransferId(int moneyTransferId) {
		this.moneyTransferId = moneyTransferId;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getRecipientCountry() {
		return recipientCountry;
	}

	public void setRecipientCountry(String recipientCountry) {
		this.recipientCountry = recipientCountry;
	}

	public String getRecipientAccountNumber() {
		return recipientAccountNumber;
	}

	public void setRecipientAccountNumber(String recipientAccountNumber) {
		this.recipientAccountNumber = recipientAccountNumber;
	}

	public String getRecipientBankCode() {
		return recipientBankCode;
	}

	public void setRecipientBankCode(String recipientBankCode) {
		this.recipientBankCode = recipientBankCode;
	}

	public String getRecipientSubAccount() {
		return recipientSubAccount;
	}

	public void setRecipientSubAccount(String recipientSubAccount) {
		this.recipientSubAccount = recipientSubAccount;
	}

	public String getRecipientAddressLine1() {
		return recipientAddressLine1;
	}

	public void setRecipientAddressLine1(String recipientAddressLine1) {
		this.recipientAddressLine1 = recipientAddressLine1;
	}

	public String getRecipientAddressLine2() {
		return recipientAddressLine2;
	}

	public void setRecipientAddressLine2(String recipientAddressLine2) {
		this.recipientAddressLine2 = recipientAddressLine2;
	}

	public String getRecipientBankCountry() {
		return recipientBankCountry;
	}

	public void setRecipientBankCountry(String recipientBankCountry) {
		this.recipientBankCountry = recipientBankCountry;
	}

	public String getRecipientBankAddressLine1() {
		return recipientBankAddressLine1;
	}

	public void setRecipientBankAddressLine1(String recipientBankAddressLine1) {
		this.recipientBankAddressLine1 = recipientBankAddressLine1;
	}

	public String getRecipientBankAddressLine2() {
		return recipientBankAddressLine2;
	}

	public void setRecipientBankAddressLine2(String recipientBankAddressLine2) {
		this.recipientBankAddressLine2 = recipientBankAddressLine2;
	}

	public ForeignChargeBearer getChargeBearer() {
		return chargeBearer;
	}

	public void setChargeBearer(ForeignChargeBearer chargeBearer) {
		this.chargeBearer = chargeBearer;
	}

	public String getRegulatoryReporting() {
		return regulatoryReporting;
	}

	public void setRegulatoryReporting(String regulatoryReporting) {
		this.regulatoryReporting = regulatoryReporting;
	}

	public String getEndToEndReference() {
		return endToEndReference;
	}

	public void setEndToEndReference(String endToEndReference) {
		this.endToEndReference = endToEndReference;
	}
}
