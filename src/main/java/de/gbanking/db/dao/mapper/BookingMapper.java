package de.gbanking.db.dao.mapper;

import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.fp32xmlextract.data.Booking.SepaTyp;
import de.gbanking.db.SqlFields;
import de.gbanking.db.StatementsConfig;
import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.util.TypeConverter;

public class BookingMapper extends AbstractDaoMapper<Booking, Void> {

	@Override
	public void setParamsFull(Booking booking, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, booking.getAccountId());
		ps.setDate(index++, TypeConverter.toSqlDateShort(booking.getDateBooking()));
		ps.setDate(index++, TypeConverter.toSqlDateShort(booking.getDateValue()));
		ps.setString(index++, booking.getPurpose());
		ps.setDouble(index++, booking.getAmount().doubleValue());
		ps.setString(index++, booking.getCurrency());
		ps.setString(index++, booking.getSepaCustomerRef());
		ps.setString(index++, booking.getSepaCreditorId());
		ps.setString(index++, booking.getSepaEndToEnd());
		ps.setString(index++, booking.getSepaMandate());
		ps.setString(index++, booking.getSepaPersonId());
		ps.setString(index++, booking.getSepaPurpose());
		ps.setString(index++, booking.getSepaTyp() != null ? booking.getSepaTyp().name() : null);
		ps.setString(index++, booking.getBookingType() != null ? booking.getBookingType().name() : null);
		ps.setString(index++, booking.getSource().name());

		if (booking.getCrossAccountId() != null) {
			ps.setInt(index, booking.getCrossAccountId());
		} else {
			ps.setNull(index, Types.INTEGER);
		}
		index++;
		if (booking.getRecipientId() > 0) {
			ps.setInt(index, booking.getRecipientId());
		}
		index++;
		if (booking.getCategory() != null && booking.getCategory().getId() > 0) {
			ps.setInt(index, booking.getCategory().getId());
		}
		index++;
		if (booking.getCrossBookingId() != null) {
			ps.setInt(index, booking.getCrossBookingId());
		}
		index++;
		ps.setDate(index++, TypeConverter.toSqlDateNow());
		if (booking.getId() > 0)
			ps.setInt(index, booking.getId());
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
	void setParamsForUpdateSource(Booking booking, PreparedStatement ps) throws SQLException {

		ps.setString(1, booking.getSource().name());
		ps.setString(2, TypeConverter.toTimestampStringNow());
		if (booking.getAccountId() > 0)
			ps.setInt(3, booking.getAccountId());
		ps.setInt(4, booking.getId());
	}

	@Override
	public void setParamsFull(Set<Booking> entitySet, PreparedStatement ps) throws SQLException {
		Iterator<Booking> parameterDataBookingIterator = entitySet.iterator();
		while (parameterDataBookingIterator.hasNext()) {
			Booking booking = parameterDataBookingIterator.next();
			AbstractDaoMapper<Booking, ?> mapper = StatementsConfig.getMapperForDaoType(booking.getClass());
			mapper.setParamsFull(booking, ps);
			ps.addBatch();
		}
	}

	@Override
	public Booking toDao(ResultSet rs) throws SQLException {
		//return toDao(rs, ResultType.FULL);
		return null;
	}

	@Override
	public Booking toDao(ResultSet rs, ResultType resultType) throws SQLException {
		Booking booking = new Booking();
		booking.setId(rs.getInt("id"));
		booking.setAccountId(rs.getInt(SqlFields.ACCOUNT_ACCOUNTID));
		booking.setBookingType(rs.getString(SqlFields.BOOKING_BOOKINGTYPE) != null ? BookingType.valueOf(rs.getString(SqlFields.BOOKING_BOOKINGTYPE)) : null);
		booking.setSource(Source.valueOf(rs.getString("bookingSource")));
		booking.setDateBooking(TypeConverter.toCalendarFromSqlDate(rs.getDate(SqlFields.BOOKING_DATEBOOKING)));
		booking.setDateValue(TypeConverter.toCalendarFromSqlDate(rs.getDate("dateValue")));
		booking.setPurpose(rs.getString(SqlFields.BOOKING_PURPOSE));
		booking.setAmount(rs.getBigDecimal(SqlFields.BOOKING_AMOUNT).setScale(2, RoundingMode.HALF_UP));
		booking.setCurrency(rs.getString("currency"));
		
		if (resultType == ResultType.FULL) {
			booking.setBalance(rs.getBigDecimal(SqlFields.BALANCE).setScale(2, RoundingMode.HALF_UP));
			booking.setAccountName(rs.getString("accountName"));
			booking.setCrossAccountName(rs.getString("crossAccountName"));
		}

		booking.setSepaCustomerRef(rs.getString("sepaCustomerRef"));
		booking.setSepaCreditorId(rs.getString("sepaCreditorId"));
		booking.setSepaEndToEnd(rs.getString("sepaEndToEnd"));
		booking.setSepaMandate(rs.getString("sepaMandate"));
		booking.setSepaPersonId(rs.getString("sepaPersonId"));
		booking.setSepaPurpose(rs.getString("sepaPurpose"));
		booking.setSepaTyp(rs.getString("sepaTyp") != null ? SepaTyp.valueOf(rs.getString("sepaTyp")) : null);

		booking.setCrossAccountId(rs.getInt("crossAccount_id"));
		booking.setUpdatedAt((TypeConverter.toCalendarFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));

		booking.setRecipientId(rs.getInt("recipient_id"));
		booking.setCategoryId(rs.getInt("category_id"));
		booking.setCrossBookingId(rs.getInt("crossBooking_id"));

		if (resultType.iswithAllColumns() && resultType.isWithRelations()) {
			if (booking.getRecipientId() > 0) {
				Recipient recipient = new Recipient();
				recipient.setName(rs.getString("name"));
				recipient.setAccountNumber(rs.getString("accountnumber"));
				recipient.setIban(rs.getString("iban"));
				recipient.setBic(rs.getString("bic"));
				recipient.setBlz(rs.getString("blz"));
				recipient.setBank(rs.getString("bank"));
				recipient.setSource(Source.valueOf(rs.getString("source")));
				recipient.setNote(rs.getString("note"));

				booking.setRecipient(recipient);
			}

			if (booking.getCategoryId() > 0) {
				Category category = new Category(booking.getCategoryId(), rs.getString("categoryFullName"));
				booking.setCategory(category);
			}
		}
		return booking;
	}

}
