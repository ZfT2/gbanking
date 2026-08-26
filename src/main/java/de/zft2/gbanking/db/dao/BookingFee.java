package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;

import de.zft2.gbanking.db.dao.enu.Currency;

public class BookingFee implements Serializable {

	private static final long serialVersionUID = 1L;

	private BigDecimal amount;
	private Currency currency;

	public BookingFee() {
	}

	public BookingFee(BookingFee feeToCopy) {
		this.amount = feeToCopy.amount;
		this.currency = feeToCopy.currency;
	}

	public boolean isEmpty() {
		return amount == null && currency == null;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}
}
