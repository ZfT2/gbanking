package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;

import de.zft2.gbanking.db.dao.enu.Currency;

public class BookingForeignCurrencyDetails implements Serializable {

	private static final long serialVersionUID = 1L;

	private BigDecimal foreignAmount;
	private Currency foreignCurrency;
	private BigDecimal exchangeRateToBaseCurrency;

	public BookingForeignCurrencyDetails() {
	}

	public BookingForeignCurrencyDetails(BookingForeignCurrencyDetails detailsToCopy) {
		this.foreignAmount = detailsToCopy.foreignAmount;
		this.foreignCurrency = detailsToCopy.foreignCurrency;
		this.exchangeRateToBaseCurrency = detailsToCopy.exchangeRateToBaseCurrency;
	}

	public boolean isEmpty() {
		return foreignAmount == null && foreignCurrency == null && exchangeRateToBaseCurrency == null;
	}

	public BigDecimal getForeignAmount() {
		return foreignAmount;
	}

	public void setForeignAmount(BigDecimal foreignAmount) {
		this.foreignAmount = foreignAmount;
	}

	public Currency getForeignCurrency() {
		return foreignCurrency;
	}

	public void setForeignCurrency(Currency foreignCurrency) {
		this.foreignCurrency = foreignCurrency;
	}

	public BigDecimal getExchangeRateToBaseCurrency() {
		return exchangeRateToBaseCurrency;
	}

	public void setExchangeRateToBaseCurrency(BigDecimal exchangeRateToBaseCurrency) {
		this.exchangeRateToBaseCurrency = exchangeRateToBaseCurrency;
	}
}
