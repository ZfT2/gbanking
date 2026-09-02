package de.zft2.gbanking.db;

import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.exception.GBankingException;

final class ReadOnlyDaoRepository<T extends Dao> extends JdbcDaoRepository<T> {

	ReadOnlyDaoRepository(Class<T> type, DbSession session) {
		super(type, session);
	}

	@Override
	public T executeWrite(T entity, StatementType statementType) throws SQLException {
		throw new GBankingException("Repository is read-only for DAO type: " + type().getName());
	}

	@Override
	public int delete(T entity, StatementType statementType) throws SQLException {
		throw new GBankingException("Repository is read-only for DAO type: " + type().getName());
	}
}
