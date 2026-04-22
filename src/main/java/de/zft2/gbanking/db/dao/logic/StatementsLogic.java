package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;

public interface StatementsLogic<T extends Dao, V> {

	SqlParameter getSqlParameter(T entity);

	StatementType getStatementTypeForInsertOrUpdate(T entity);

	PreparedStatement getPreparedStatementForListUpdate(T dao, Set<Integer> targetEntityIdList, String sql) throws SQLException;

	boolean insertSpecific(T dao);

	boolean updateSpecific(T dao);

	T insertOrUpdateSingle(T entity);

	Set<T> insertAll(Set<T> entityList);

	Map<String, Integer> getTableIds(Class<T> type, String field, String optionalField);

	void addOneToManyRelations(T parentEntity, List<? extends Dao> childrenList);

	void addOneToOneRelations(T entity);
}
