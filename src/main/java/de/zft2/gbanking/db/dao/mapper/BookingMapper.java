package de.zft2.gbanking.db.dao.mapper;

import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.BookingCreditCardDetails;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.TypeConverter;

public class BookingMapper extends AbstractDaoMapper<Booking, Void> {

	@Override
	public void setParamsFull(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, booking.getAccountId());
		index = setIntegerNullable(index, booking.getParentBookingId(), ps);
		ps.setDate(index++, TypeConverter.toSqlDateShort(booking.getDateBooking()));
		ps.setDate(index++, TypeConverter.toSqlDateShort(booking.getDateValue()));
		ps.setString(index++, booking.getPurpose());
		ps.setDouble(index++, booking.getAmount().doubleValue());
		setEnumNullable(index++, booking.getBookingType(), ps);
		ps.setInt(index++, booking.getSource().getDbStateId());

		index = setIntegerNullable(index, booking.getCrossAccountId(), ps);
		index = setIntegerNullable(index, booking.getRecipientId(), ps);
		Integer categoryId = resolveCategoryId(booking);
		index = setIntegerNullable(index, categoryId, ps);
		index = setIntegerNullable(index, booking.getCategoryRuleId(), ps);
		index = setIntegerNullable(index, booking.getCrossBookingId(), ps);

		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (booking.getId() > 0)
			ps.setInt(index, booking.getId());
	}

	public void setParamsSepa(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;
		BookingSepaDetails details = Objects.requireNonNull(booking.getSepaDetails(), "sepaDetails");

		ps.setInt(index++, booking.getId());
		ps.setString(index++, details.getCustomerRef());
		ps.setString(index++, details.getCreditorId());
		ps.setString(index++, details.getEndToEnd());
		ps.setString(index++, details.getMandate());
		ps.setString(index++, details.getPersonId());
		ps.setString(index++, details.getPurpose());
		index = setEnumNullable(index, details.getType(), ps);
		ps.setTimestamp(index, TypeConverter.toSqlTimestampNow());
	}

	public void setParamsAdditional(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;
		BookingAdditionalDetails details = Objects.requireNonNull(booking.getAdditionalDetails(), "additionalDetails");

		ps.setInt(index++, booking.getId());
		ps.setString(index++, details.getInstref());
		ps.setString(index++, details.getGvcode());
		ps.setString(index++, details.getText());
		ps.setString(index++, details.getPrimanota());
		ps.setString(index++, details.getKey());
		index = setBooleanNullable(index, details.getStorno(), ps);
		ps.setString(index++, details.getRawData());
		index = setBooleanNullable(index, details.getSepa(), ps);
		index = setBooleanNullable(index, details.getCamt(), ps);
		index = setBigDecimalNullable(index, details.getBankSaldo(), ps);
		ps.setTimestamp(index, TypeConverter.toSqlTimestampNow());
	}

	public void setParamsAdditionalNote(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;
		BookingNoteDetails details = Objects.requireNonNull(booking.getNoteDetails(), "noteDetails");
		ps.setInt(index++, booking.getId());
		ps.setString(index++, details.getNote());
		ps.setBoolean(index++, details.isReviewRequired());
		ps.setTimestamp(index, TypeConverter.toSqlTimestampNow());
	}

	public void setParamsAdditionalCreditcard(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;
		BookingCreditCardDetails details = Objects.requireNonNull(booking.getCreditCardDetails(), "creditCardDetails");

		ps.setInt(index++, booking.getId());
		ps.setDate(index++, TypeConverter.toSqlDateShort(details.getTransactionDate()));
		ps.setString(index++, details.getType());
		ps.setString(index++, details.getMerchantArea());
		ps.setString(index++, details.getMerchantCategory());
		ps.setTimestamp(index, TypeConverter.toSqlTimestampNow());
	}

	public void setParamsForeignCurrency(Booking booking, PreparedStatement ps) throws SQLException {
		BookingForeignCurrencyDetails details = Objects.requireNonNull(booking.getForeignCurrencyDetails(), "foreignCurrencyDetails");
		ps.setInt(1, booking.getId());
		setBigDecimalNullable(2, details.getForeignAmount(), ps);
		ps.setInt(3, details.getForeignCurrency().getDbStateId());
		setBigDecimalNullable(4, details.getExchangeRateToBaseCurrency(), ps);
		ps.setTimestamp(5, TypeConverter.toSqlTimestampNow());
	}

	public void setParamsFee(Booking booking, PreparedStatement ps) throws SQLException {
		BookingFee fee = Objects.requireNonNull(booking.getFee(), "fee");
		ps.setInt(1, booking.getId());
		setBigDecimalNullable(2, fee.getAmount(), ps);
		ps.setInt(3, fee.getCurrency().getDbStateId());
		ps.setTimestamp(4, TypeConverter.toSqlTimestampNow());
	}

	public boolean hasSepaData(Booking booking) {
		return booking.getSepaDetails() != null && !booking.getSepaDetails().isEmpty();
	}

	public boolean hasAdditionalData(Booking booking) {
		return booking.getAdditionalDetails() != null && !booking.getAdditionalDetails().isEmpty();
	}

	public boolean hasAdditionalNoteData(Booking booking) {
		BookingNoteDetails details = booking.getNoteDetails();
		return details != null && !details.isEmpty();
	}

	public boolean hasAdditionalCreditcardData(Booking booking) {
		return booking.getCreditCardDetails() != null && !booking.getCreditCardDetails().isEmpty();
	}

	public boolean hasForeignCurrencyData(Booking booking) {
		return booking.getForeignCurrencyDetails() != null && !booking.getForeignCurrencyDetails().isEmpty();
	}

	public boolean hasFeeData(Booking booking) {
		return booking.getFee() != null && !booking.getFee().isEmpty();
	}

	@Override
	public <W> void setParamsForUpdateSimpleField(List<Booking> entitySet, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		for (Booking booking : entitySet) {
			setParamsForUpdateSimpleField(booking, typeToUpdate, ps);
		}
	}

	@Override
	public <V> void setParamsForUpdateSimpleField(Booking booking, Class<V> typeToUpdate, PreparedStatement ps) throws SQLException {

		setParamsForUpdateSource(booking, ps);
	}

	@Override
	public int setParamsSpecific(Booking booking, StatementType statementType, int parameterIndex, PreparedStatement ps) throws SQLException {
		if (statementType == StatementType.SELECT_WITH_PARENT_AND_DATE_RANGE) {
			ps.setDate(parameterIndex++, TypeConverter.toSqlDateShort(booking.getDateBooking()));
			ps.setDate(parameterIndex++, TypeConverter.toSqlDateShort(booking.getDateValue()));
		}
		return parameterIndex;
	}

	@Override
	void setParamsForUpdateSource(Booking booking, PreparedStatement ps) throws SQLException {

		ps.setInt(1, booking.getSource().getDbStateId());
		ps.setDate(2, TypeConverter.toSqlDateNow());
		if (booking.getAccountId() > 0)
			ps.setInt(3, booking.getAccountId());
		ps.setInt(4, booking.getId());
	}

	@Override
	public void mapDao(Booking booking, ResultType resultType, ResultSet rs) throws SQLException {
		booking.setAccountId(rs.getInt(SqlFields.ACCOUNT_ACCOUNTID));
		booking.setParentBookingId(getIntegerNullable("parentBooking_id", rs));
		booking.setBookingType(getEnumNullable(SqlFields.BOOKING_BOOKINGTYPE, BookingType.class, rs));
		booking.setSource(Source.forInt(rs.getInt("bookingSource")));
		booking.setDateBooking(TypeConverter.toLocalDateFromSqlDate(rs.getDate(SqlFields.BOOKING_DATEBOOKING)));
		booking.setDateValue(TypeConverter.toLocalDateFromSqlDate(rs.getDate("dateValue")));
		booking.setPurpose(rs.getString(SqlFields.BOOKING_PURPOSE));
		booking.setAmount(rs.getBigDecimal(SqlFields.BOOKING_AMOUNT).setScale(2, RoundingMode.HALF_UP));

		if (resultType == ResultType.FULL) {
			booking.setAccountName(rs.getString("accountName"));
			booking.setCrossAccountName(rs.getString("crossAccountName"));
		}

		booking.setSepaDetails(mapSepaDetails(rs));
		booking.setNoteDetails(mapNoteDetails(rs));
		booking.setAdditionalDetails(mapAdditionalDetails(rs));
		booking.setCreditCardDetails(mapCreditCardDetails(rs));
		booking.setForeignCurrencyDetails(mapForeignCurrencyDetails(rs));
		booking.setFee(mapFee(rs));

		booking.setCrossAccountId(getIntegerNullable("crossAccount_id", rs));

		booking.setRecipientId(rs.getInt("recipient_id"));
		booking.setCategoryId(rs.getInt("category_id"));
		booking.setCategoryRuleId(getIntegerNullable("categoryRule_id", rs));
		if (hasColumn(rs, "categoryRuleName")) {
			booking.setCategoryRuleName(rs.getString("categoryRuleName"));
		}
		booking.setCrossBookingId(getIntegerNullable("crossBooking_id", rs));

		if (resultType.isWithAllColumns() && resultType.isWithRelations()) {
			if (booking.getRecipientId() > 0) {
				Recipient recipient = new Recipient();
				recipient.setId(booking.getRecipientId());
				recipient.setName(rs.getString("recipientName"));
				recipient.setAccountNumber(rs.getString("recipientAccountNumber"));
				recipient.setIban(rs.getString("recipientIban"));
				recipient.setBic(rs.getString("recipientBic"));
				recipient.setBlz(rs.getString("recipientBlz"));
				recipient.setBank(rs.getString("recipientBank"));
				recipient.setSource(Source.forInt(rs.getInt("recipientSource")));
				recipient.setNote(rs.getString("recipientNote"));
				recipient.setDefault(rs.getBoolean("recipientIsDefault"));
				recipient.setUpdatedAt(TypeConverter.toLocalDateFromSqlDate(rs.getDate("recipientUpdatedAt")));

				booking.setRecipient(recipient);
			}

			if (booking.getCategoryId() > 0) {
				Category category = new Category(booking.getCategoryId(), rs.getString("categoryFullName"));
				category.setName(rs.getString("categoryName"));
				category.setParentId(getIntegerNullable("categoryParentId", rs));
				category.setUpdatedAt(TypeConverter.toLocalDateFromSqlDate(rs.getDate("categoryUpdatedAt")));
				booking.setCategory(category);
			}
		}
	}

	private BookingSepaDetails mapSepaDetails(ResultSet rs) throws SQLException {
		BookingSepaDetails details = new BookingSepaDetails();
		details.setCustomerRef(rs.getString("sepaCustomerRef"));
		details.setCreditorId(rs.getString("sepaCreditorId"));
		details.setEndToEnd(rs.getString("sepaEndToEnd"));
		details.setMandate(rs.getString("sepaMandate"));
		details.setPersonId(rs.getString("sepaPersonId"));
		details.setPurpose(rs.getString("sepaPurpose"));
		details.setType(getEnumNullable("sepaTyp", SepaType.class, rs));
		return details;
	}

	private BookingNoteDetails mapNoteDetails(ResultSet rs) throws SQLException {
		BookingNoteDetails details = new BookingNoteDetails();
		details.setNote(rs.getString("bookingNote"));
		details.setReviewRequired(rs.getBoolean("bookingReviewRequired"));
		return details;
	}

	private BookingAdditionalDetails mapAdditionalDetails(ResultSet rs) throws SQLException {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref(rs.getString("addInstref"));
		details.setGvcode(rs.getString("addGvcode"));
		details.setText(rs.getString("addText"));
		details.setPrimanota(rs.getString("addPrimanota"));
		details.setKey(rs.getString("addKey"));
		details.setStorno(getBooleanNullable("addIsStorno", rs));
		details.setRawData(rs.getString("addRawData"));
		details.setSepa(getBooleanNullable("addIsSepa", rs));
		details.setCamt(getBooleanNullable("addIsCamt", rs));
		details.setBankSaldo(getScaledBigDecimalNullable("addBankSaldo", rs));
		return details;
	}

	private BookingCreditCardDetails mapCreditCardDetails(ResultSet rs) throws SQLException {
		BookingCreditCardDetails details = new BookingCreditCardDetails();
		details.setTransactionDate(TypeConverter.toLocalDateFromSqlDate(rs.getDate("creditcardTransactionDate")));
		details.setType(rs.getString("creditcardType"));
		details.setMerchantArea(rs.getString("creditcardMerchantArea"));
		details.setMerchantCategory(rs.getString("creditcardMerchantCategory"));
		return details;
	}

	private BookingForeignCurrencyDetails mapForeignCurrencyDetails(ResultSet rs) throws SQLException {
		BookingForeignCurrencyDetails details = new BookingForeignCurrencyDetails();
		details.setForeignAmount(getBigDecimalNullable("foreignAmount", rs));
		details.setForeignCurrency(getEnumNullable("foreignCurrency", Currency.class, rs));
		details.setExchangeRateToBaseCurrency(getBigDecimalNullable("exchangeRateToBaseCurrency", rs));
		return details;
	}

	private BookingFee mapFee(ResultSet rs) throws SQLException {
		BookingFee fee = new BookingFee();
		fee.setAmount(getBigDecimalNullable("feeAmount", rs));
		fee.setCurrency(getEnumNullable("feeCurrency", Currency.class, rs));
		return fee;
	}

	private java.math.BigDecimal getScaledBigDecimalNullable(String field, ResultSet rs) throws SQLException {
		java.math.BigDecimal value = getBigDecimalNullable(field, rs);
		return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
	}

	private Integer resolveCategoryId(Booking booking) {
		if (booking.getCategory() != null && booking.getCategory().getId() > 0) {
			return booking.getCategory().getId();
		}
		return booking.getCategoryId() > 0 ? booking.getCategoryId() : null;
	}

}
