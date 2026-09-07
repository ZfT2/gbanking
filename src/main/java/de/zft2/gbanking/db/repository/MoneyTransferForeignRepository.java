package de.zft2.gbanking.db.repository;

import java.sql.SQLException;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.DbSession;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;

public final class MoneyTransferForeignRepository extends JdbcDaoRepository<MoneyTransferForeign> {

	public MoneyTransferForeignRepository(DbSession session) {
		super(MoneyTransferForeign.class, session);
	}

	@Override
	public int delete(MoneyTransferForeign entity, StatementType statementType) throws SQLException {
		return jdbc().update(DaoSqlStatements.SQL_DELETE_MONEYTRANSFER_FOREIGN_BY_MONEYTRANSFER,
				statement -> statement.setInt(1, entity.getMoneyTransferId()));
	}
}
