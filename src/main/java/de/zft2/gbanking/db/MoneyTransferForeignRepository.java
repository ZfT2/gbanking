package de.zft2.gbanking.db;

import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;

final class MoneyTransferForeignRepository extends JdbcDaoRepository<MoneyTransferForeign> {

	MoneyTransferForeignRepository(DbSession session) {
		super(MoneyTransferForeign.class, session);
	}

	@Override
	public int delete(MoneyTransferForeign entity, StatementType statementType) throws SQLException {
		return jdbc().update(DaoSqlStatements.SQL_DELETE_MONEYTRANSFER_FOREIGN_BY_MONEYTRANSFER,
				statement -> statement.setInt(1, entity.getMoneyTransferId()));
	}
}
