package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.zft2.gbanking.db.DbExecutor;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicDefault<T extends Dao> extends DbExecutor implements StatementsLogic<T> {

	@Override
	public SqlParameter getSqlParameter(T entity) {
		if (entity instanceof MnDao) {
			return new SqlParameter(null, null, false, false);
		}
		return new SqlParameter(String.valueOf(entity.getId()));
	}

	@Override
	public PreparedStatement getPreparedStatementForListUpdate(T dao, Set<Integer> targetEntityIdList, String sql) throws SQLException {
		throw new GBankingException(
				"getPreparedStatementForListUpdate(T dao, Set<Integer> targetEntityIdList, String sql): not implemented for type " + this.getClass().getName());
	}

	@Override
	public boolean insertSpecific(T dao) {
		throw new GBankingException("insertSpecific(T dao): not implemented for type " + dao.getClass().getName());
	}

	@Override
	public boolean updateSpecific(T dao) {
		throw new GBankingException("updateSpecific(T dao): not implemented for type " + dao.getClass().getName());
	}

	@Override
	public Map<String, Integer> getTableIds(Class<T> type, String field, String optionalField) {
		throw new GBankingException("getTableIds(Class<T> type, String field, String optionalField): not implemented for type " + type.getName());
	}

	@Override
	public StatementType getStatementTypeForInsertOrUpdate(T entity) {
		Class<? extends Dao> type = entity.getClass();
		StatementType statementType = StatementType.SELECT_ID;
		int id = entity.getId();
		SqlParameter sqlParameter = getSqlParameter(entity);
		if (sqlParameter.isIdLookup()) {
			String sql = StatementsConfig.getSqlStatement(type, statementType);
			List<SqlParameterValue> parameters = new ArrayList<>(2);
			parameters.add(sqlParameterValue(sqlParameter.getParam1(), java.sql.Types.VARCHAR));
			if (sqlParameter.getParam2() != null)
				parameters.add(sqlParameterValue(sqlParameter.getParam2(), java.sql.Types.VARCHAR));
			id = executeSelectId(sql, parameters);
		}
		statementType = id > 0 ? StatementType.UPDATE : StatementType.INSERT;

		if (id > 0 && sqlParameter.isIdUpdate()) {
			entity.setId(id);
		}
		return statementType;
	}


	@Override
	public T insertOrUpdateSingle(T entity) {

		StatementType statementType = getStatementTypeForInsertOrUpdate(entity);
		return executeInsertUpdateStatement(statementType, entity);
	}

	@Override
	public Set<T> insertAll(Set<T> entityList) {

		for (T entity : entityList) {
			insertOrUpdateSingle(entity);
		}

		return entityList;
	}

}
