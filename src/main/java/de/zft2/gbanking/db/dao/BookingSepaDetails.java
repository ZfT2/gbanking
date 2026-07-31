package de.zft2.gbanking.db.dao;

import java.io.Serializable;

import de.zft2.gbanking.db.dao.enu.SepaType;

public class BookingSepaDetails implements Serializable {

	private static final long serialVersionUID = 1L;

	private String customerRef;
	private String creditorId;
	private String endToEnd;
	private String mandate;
	private String personId;
	private String purpose;
	private SepaType type;

	public BookingSepaDetails() {
	}

	public BookingSepaDetails(BookingSepaDetails detailsToCopy) {
		this.customerRef = detailsToCopy.customerRef;
		this.creditorId = detailsToCopy.creditorId;
		this.endToEnd = detailsToCopy.endToEnd;
		this.mandate = detailsToCopy.mandate;
		this.personId = detailsToCopy.personId;
		this.purpose = detailsToCopy.purpose;
		this.type = detailsToCopy.type;
	}

	public boolean isEmpty() {
		return !hasText(customerRef)
				&& !hasText(creditorId)
				&& !hasText(endToEnd)
				&& !hasText(mandate)
				&& !hasText(personId)
				&& !hasText(purpose)
				&& type == null;
	}

	public String getCustomerRef() {
		return customerRef;
	}

	public void setCustomerRef(String customerRef) {
		this.customerRef = customerRef;
	}

	public String getCreditorId() {
		return creditorId;
	}

	public void setCreditorId(String creditorId) {
		this.creditorId = creditorId;
	}

	public String getEndToEnd() {
		return endToEnd;
	}

	public void setEndToEnd(String endToEnd) {
		this.endToEnd = endToEnd;
	}

	public String getMandate() {
		return mandate;
	}

	public void setMandate(String mandate) {
		this.mandate = mandate;
	}

	public String getPersonId() {
		return personId;
	}

	public void setPersonId(String personId) {
		this.personId = personId;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public SepaType getType() {
		return type;
	}

	public void setType(SepaType type) {
		this.type = type;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
