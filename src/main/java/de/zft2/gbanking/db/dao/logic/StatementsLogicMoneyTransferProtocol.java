package de.zft2.gbanking.db.dao.logic;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;

public class StatementsLogicMoneyTransferProtocol extends StatementsLogicDefault<MoneyTransferProtocol> implements StatementsLogic<MoneyTransferProtocol> {

	@Override
	public StatementType getStatementTypeForInsertOrUpdate(MoneyTransferProtocol protocol) {
		return StatementType.INSERT;
	}
}
