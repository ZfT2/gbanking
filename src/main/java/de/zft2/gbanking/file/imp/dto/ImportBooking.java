package de.zft2.gbanking.file.imp.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

import de.zft2.core.dto.Booking;
import de.zft2.core.dto.BookingDetails;
import de.zft2.core.dto.Counterpart;
import de.zft2.core.dto.DefaultCounterpart;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;

public class ImportBooking implements BookingDetails {

	private LocalDate date;
	private LocalDate dateBooking;
	private LocalDate dateValue;
	private String purpose;
	private BigDecimal amount;
	private String currency;
	private Typ typ;
	private BookingType bookingType;
	private String accountName;
	private Counterpart counterpart;
	private Integer recipientId;
	private String crossAccountName;
	private String category;
	private String sepaCustomerRef;
	private String sepaCreditorId;
	private String sepaEndToEnd;
	private String sepaMandate;
	private String sepaPersonId;
	private String sepaPurpose;
	private SepaType sepaType;
	private BigDecimal balance;
	private String addInstref;
	private String addGvcode;
	private String addText;
	private String addPrimanota;
	private String addKey;
	private Boolean addIsStorno;
	private BigDecimal addOrigValue;
	private BigDecimal addChargeValue;
	private String addRawData;
	private Boolean addIsSepa;
	private Boolean addIsCamt;
	private BigDecimal addBankSaldo;
	private LocalDate creditcardTransactionDate;
	private String creditcardType;
	private BigDecimal creditcardCurrencyAmount;
	private BigDecimal creditcardCurrencyRate;
	private String creditcardCurrency;
	private String creditcardMerchantArea;
	private String creditcardMerchantCategory;
	private Source source;
	private LocalDate updatedAt;
	private Booking crossBooking;

	public ImportBooking() {
	}

	public ImportBooking(Booking booking) {
		this.date = booking.getDate();
		this.purpose = booking.getPurpose();
		this.amount = booking.getAmount();
		this.typ = booking.getTyp();
		this.accountName = booking.getAccountName();
		setCounterpart(booking.getCounterpart());
		this.crossAccountName = booking.getCrossAccountName();
		this.crossBooking = booking.getCrossBooking();

		if (booking instanceof BookingDetails bookingDetails) {
			this.dateBooking = bookingDetails.getDateBooking();
			this.dateValue = bookingDetails.getDateValue();
			this.sepaCustomerRef = bookingDetails.getSepaCustomerRef();
			this.sepaCreditorId = bookingDetails.getSepaCreditorId();
			this.sepaEndToEnd = bookingDetails.getSepaEndToEnd();
			this.sepaMandate = bookingDetails.getSepaMandate();
			this.sepaPersonId = bookingDetails.getSepaPersonId();
			this.sepaPurpose = bookingDetails.getSepaPurpose();
		}

		if (booking instanceof ImportBooking importBooking) {
			this.currency = importBooking.currency;
			this.bookingType = importBooking.bookingType;
			this.recipientId = importBooking.recipientId;
			this.category = importBooking.category;
			this.sepaType = importBooking.sepaType;
			this.balance = importBooking.balance;
			this.addInstref = importBooking.addInstref;
			this.addGvcode = importBooking.addGvcode;
			this.addText = importBooking.addText;
			this.addPrimanota = importBooking.addPrimanota;
			this.addKey = importBooking.addKey;
			this.addIsStorno = importBooking.addIsStorno;
			this.addOrigValue = importBooking.addOrigValue;
			this.addChargeValue = importBooking.addChargeValue;
			this.addRawData = importBooking.addRawData;
			this.addIsSepa = importBooking.addIsSepa;
			this.addIsCamt = importBooking.addIsCamt;
			this.addBankSaldo = importBooking.addBankSaldo;
			this.creditcardTransactionDate = importBooking.creditcardTransactionDate;
			this.creditcardType = importBooking.creditcardType;
			this.creditcardCurrencyAmount = importBooking.creditcardCurrencyAmount;
			this.creditcardCurrencyRate = importBooking.creditcardCurrencyRate;
			this.creditcardCurrency = importBooking.creditcardCurrency;
			this.creditcardMerchantArea = importBooking.creditcardMerchantArea;
			this.creditcardMerchantCategory = importBooking.creditcardMerchantCategory;
			this.source = importBooking.source;
			this.updatedAt = importBooking.updatedAt;
		}
	}

	@Override
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

	@Override
	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	@Override
	public String getAmountStr() {
		return amount != null ? String.format(Locale.GERMANY, "%.2f", amount.setScale(2, RoundingMode.DOWN)) : null;
	}

	@Override
	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	@Override
	public Typ getTyp() {
		if (typ != null) {
			return typ;
		}
		return toCoreTyp(bookingType);
	}

	@Override
	public void setTyp(Typ typ) {
		this.typ = typ;
	}

	public BookingType getBookingType() {
		return bookingType;
	}

