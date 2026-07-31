package de.zft2.gbanking.file.imp.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import de.zft2.core.dto.Account;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;

public class ImportBankAccount implements Account<ImportBooking> {

	private String accountName;
	private String namePP;
	private String parentAccount;
	private String iban;
	private String bic;
	private String number;
	private String subnumber;
	private String bankName;
	private String blz;
	private String ownerName;
	private String ownerName2;
	private String country;
	private String currency;
	private BigDecimal balance;
	private AccountType accountType;
	private AccountState accountState;
	private Source source;
	private boolean offlineAccount;
	private List<ImportBooking> bookings = new ArrayList<>();

	@Override
	public String getIban() {
		return iban;
	}

	@Override
	public void setIban(String iban) {
		this.iban = iban;
	}

	@Override
	public String getBic() {
		return bic;
	}

	@Override
	public void setBic(String bic) {
		this.bic = bic;
	}

	@Override
	public String getNumber() {
		return number;
	}

	@Override
	public void setNumber(String number) {
		this.number = number;
	}

	@Override
	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	@Override
	public String getNamePP() {
		return namePP != null ? namePP : accountName;
	}

	@Override
	public void setNamePP(String namePP) {
		this.namePP = namePP;
	}

	public String getParentAccount() {
		return parentAccount;
	}

	@Override
	public void setParentAccount(String parentAccount) {
		this.parentAccount = parentAccount;
	}

	@Override
	public List<ImportBooking> getBookings() {
		return bookings;
	}

	@Override
	public void setBookings(List<ImportBooking> bookings) {
		this.bookings = bookings != null ? bookings : new ArrayList<>();
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

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public AccountType getAccountType() {
		return accountType;
	}

	public void setAccountType(AccountType accountType) {
		this.accountType = accountType;
	}

	public AccountState getAccountState() {
		return accountState;
	}

	public void setAccountState(AccountState accountState) {
		this.accountState = accountState;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public boolean isOfflineAccount() {
		return offlineAccount;
	}

	public void setOfflineAccount(boolean offlineAccount) {
		this.offlineAccount = offlineAccount;
	}
}
