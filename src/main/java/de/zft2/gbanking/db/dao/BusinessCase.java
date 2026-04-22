package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.util.Objects;

public class BusinessCase extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6056585812091553004L;

	private int accountId;
	private String caseValue;

	public int getAccountId() {
		return accountId;
	}

	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}

	public String getCaseValue() {
		return caseValue;
	}

	public void setCaseValue(String caseValue) {
		this.caseValue = caseValue;
	}

	@Override
	public int hashCode() {
		return Objects.hash(caseValue);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BusinessCase other = (BusinessCase) obj;
		return Objects.equals(caseValue, other.caseValue);
	}
}
