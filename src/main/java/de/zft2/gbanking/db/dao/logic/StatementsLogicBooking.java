package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.BookingMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicBooking extends StatementsLogicDefault<Booking> implements StatementsLogic<Booking> {

	private static Logger log = LogManager.getLogger(StatementsLogicBooking.class);
	
	@Override
	public SqlParameter getSqlParameter(Booking bK) {
		return new SqlParameter(null, null, false, true);
	}

	@Override
	public Booking insertOrUpdateSingle(Booking booking) {
		StatementType statementType = getStatementTypeForInsertOrUpdate(booking);
		String sql = StatementsConfig.getSqlStatement(Booking.class, statementType);
		AbstractDaoMapper<Booking, Void> mapperBase = StatementsConfig.getMapperForDaoType(Booking.class);
		BookingMapper mapper = (BookingMapper) mapperBase;
		boolean insert = statementType == StatementType.INSERT;

		try (PreparedStatement ps = connection.prepareStatement(sql, insert ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
			mapper.setParamsFull(booking, ps);
			ps.executeUpdate();
			if (insert) {
				setGeneratedDbId(booking, ps);
			}

			upsertBookingDetails(booking, mapper);
		} catch (SQLException e) {
			String errorKey = statementType == StatementType.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT;
			log.error(getText(errorKey, booking.getId()), e);
			throw new GBankingException(getText(errorKey), e);
		}

		return booking;
	}
	
	@Override
	public void addOneToOneRelations(Booking booking) {
		
		log.debug("addOneToOneRelations()");
		
		if (booking.getRecipient() == null && booking.getRecipientId() > 0) {
			booking.setRecipient(getById(Recipient.class, booking.getRecipientId()));
		}
		if (booking.getCategory() == null && booking.getCategoryId() > 0) {
			booking.setCategory(getById(Category.class, booking.getCategoryId()));
		}
	}

	private void upsertBookingDetails(Booking booking, BookingMapper mapper) throws SQLException {
		if (mapper.hasSepaData(booking)) {
			try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_SEPA)) {
				mapper.setParamsSepa(booking, ps);
				ps.executeUpdate();
			}
		} else {
			deleteBookingDetails(DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_SEPA, booking);
		}

		if (mapper.hasAdditionalData(booking)) {
			try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL)) {
				mapper.setParamsAdditional(booking, ps);
				ps.executeUpdate();
			}
		} else {
			deleteBookingDetails(DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL, booking);
		}

		if (mapper.hasAdditionalNoteData(booking)) {
			try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_NOTE)) {
				mapper.setParamsAdditionalNote(booking, ps);
				ps.executeUpdate();
			}
		} else {
			deleteBookingDetails(DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_NOTE, booking);
		}

		if (mapper.hasAdditionalCreditcardData(booking)) {
			try (PreparedStatement ps = connection.prepareStatement(DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_CREDITCARD)) {
				mapper.setParamsAdditionalCreditcard(booking, ps);
				ps.executeUpdate();
			}
		} else {
			deleteBookingDetails(DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_CREDITCARD, booking);
		}
	}

	private void deleteBookingDetails(String sql, Booking booking) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, booking.getId());
			ps.executeUpdate();
		}
	}

	private void setGeneratedDbId(Booking booking, PreparedStatement ps) throws SQLException {
		try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
			if (generatedKeys.next()) {
				booking.setId(generatedKeys.getInt(1));
				return;
			}
		}
		throw new SQLException(getText(SqlErrors.ERROR_DB_NO_ID, booking.getClass().getName()));
	}

}
