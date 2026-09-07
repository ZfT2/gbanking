package de.zft2.gbanking.db.repository;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.DbSession;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Booking;

public final class BookingRepository extends JdbcDaoRepository<Booking> {

	public BookingRepository(DbSession session) {
		super(Booking.class, session);
	}

	@Override
	protected String selectByIdSql(ResultType resultType) {
		return resultType.isWithRelations()
				? DaoSqlStatements.SQL_SELECT_BOOKING_FULL_BY_ID
				: super.selectByIdSql(resultType);
	}

	@Override
	protected ResultType rowResultType(ResultType requestedResultType) {
		return requestedResultType.isWithRelations() ? ResultType.FULL : ResultType.WITHOUT_RELATIONS;
	}
}
