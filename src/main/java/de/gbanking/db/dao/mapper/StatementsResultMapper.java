package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig;
import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Dao;

public class StatementsResultMapper {

	private StatementsResultMapper() {
	}

	public static <T extends Dao, V> Dao toDao(Class<T> type, ResultSet rs, ResultType resultType) throws SQLException {

		AbstractDaoMapper<T, V> mapper = StatementsConfig.getMapperForDaoType(type);
		T result = null;
		// result = type.newInstance();
		result = mapper.initResultDao(type, rs);

		mapper.mapDao(result, rs);

		/* if (result == null) { */
		if (type == Booking.class) {
			mapper.mapDao(result, resultType, rs);
		}

		return result;
	}

}
