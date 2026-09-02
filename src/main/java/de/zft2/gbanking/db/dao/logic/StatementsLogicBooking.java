package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.BookingMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicBooking extends StatementsLogicDefault<Booking> implements StatementsLogic<Booking> {

	private static final Logger log = LogManager.getLogger(StatementsLogicBooking.class);
	
	@Override
	public SqlParameter getSqlParameter(Booking bK) {
		return new SqlParameter(null, null, false, true);
	}

	@Override
	public Booking insertOrUpdateSingle(Booking booking) {
		StatementType statementType = getStatementTypeForInsertOrUpdate(booking);
		AbstractDaoMapper<Booking, Void> mapperBase = StatementsConfig.getMapperForDaoType(Booking.class);
		BookingMapper mapper = (BookingMapper) mapperBase;

		try {
			executeInsertUpdateStatement(statementType, booking);
			upsertBookingDetails(booking, mapper);
		} catch (SQLException exception) {
			String errorKey = statementType == StatementType.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT;
			log.error(getText(errorKey, booking.getId()), exception);
			throw new GBankingException(getText(errorKey), exception);
		}

		return booking;
	}

	@Override
	public Set<Booking> insertAll(Set<Booking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return Collections.emptySet();
		}

		AbstractDaoMapper<Booking, Void> mapperBase = StatementsConfig.getMapperForDaoType(Booking.class);
		BookingMapper mapper = (BookingMapper) mapperBase;
		String updateSql = StatementsConfig.getSqlStatement(Booking.class, StatementType.UPDATE);
		List<Booking> inserts = new ArrayList<>();
		List<Booking> updates = new ArrayList<>();
		try {
			for (Booking booking : bookings) {
				if (booking.getId() > 0) {
					updates.add(booking);
				} else {
					inserts.add(booking);
				}
			}
			if (!inserts.isEmpty()) {
				executeInsertBatchWithReservedIds(DaoSqlStatements.SQL_SELECT_MAX_BOOKING_ID,
						DaoSqlStatements.SQL_INSERT_BOOKING_BATCH, inserts,
						(booking, statement) -> mapper.setParamsInsertWithId(booking, statement));
			}
			if (!updates.isEmpty()) {
				executeCachedBatchExactlyOnce(updateSql, updates,
						(booking, statement) -> mapper.setParamsFull(booking, statement));
			}
			persistBookingDetails(bookings, updates, mapper);
			return bookings;
		} catch (SQLException exception) {
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		}
	}

	private void upsertBookingDetails(Booking booking, BookingMapper mapper) throws SQLException {
		persistSingleDetail(booking, mapper.hasSepaData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_SEPA,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_SEPA, ps -> mapper.setParamsSepa(booking, ps));
		persistSingleDetail(booking, mapper.hasAdditionalData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL, ps -> mapper.setParamsAdditional(booking, ps));
		persistSingleDetail(booking, mapper.hasAdditionalNoteData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_NOTE,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_NOTE, ps -> mapper.setParamsAdditionalNote(booking, ps));
		persistSingleDetail(booking, mapper.hasAdditionalCreditcardData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_CREDITCARD,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_CREDITCARD, ps -> mapper.setParamsAdditionalCreditcard(booking, ps));
		persistSingleDetail(booking, mapper.hasForeignCurrencyData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_FOREIGNCURRENCY,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_FOREIGNCURRENCY, ps -> mapper.setParamsForeignCurrency(booking, ps));
		persistSingleDetail(booking, mapper.hasFeeData(booking), DaoSqlStatements.SQL_INSERT_BOOKING_FEE,
				DaoSqlStatements.SQL_DELETE_BOOKING_FEE, ps -> mapper.setParamsFee(booking, ps));
	}

	private void persistSingleDetail(Booking booking, boolean hasData, String insertSql, String deleteSql,
			SqlStatementBinder parameterBinder) throws SQLException {
		if (!hasData) {
			deleteBookingDetails(deleteSql, booking);
			return;
		}
		executeCachedUpdateExactlyOnce(insertSql, parameterBinder);
	}

	private void deleteBookingDetails(String sql, Booking booking) throws SQLException {
		executeCachedUpdate(sql, statement -> statement.setInt(1, booking.getId()));
	}

	private void persistBookingDetails(Set<Booking> bookings, List<Booking> updates, BookingMapper mapper)
			throws SQLException {
		persistDetailBatch(bookings, updates, mapper::hasSepaData,
				DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_SEPA,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_SEPA,
				mapper::setParamsSepa);
		persistDetailBatch(bookings, updates, mapper::hasAdditionalData,
				DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL,
				mapper::setParamsAdditional);
		persistDetailBatch(bookings, updates, mapper::hasAdditionalNoteData,
				DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_NOTE,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_NOTE,
				mapper::setParamsAdditionalNote);
		persistDetailBatch(bookings, updates, mapper::hasAdditionalCreditcardData,
				DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_CREDITCARD,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_CREDITCARD,
				mapper::setParamsAdditionalCreditcard);
		persistDetailBatch(bookings, updates, mapper::hasForeignCurrencyData,
				DaoSqlStatements.SQL_INSERT_BOOKING_ADDITIONAL_FOREIGNCURRENCY,
				DaoSqlStatements.SQL_DELETE_BOOKING_ADDITIONAL_FOREIGNCURRENCY,
				mapper::setParamsForeignCurrency);
		persistDetailBatch(bookings, updates, mapper::hasFeeData,
				DaoSqlStatements.SQL_INSERT_BOOKING_FEE,
				DaoSqlStatements.SQL_DELETE_BOOKING_FEE,
				mapper::setParamsFee);
	}

	private void persistDetailBatch(Iterable<Booking> bookings, Iterable<Booking> updates,
			Predicate<Booking> hasDetails, String upsertSql, String deleteSql,
			SqlBatchBinder<Booking> upsertBinder) throws SQLException {
		List<Booking> upserts = matching(bookings, hasDetails);
		if (!upserts.isEmpty()) {
			executeCachedBatchExactlyOnce(upsertSql, upserts, upsertBinder);
		}
		List<Booking> deletes = matching(updates, hasDetails.negate());
		if (!deletes.isEmpty()) {
			executeCachedBatch(deleteSql, deletes,
					(booking, statement) -> statement.setInt(1, booking.getId()));
		}
	}

	private static List<Booking> matching(Iterable<Booking> bookings, Predicate<Booking> predicate) {
		List<Booking> matches = new ArrayList<>();
		for (Booking booking : bookings) {
			if (predicate.test(booking)) {
				matches.add(booking);
			}
		}
		return matches;
	}

}
