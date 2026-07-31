package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class BookingCreditCardDetails implements Serializable {

	private static final long serialVersionUID = 1L;

	private LocalDate transactionDate;
	private String type;
	private BigDecimal currencyAmount;
	private BigDecimal currencyRate;
	private String currency;
	private String merchantArea;
	private String merchantCategory;

	public BookingCreditCardDetails() {
	}

	public BookingCreditCardDetails(BookingCreditCardDetails detailsToCopy) {
		this.transactionDate = detailsToCopy.transactionDate;
		this.type = detailsToCopy.type;
		this.currencyAmount = detailsToCopy.currencyAmount;
		this.currencyRate = detailsToCopy.currencyRate;
		this.currency = detailsToCopy.currency;
		this.merchantArea = detailsToCopy.merchantArea;
		this.merchantCategory = detailsToCopy.merchantCategory;
	}

	public boolean isEmpty() {
		return transactionDate == null
				&& !hasText(type)
				&& currencyAmount == null
				&& currencyRate == null
				&& !hasText(currency)
				&& !hasText(merchantArea)
				&& !hasText(merchantCategory);
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(LocalDate transactionDate) {
		this.transactionDate = transactionDate;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public BigDecimal getCurrencyAmount() {
		return currencyAmount;
	}

	public void setCurrencyAmount(BigDecimal currencyAmount) {
		this.currencyAmount = currencyAmount;
	}

	public BigDecimal getCurrencyRate() {
		return currencyRate;
	}

	public void setCurrencyRate(BigDecimal currencyRate) {
		this.currencyRate = currencyRate;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getMerchantArea() {
		return merchantArea;
	}

	public void setMerchantArea(String merchantArea) {
		this.merchantArea = merchantArea;
	}

	public String getMerchantCategory() {
		return merchantCategory;
	}

	public void setMerchantCategory(String merchantCategory) {
		this.merchantCategory = merchantCategory;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
