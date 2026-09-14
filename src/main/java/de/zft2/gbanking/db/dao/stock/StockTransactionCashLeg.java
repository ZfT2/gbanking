package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;

@StockTable(name = "stockTransactionCashLeg", parentColumn = "transaction_id", createdAt = true)
public class StockTransactionCashLeg extends StockDao {

	private int transactionId;
	private int legNumber;
	private int accountId;
	private Integer bookingId;
	private StockCashLegRole legRole;
	private long amountMinor;
	private Currency currency;
	private Integer exchangeRateId;
	private LocalDateTime valueAt;

	public int getTransactionId() { return transactionId; }
	public void setTransactionId(int transactionId) { this.transactionId = transactionId; }
	public int getLegNumber() { return legNumber; }
	public void setLegNumber(int legNumber) { this.legNumber = legNumber; }
	public int getAccountId() { return accountId; }
	public void setAccountId(int accountId) { this.accountId = accountId; }
	public Integer getBookingId() { return bookingId; }
	public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }
	public StockCashLegRole getLegRole() { return legRole; }
	public void setLegRole(StockCashLegRole legRole) { this.legRole = legRole; }
	public long getAmountMinor() { return amountMinor; }
	public void setAmountMinor(long amountMinor) { this.amountMinor = amountMinor; }
	public Currency getCurrency() { return currency; }
	public void setCurrency(Currency currency) { this.currency = currency; }
	public Integer getExchangeRateId() { return exchangeRateId; }
	public void setExchangeRateId(Integer exchangeRateId) { this.exchangeRateId = exchangeRateId; }
	public LocalDateTime getValueAt() { return valueAt; }
	public void setValueAt(LocalDateTime valueAt) { this.valueAt = valueAt; }
}