	public void setBookingType(BookingType bookingType) {
		this.bookingType = bookingType;
	}

	@Override
	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	@Override
	public Counterpart getCounterpart() {
		return counterpart;
	}

	@Override
	public void setCounterpart(Counterpart counterpart) {
		this.counterpart = DefaultCounterpart.copyOf(counterpart);
	}

	@Override
	public String getCrossAccountName() {
		return crossAccountName;
	}

	@Override
	public void setCrossAccountName(String crossAccountName) {
		this.crossAccountName = crossAccountName;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
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

	public SepaType getSepaType() {
		return sepaType;
	}

	public void setSepaType(SepaType sepaType) {
		this.sepaType = sepaType;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public String getAddInstref() {
		return addInstref;
	}

	public void setAddInstref(String addInstref) {
		this.addInstref = addInstref;
	}

	public String getAddGvcode() {
		return addGvcode;
	}

	public void setAddGvcode(String addGvcode) {
		this.addGvcode = addGvcode;
	}

	public String getAddText() {
		return addText;
	}

	public void setAddText(String addText) {
		this.addText = addText;
	}

	public String getAddPrimanota() {
		return addPrimanota;
	}

	public void setAddPrimanota(String addPrimanota) {
		this.addPrimanota = addPrimanota;
	}

	public String getAddKey() {
		return addKey;
	}

	public void setAddKey(String addKey) {
		this.addKey = addKey;
	}

	public Boolean getAddIsStorno() {
		return addIsStorno;
	}

	public void setAddIsStorno(Boolean addIsStorno) {
		this.addIsStorno = addIsStorno;
	}

	public BigDecimal getAddOrigValue() {
		return addOrigValue;
	}

	public void setAddOrigValue(BigDecimal addOrigValue) {
		this.addOrigValue = addOrigValue;
	}

	public BigDecimal getAddChargeValue() {
		return addChargeValue;
	}

	public void setAddChargeValue(BigDecimal addChargeValue) {
		this.addChargeValue = addChargeValue;
	}

	public String getAddRawData() {
		return addRawData;
	}

	public void setAddRawData(String addRawData) {
		this.addRawData = addRawData;
	}

	public Boolean getAddIsSepa() {
		return addIsSepa;
	}

	public void setAddIsSepa(Boolean addIsSepa) {
		this.addIsSepa = addIsSepa;
	}

	public Boolean getAddIsCamt() {
		return addIsCamt;
	}

	public void setAddIsCamt(Boolean addIsCamt) {
		this.addIsCamt = addIsCamt;
	}

	public BigDecimal getAddBankSaldo() {
		return addBankSaldo;
	}

	public void setAddBankSaldo(BigDecimal addBankSaldo) {
		this.addBankSaldo = addBankSaldo;
	}

	public LocalDate getCreditcardTransactionDate() {
		return creditcardTransactionDate;
	}

	public void setCreditcardTransactionDate(LocalDate creditcardTransactionDate) {
		this.creditcardTransactionDate = creditcardTransactionDate;
	}

	public String getCreditcardType() {
		return creditcardType;
	}

	public void setCreditcardType(String creditcardType) {
		this.creditcardType = creditcardType;
	}

	public BigDecimal getCreditcardCurrencyAmount() {
		return creditcardCurrencyAmount;
	}

	public void setCreditcardCurrencyAmount(BigDecimal creditcardCurrencyAmount) {
		this.creditcardCurrencyAmount = creditcardCurrencyAmount;
	}

	public BigDecimal getCreditcardCurrencyRate() {
		return creditcardCurrencyRate;
	}

	public void setCreditcardCurrencyRate(BigDecimal creditcardCurrencyRate) {
		this.creditcardCurrencyRate = creditcardCurrencyRate;
	}

	public String getCreditcardCurrency() {
		return creditcardCurrency;
	}

	public void setCreditcardCurrency(String creditcardCurrency) {
		this.creditcardCurrency = creditcardCurrency;
	}

	public String getCreditcardMerchantArea() {
		return creditcardMerchantArea;
	}

	public void setCreditcardMerchantArea(String creditcardMerchantArea) {
		this.creditcardMerchantArea = creditcardMerchantArea;
	}

	public String getCreditcardMerchantCategory() {
		return creditcardMerchantCategory;
	}

	public void setCreditcardMerchantCategory(String creditcardMerchantCategory) {
		this.creditcardMerchantCategory = creditcardMerchantCategory;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Source getSource() {
		return source;
	}

	public Integer getRecipientId() {
		return recipientId;
	}

	public void setRecipientId(Integer recipientId) {
		this.recipientId = recipientId;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public LocalDate getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDate updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Booking getCrossBooking() {
		return crossBooking;
	}

	@Override
	public void setCrossBooking(Booking crossBooking) {
		this.crossBooking = crossBooking;
	}

	private Typ toCoreTyp(BookingType bookingType) {
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
}
