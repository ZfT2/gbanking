package de.gbanking.db.dao;

import java.util.Objects;

import de.gbanking.db.dao.enu.Source;

public class Recipient extends Dao {

	private String name;
	private String iban;
	private String bic;
	private String accountNumber;
	private String blz;
	private String bank;
	private String note;

	public Recipient(String name, String iban, String bic, String accountNumber, String blz, String bank, Source source) {
		super();
		this.name = name;
		this.iban = iban;
		this.bic = bic;
		this.accountNumber = accountNumber;
		this.blz = blz;
		this.bank = bank;
		this.source = source;
	}

	public Recipient(String recipientName, String recipientIban) {
		this.name = recipientName;
		this.iban = recipientIban;
	}
	
	public Recipient() {
		super();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getBlz() {
		return blz;
	}

	public void setBlz(String blz) {
		this.blz = blz;
	}

	public String getBank() {
		return bank;
	}

	public void setBank(String bank) {
		this.bank = bank;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(iban, accountNumber, bic, blz, name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Recipient other = (Recipient) obj;
		return (iban == null && other.iban == null || iban != null && iban.equals(other.iban))
				&& (accountNumber == null && other.accountNumber == null || accountNumber != null && accountNumber.equals(other.accountNumber))
				&& (bic == null && other.bic == null || bic != null && bic.equals(other.bic))
				&& (blz == null && other.blz == null || blz != null && blz.equals(other.blz))
				&& (name == null && other.name == null || name != null && name.equalsIgnoreCase(other.name));
	}

}
