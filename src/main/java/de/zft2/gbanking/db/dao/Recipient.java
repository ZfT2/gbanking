package de.zft2.gbanking.db.dao;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.Locale;
import java.util.Objects;

import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.db.dao.enu.Source;

public class Recipient extends Dao implements Counterpart {

	private String name;
	private String iban;
	private String bic;
	private String accountNumber;
	private String blz;
	private String bank;
	private String note;
	private boolean defaultRecipient;

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

	public Recipient(String recipientName, String recipientIban, String recipientBic) {
		this.name = recipientName;
		this.iban = recipientIban;
		this.bic = recipientBic;
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

	@Override
	public String getBankName() {
		return getBank();
	}

	@Override
	public void setBankName(String bankName) {
		setBank(bankName);
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public boolean isDefault() {
		return defaultRecipient;
	}

	public void setDefault(boolean defaultRecipient) {
		this.defaultRecipient = defaultRecipient;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(caseInsensitiveValue(name), caseInsensitiveValue(iban), caseInsensitiveValue(bic), trimToNull(accountNumber),
				trimToNull(blz), caseInsensitiveValue(bank));
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
		return hasSameDataExceptBank(other)
				&& Objects.equals(caseInsensitiveValue(bank), caseInsensitiveValue(other.bank));
	}

	public boolean hasSameDataWithCompatibleBank(Recipient other) {
		if (other == null || !hasSameDataExceptBank(other)) {
			return false;
		}
		String normalizedBank = caseInsensitiveValue(bank);
		String otherBank = caseInsensitiveValue(other.bank);
		return normalizedBank == null || otherBank == null || normalizedBank.equals(otherBank);
	}

	public boolean hasSameAccountIdentifier(Recipient other) {
		if (other == null) {
			return false;
		}
		String normalizedIban = caseInsensitiveValue(iban);
		String otherIban = caseInsensitiveValue(other.iban);
		if (normalizedIban != null && normalizedIban.equals(otherIban)) {
			return true;
		}
		String normalizedAccountNumber = trimToNull(accountNumber);
		return normalizedAccountNumber != null
				&& normalizedAccountNumber.equals(trimToNull(other.accountNumber))
				&& hasSameBankIdentifier(other);
	}

	public boolean hasAccountIdentifier() {
		return caseInsensitiveValue(iban) != null || trimToNull(accountNumber) != null;
	}

	private boolean hasSameDataExceptBank(Recipient other) {
		return Objects.equals(caseInsensitiveValue(name), caseInsensitiveValue(other.name))
				&& Objects.equals(caseInsensitiveValue(iban), caseInsensitiveValue(other.iban))
				&& Objects.equals(caseInsensitiveValue(bic), caseInsensitiveValue(other.bic))
				&& Objects.equals(trimToNull(accountNumber), trimToNull(other.accountNumber))
				&& Objects.equals(trimToNull(blz), trimToNull(other.blz));
	}

	private boolean hasSameBankIdentifier(Recipient other) {
		String normalizedBlz = trimToNull(blz);
		String normalizedBic = caseInsensitiveValue(bic);
		boolean sameBlz = normalizedBlz != null && normalizedBlz.equals(trimToNull(other.blz));
		boolean sameBic = normalizedBic != null && normalizedBic.equals(caseInsensitiveValue(other.bic));
		return sameBlz || sameBic;
	}

	private static String caseInsensitiveValue(String value) {
		String normalizedValue = trimToNull(value);
		return normalizedValue != null ? normalizedValue.toLowerCase(Locale.ROOT) : null;
	}

}
