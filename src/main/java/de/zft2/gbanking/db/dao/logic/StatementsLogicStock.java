package de.zft2.gbanking.db.dao.logic;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.stock.StockDao;

public final class StatementsLogicStock<T extends StockDao> extends StatementsLogicDefault<T> {

	@Override
	public StatementType getStatementTypeForInsertOrUpdate(T entity) {
		if (entity.getId() <= 0) {
			return StatementType.INSERT;
		}
		return getById(daoType(entity), entity.getId()) == null ? StatementType.INSERT : StatementType.UPDATE;
	}

	@SuppressWarnings("unchecked")
	private Class<T> daoType(T entity) {
		return (Class<T>) entity.getClass();
	}
}
