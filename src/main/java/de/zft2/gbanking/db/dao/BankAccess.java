package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.util.List;

import de.zft2.gbanking.db.dao.enu.BankAccessType;

public class BankAccess extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5537813108036780637L;

	private String bankName;
	private char[] pin;
	private boolean active;
	private BankAccessType accessType = BankAccessType.HBCI;
	private BankAccessFints fints = new BankAccessFints();
	private BankAccessPaypal paypal = new BankAccessPaypal();
	private BankAccessEnablebanking enablebanking;

	private List<BankAccount> accounts;

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public char[] getPin() {
		return pin;
	}

	public void setPin(char[] pin) {
		this.pin = pin;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public BankAccessType getAccessType() {
		return accessType;
	}

	public void setAccessType(BankAccessType accessType) {
		this.accessType = accessType;
	}

	public BankAccessFints getFints() {
		return fints;
	}

	public void setFints(BankAccessFints fints) {
		this.fints = fints != null ? fints : new BankAccessFints();
	}

	public BankAccessPaypal getPaypal() {
		return paypal;
	}

	public void setPaypal(BankAccessPaypal paypal) {
		this.paypal = paypal != null ? paypal : new BankAccessPaypal();
	}

	public BankAccessEnablebanking getEnablebanking() {
		return enablebanking;
	}

	public void setEnablebanking(BankAccessEnablebanking enablebanking) {
		this.enablebanking = enablebanking;
	}

	public List<BankAccount> getAccounts() {
		return accounts;
	}

	public void setAccounts(List<BankAccount> accounts) {
		this.accounts = accounts;
	}
}
