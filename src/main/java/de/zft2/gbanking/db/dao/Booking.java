package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

import de.zft2.core.dto.BookingDetails;
import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;

public class Booking extends Dao implements Serializable, BookingDetails {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5006523951641613067L;

	private int accountId;
	private Integer parentBookingId;
	private LocalDate date;
	private LocalDate dateBooking;
	private LocalDate dateValue;
	private String purpose;
	private BigDecimal amount;
	private BookingSepaDetails sepaDetails;
	private BookingNoteDetails noteDetails;
	private BigDecimal balance;
	private BookingAdditionalDetails additionalDetails;
	private BookingCreditCardDetails creditCardDetails;
	private BookingForeignCurrencyDetails foreignCurrencyDetails;
	private BookingFee fee;
	private BookingType bookingType;
	private Integer crossAccountId;
	private transient Recipient recipient;
	private Category category;
	private String accountName;
	private String crossAccountName;
	private Integer categoryRuleId;
	private String categoryRuleName;
	private transient de.zft2.core.dto.Booking crossBooking;

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
		this.parentBookingId = bookingToCopy.parentBookingId;
		this.date = bookingToCopy.date;
		this.dateBooking = bookingToCopy.dateBooking;
		this.dateValue = bookingToCopy.dateValue;
		this.purpose = bookingToCopy.purpose;
		this.amount = bookingToCopy.amount;
		this.sepaDetails = bookingToCopy.sepaDetails != null ? new BookingSepaDetails(bookingToCopy.sepaDetails) : null;
		this.noteDetails = bookingToCopy.noteDetails != null ? new BookingNoteDetails(bookingToCopy.noteDetails) : null;
		this.balance = bookingToCopy.balance;
		this.additionalDetails = bookingToCopy.additionalDetails != null ? new BookingAdditionalDetails(bookingToCopy.additionalDetails) : null;
		this.creditCardDetails = bookingToCopy.creditCardDetails != null ? new BookingCreditCardDetails(bookingToCopy.creditCardDetails) : null;
		this.foreignCurrencyDetails = bookingToCopy.foreignCurrencyDetails != null
				? new BookingForeignCurrencyDetails(bookingToCopy.foreignCurrencyDetails) : null;
		this.fee = bookingToCopy.fee != null ? new BookingFee(bookingToCopy.fee) : null;
		this.bookingType = bookingToCopy.bookingType;
		this.crossAccountId = bookingToCopy.crossAccountId;
		this.recipient = bookingToCopy.recipient;
		this.category = bookingToCopy.category;
		this.categoryRuleId = bookingToCopy.categoryRuleId;
		this.categoryRuleName = bookingToCopy.categoryRuleName;
	}

	public Booking() {
	}

	public String getAmountStr() {
		return amount != null ? String.format(Locale.GERMANY, "%.2f", amount.setScale(2, RoundingMode.DOWN)) : null;
	}

	public int getAccountId() {
		return accountId;
	}

	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}

	public Integer getParentBookingId() {
		return parentBookingId;
	}

	public void setParentBookingId(Integer parentBookingId) {
		this.parentBookingId = parentBookingId;
	}

	public LocalDate getDate() {
		if (date != null) {
			return date;
		}
		return dateValue != null ? dateValue : dateBooking;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalDate getDateBooking() {
		return dateBooking;
	}

	public void setDateBooking(LocalDate dateBooking) {
		this.dateBooking = dateBooking;
		if (date == null) {
			date = dateBooking;
		}
	}

	public LocalDate getDateValue() {
		return dateValue;
	}

	public void setDateValue(LocalDate dateValue) {
		this.dateValue = dateValue;
		if (dateValue != null) {
			date = dateValue;
		}
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

	public BookingSepaDetails getSepaDetails() {
		return sepaDetails != null ? new BookingSepaDetails(sepaDetails) : null;
	}

	public void setSepaDetails(BookingSepaDetails sepaDetails) {
		this.sepaDetails = sepaDetails == null || sepaDetails.isEmpty() ? null : new BookingSepaDetails(sepaDetails);
	}

	@Override
	public String getSepaCustomerRef() {
		return sepaDetails != null ? sepaDetails.getCustomerRef() : null;
	}

	@Override
	public void setSepaCustomerRef(String sepaCustomerRef) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setCustomerRef(sepaCustomerRef);
		setSepaDetails(details);
	}

	@Override
	public String getSepaCreditorId() {
		return sepaDetails != null ? sepaDetails.getCreditorId() : null;
	}

	@Override
	public void setSepaCreditorId(String sepaCreditorId) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setCreditorId(sepaCreditorId);
		setSepaDetails(details);
	}

	@Override
	public String getSepaEndToEnd() {
		return sepaDetails != null ? sepaDetails.getEndToEnd() : null;
	}

	@Override
	public void setSepaEndToEnd(String sepaEndToEnd) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setEndToEnd(sepaEndToEnd);
		setSepaDetails(details);
	}

	@Override
	public String getSepaMandate() {
		return sepaDetails != null ? sepaDetails.getMandate() : null;
	}

	@Override
	public void setSepaMandate(String sepaMandate) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setMandate(sepaMandate);
		setSepaDetails(details);
	}

	@Override
	public String getSepaPersonId() {
		return sepaDetails != null ? sepaDetails.getPersonId() : null;
	}

	@Override
	public void setSepaPersonId(String sepaPersonId) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setPersonId(sepaPersonId);
		setSepaDetails(details);
	}

	@Override
	public String getSepaPurpose() {
		return sepaDetails != null ? sepaDetails.getPurpose() : null;
	}

	@Override
	public void setSepaPurpose(String sepaPurpose) {
		BookingSepaDetails details = getMutableSepaDetails();
		details.setPurpose(sepaPurpose);
		setSepaDetails(details);
	}

	private BookingSepaDetails getMutableSepaDetails() {
		return sepaDetails != null ? sepaDetails : new BookingSepaDetails();
	}

	public BookingNoteDetails getNoteDetails() {
		return noteDetails != null ? new BookingNoteDetails(noteDetails) : null;
	}

	public void setNoteDetails(BookingNoteDetails noteDetails) {
		this.noteDetails = noteDetails == null || noteDetails.isEmpty() ? null : new BookingNoteDetails(noteDetails);
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public BookingAdditionalDetails getAdditionalDetails() {
		return additionalDetails != null ? new BookingAdditionalDetails(additionalDetails) : null;
	}

	public void setAdditionalDetails(BookingAdditionalDetails additionalDetails) {
		this.additionalDetails = additionalDetails == null || additionalDetails.isEmpty() ? null : new BookingAdditionalDetails(additionalDetails);
	}

	public BookingCreditCardDetails getCreditCardDetails() {
		return creditCardDetails != null ? new BookingCreditCardDetails(creditCardDetails) : null;
	}

	public void setCreditCardDetails(BookingCreditCardDetails creditCardDetails) {
		this.creditCardDetails = creditCardDetails == null || creditCardDetails.isEmpty() ? null : new BookingCreditCardDetails(creditCardDetails);
	}

	public BookingForeignCurrencyDetails getForeignCurrencyDetails() {
		return foreignCurrencyDetails != null ? new BookingForeignCurrencyDetails(foreignCurrencyDetails) : null;
	}

	public void setForeignCurrencyDetails(BookingForeignCurrencyDetails foreignCurrencyDetails) {
		this.foreignCurrencyDetails = foreignCurrencyDetails == null || foreignCurrencyDetails.isEmpty() ? null
				: new BookingForeignCurrencyDetails(foreignCurrencyDetails);
	}

	public BookingFee getFee() {
		return fee != null ? new BookingFee(fee) : null;
	}

	public void setFee(BookingFee fee) {
		this.fee = fee == null || fee.isEmpty() ? null : new BookingFee(fee);
	}

	public BookingType getBookingType() {
		return bookingType;
	}

	public void setBookingType(BookingType bookingType) {
		this.bookingType = bookingType;
	}

	@Override
	public Typ getTyp() {
		return toCoreTyp(bookingType);
	}

	@Override
	public void setTyp(Typ typ) {
		this.bookingType = toBookingType(typ);
	}

	public Integer getCrossAccountId() {
		return crossAccountId;
	}

	public void setCrossAccountId(Integer crossAccountId) {
		this.crossAccountId = crossAccountId;
	}

	public Recipient getRecipient() {
		return (Recipient) getCounterpart();
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

	public Integer getCategoryRuleId() {
		return categoryRuleId;
	}

	public void setCategoryRuleId(Integer categoryRuleId) {
		this.categoryRuleId = categoryRuleId;
	}

	public String getCategoryRuleName() {
		return categoryRuleName;
	}

	public void setCategoryRuleName(String categoryRuleName) {
		this.categoryRuleName = categoryRuleName;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	@Override
	public Counterpart getCounterpart() {
		return recipient;
	}

	@Override
	public void setCounterpart(Counterpart counterpart) {
		if (counterpart == null) {
			recipient = null;
			return;
		}
		Recipient newRecipient = counterpart instanceof Recipient recipientCounterpart ? recipientCounterpart : new Recipient();
		Counterpart.copy(counterpart, newRecipient);
		if (newRecipient.getSource() == null) {
			newRecipient.setSource(source != null ? source : Source.MANUELL);
		}
		recipient = newRecipient;
	}

	@Override
	public de.zft2.core.dto.Booking getCrossBooking() {
		return crossBooking;
	}

	@Override
	public void setCrossBooking(de.zft2.core.dto.Booking crossBooking) {
		this.crossBooking = crossBooking;
		if (crossBooking == null) {
			crossBookingId = null;
		} else if (crossBooking instanceof Booking daoBooking && daoBooking.getId() > 0) {
			crossBookingId = daoBooking.getId();
		}
	}

	public Integer getCrossBookingId() {
		return crossBookingId;
	}

	public void setCrossBookingId(Integer crossBookingId) {
		this.crossBookingId = crossBookingId;
	}

	public static BookingType toBookingType(Typ typ) {
		if (typ == null || typ == Typ.UNKNOWN) {
			return null;
		}
		return switch (typ) {
		case REBOOKING_IN -> BookingType.REBOOKING_IN;
		case REBOOKING_OUT -> BookingType.REBOOKING_OUT;
		case INTEREST -> BookingType.INTEREST;
		case INTEREST_CHARGE -> BookingType.INTEREST_CHARGE;
		case CANCEL -> BookingType.CANCEL;
		default -> null;
		};
	}

	public static Typ toCoreTyp(BookingType bookingType) {
		if (bookingType == null) {
			return null;
		}
		return switch (bookingType) {
		case REBOOKING_IN -> Typ.REBOOKING_IN;
		case REBOOKING_OUT -> Typ.REBOOKING_OUT;
		case INTEREST -> Typ.INTEREST;
		case INTEREST_CHARGE -> Typ.INTEREST_CHARGE;
		case CANCEL -> Typ.CANCEL;
		default -> null;
		};
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
