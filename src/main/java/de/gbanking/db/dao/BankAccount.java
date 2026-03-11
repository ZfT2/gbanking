package de.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.AccountType;

public class BankAccount extends Dao implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1896883783278517707L;

	private Integer bankAccessId;
	private String accountName;
	private AccountType accountType;
	private String currency;
	private String iban;
	private String bic;
	private String number;
	private String subnumber;
	private String bankName;
	private String blz;
	private int hbciAccountType;
	private String accountLimit;
	private String customerid;
	private String ownerName;
	private String ownerName2;
	private String country;
	private String creditorid;
	private boolean isSEPAAccount;
	private boolean isOfflineAccount;
	private AccountState accountState;
	private BigDecimal balance;

	private List<BusinessCase> allowedBusinessCases;
	private List<Booking> bookings;

	public String getDefaultAccountName() {
		return bankName + " - " + accountType + " - " + (iban != null ? iban : number);
	}

	public String getAccountName() {
		if (accountName != null) {
			return accountName;
		}
		return null;
		/* return getDefaultAccountName(); */
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public Integer getBankAccessId() {
		return bankAccessId;
	}

	public void setBankAccessId(Integer bankAccessId) {
		this.bankAccessId = bankAccessId;
	}

	public AccountType getAccountType() {
		return accountType;
	}

	public void setAccountType(AccountType accountType) {
		this.accountType = accountType;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
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

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public String getSubnumber() {
		return subnumber;
	}

	public void setSubnumber(String subnumber) {
		this.subnumber = subnumber;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getBlz() {
		return blz;
	}

	public void setBlz(String blz) {
		this.blz = blz;
	}

	public int getHbciAccountType() {
		return hbciAccountType;
	}

	public void setHbciAccountType(int hbciAccountType) {
		this.hbciAccountType = hbciAccountType;
	}

	public String getLimit() {
		return accountLimit;
	}

	public void setLimit(String limit) {
		this.accountLimit = limit;
	}

	public String getCustomerid() {
		return customerid;
	}

	public void setCustomerid(String customerid) {
		this.customerid = customerid;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	public String getOwnerName2() {
		return ownerName2;
	}

	public void setOwnerName2(String ownerName2) {
		this.ownerName2 = ownerName2;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getCreditorid() {
		return creditorid;
	}

	public void setCreditorid(String creditorid) {
		this.creditorid = creditorid;
	}

	public boolean isSEPAAccount() {
		return isSEPAAccount;
	}

	public void setSEPAAccount(boolean isSEPAAccount) {
		this.isSEPAAccount = isSEPAAccount;
	}

	public boolean isOfflineAccount() {
		return isOfflineAccount;
	}

	public void setOfflineAccount(boolean isOfflineAccount) {
		this.isOfflineAccount = isOfflineAccount;
	}

	public AccountState getAccountState() {
		return accountState;
	}

	public void setAccountState(AccountState accountState) {
		this.accountState = accountState;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public List<BusinessCase> getAllowedBusinessCases() {
		return allowedBusinessCases;
	}

	public void setAllowedBusinessCases(List<BusinessCase> allowedBusinessCases) {
		this.allowedBusinessCases = allowedBusinessCases;
	}

	public List<Booking> getBookings() {
		return bookings;
	}

	public void setBookings(List<Booking> bookings) {
		this.bookings = bookings;
	}

	@Override
	public String toString() {
		return accountName;
	}

}
