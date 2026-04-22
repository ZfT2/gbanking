package de.zft2.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Dao;

public class StatementsResultMapper {

	private StatementsResultMapper() {
	}

	public static <T extends Dao, V> Dao toDao(Class<T> type, ResultSet rs, ResultType resultType) throws SQLException {

		AbstractDaoMapper<T, V> mapper = StatementsConfig.getMapperForDaoType(type);

		T result = mapper.initResultDao(type, rs);
		mapper.mapDao(result, resultType, rs);

		return result;
	}

}
