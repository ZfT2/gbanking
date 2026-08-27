package de.zft2.gbanking.db;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Booking;

final class BookingRepository extends JdbcDaoRepository<Booking> {

	BookingRepository(DbSession session) {
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
