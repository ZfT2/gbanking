package de.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

import de.fp32xmlextract.data.Booking.SepaTyp;
import de.gbanking.db.dao.enu.BookingType;

public class Booking extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5006523951641613067L;

	private int accountId;
	private LocalDate date;
	private LocalDate dateBooking;
	private LocalDate dateValue;
	private String purpose;
	private BigDecimal amount;
	private String currency;
	private String sepaCustomerRef;
	private String sepaCreditorId;
	private String sepaEndToEnd;
	private String sepaMandate;
	private String sepaPersonId;
	private String sepaPurpose;
	private SepaTyp sepaTyp;
	private BigDecimal balance;
	private BookingType bookingType;
	private Integer crossAccountId;
	private Recipient recipient;
	private Category category;
	private String accountName;
	private String crossAccountName;
	
	private Integer crossBookingId;

	// lazy-loading
	private int recipientId;
	private int categoryId;

	public Booking(int accountId, LocalDate dateBooking, LocalDate dateValue, String purpose, BigDecimal amount, BookingType bookingType,
			int crossAccountId) {
		this.accountId = accountId;
		this.date = dateValue != null ? dateValue : dateBooking;
		this.dateBooking = dateBooking;
		this.dateValue = dateValue;
		this.purpose = purpose;
		this.amount = amount;
		this.bookingType = bookingType;
		this.crossAccountId = crossAccountId;
	}

	public Booking(Booking bookingToCopy) {
		this.accountId = bookingToCopy.accountId;
		this.date = bookingToCopy.date;
		this.dateBooking = bookingToCopy.dateBooking;
		this.dateValue = bookingToCopy.dateValue;
		this.purpose = bookingToCopy.purpose;
		this.amount = bookingToCopy.amount;
		this.balance = bookingToCopy.balance;
		this.bookingType = bookingToCopy.bookingType;
		this.crossAccountId = bookingToCopy.crossAccountId;
		this.recipient = bookingToCopy.recipient;
	}

	public Booking() {
	}

	public String getAmountStr() {
		return String.format("%.2f", amount.setScale(2, RoundingMode.DOWN));
	}

	public int getAccountId() {
		return accountId;
	}

	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalDate getDateBooking() {
		return dateBooking;
	}

	public void setDateBooking(LocalDate dateBooking) {
		this.dateBooking = dateBooking;
	}

	public LocalDate getDateValue() {
		return dateValue;
	}

	public void setDateValue(LocalDate dateValue) {
		this.dateValue = dateValue;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getSepaCustomerRef() {
		return sepaCustomerRef;
	}

	public void setSepaCustomerRef(String sepaCustomerRef) {
		this.sepaCustomerRef = sepaCustomerRef;
	}

	public String getSepaCreditorId() {
		return sepaCreditorId;
	}

	public void setSepaCreditorId(String sepaCreditorId) {
		this.sepaCreditorId = sepaCreditorId;
	}

	public String getSepaEndToEnd() {
		return sepaEndToEnd;
	}

	public void setSepaEndToEnd(String sepaEndToEnd) {
		this.sepaEndToEnd = sepaEndToEnd;
	}

	public String getSepaMandate() {
		return sepaMandate;
	}

	public void setSepaMandate(String sepaMandate) {
		this.sepaMandate = sepaMandate;
	}

	public String getSepaPersonId() {
		return sepaPersonId;
	}

	public void setSepaPersonId(String sepaPersonId) {
		this.sepaPersonId = sepaPersonId;
	}

	public String getSepaPurpose() {
		return sepaPurpose;
	}

	public void setSepaPurpose(String sepaPurpose) {
		this.sepaPurpose = sepaPurpose;
	}

	public SepaTyp getSepaTyp() {
		return sepaTyp;
	}

	public void setSepaTyp(SepaTyp sepaTyp) {
		this.sepaTyp = sepaTyp;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public BookingType getBookingType() {
		return bookingType;
	}

	public void setBookingType(BookingType bookingType) {
		this.bookingType = bookingType;
	}

	public Integer getCrossAccountId() {
		return crossAccountId;
	}

	public void setCrossAccountId(Integer crossAccountId) {
		this.crossAccountId = crossAccountId;
	}

	public Recipient getRecipient() {
		return recipient;
	}

	public void setRecipient(Recipient recipient) {
		this.recipient = recipient;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public String getCrossAccountName() {
		return crossAccountName;
	}

	public void setCrossAccountName(String crossAccountName) {
		this.crossAccountName = crossAccountName;
	}

	public int getRecipientId() {
		return recipientId;
	}

	public void setRecipientId(int recipientId) {
		this.recipientId = recipientId;
	}

	public int getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(int categoryId) {
		this.categoryId = categoryId;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public Integer getCrossBookingId() {
		return crossBookingId;
	}

	public void setCrossBookingId(Integer crossBookingId) {
		this.crossBookingId = crossBookingId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, date, dateBooking, dateValue, purpose, crossAccountId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Booking other = (Booking) obj;
		return Objects.equals(accountId, other.accountId) && Objects.equals(amount, other.amount) && Objects.equals(date, other.date)
				&& Objects.equals(dateBooking, other.dateBooking) && Objects.equals(dateValue, other.dateValue)
				&& Objects.equals(purpose, other.purpose);
	}
}
