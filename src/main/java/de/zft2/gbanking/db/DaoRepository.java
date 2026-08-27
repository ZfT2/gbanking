package de.zft2.gbanking.db;

import java.sql.SQLException;
import java.util.List;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.enu.StateType;

interface DaoRepository<T extends Dao> {

	Class<T> type();

	T findById(int id, ResultType resultType) throws SQLException;

	T find(Dao criteria) throws SQLException;

	List<T> findAll(Query query, Dao criteria) throws SQLException;

	T executeWrite(T entity, StatementType statementType) throws SQLException;

	int delete(T entity, StatementType statementType) throws SQLException;

	record Query(Integer parentId, StatementType statementType, StateType stateFilter, String sqlKey) {

		public Query {
			if (statementType == null) {
				throw new IllegalArgumentException("statementType must not be null");
			}
		}
	}
}
