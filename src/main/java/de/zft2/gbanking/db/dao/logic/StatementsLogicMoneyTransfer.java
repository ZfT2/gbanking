package de.zft2.gbanking.db.dao.logic;

import de.zft2.gbanking.db.dao.MoneyTransfer;

public class StatementsLogicMoneyTransfer extends StatementsLogicDefault<MoneyTransfer, Void> implements StatementsLogic<MoneyTransfer, Void> {

	@Override
	public SqlParameter getSqlParameter(MoneyTransfer mt) {
		return new SqlParameter(String.valueOf(mt.getId()), String.valueOf(mt.getAccountId()));
	}
}
