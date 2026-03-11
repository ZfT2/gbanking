package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig;
import de.gbanking.db.StatementsConfig.ResultType;

public class StatementsResultMapper {

	private StatementsResultMapper() {
	}

	public static <T, V> T toDao(Class<T> type, ResultSet rs, ResultType resultType) throws SQLException {

		AbstractDaoMapper<T, V> mapper = StatementsConfig.getMapperForDaoType(type);
		T result = null;

		result = mapper.toDao(rs);

		if (result == null) {
			result = mapper.toDao(rs, resultType);
		}

		return result;
	}

}
