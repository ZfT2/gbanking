package de.gbanking.db.dao.logic;

import de.gbanking.db.DaoSqlStatements;
import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.Recipient;

public class StatementsLogicRecipient extends StatementsLogicDefault<Recipient, Void> implements StatementsLogic<Recipient, Void> {
	
	@Override
	public SqlParameter getSqlParameter(Recipient rp) {
		return new SqlParameter(String.valueOf(rp.getId()),rp.getNote());
	}

	@Override
	public StatementType getStatementTypeForInsertOrUpdate(Recipient recipient) {

		int id;
		StatementType statementType = StatementType.INSERT;
		Recipient recipientDb = getById(Recipient.class, executeSelectId(DaoSqlStatements.SQL_SELECT_ID_RECIPIENT_BY_IBAN, recipient.getIban(), null));
		if (recipientDb != null) {
			id = recipientDb.getId();
		} else {
			id = 0;
		}
		if (id > 0) {
			if (executeSelectId(DaoSqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED, String.valueOf(recipientDb.getId()), null) > 0) {
				statementType = StatementType.UPDATE;
			} else {
				if (recipient.getNote() != null && !recipient.getNote().equals(recipientDb.getNote())) {
					statementType = StatementType.UPDATE_SPECIFIC_REFERENCED;
				} else {
					id = 0;
				}
			}
		}

		recipient.setId(id);

		SqlParameter sqlParameter = getSqlParameter(recipient); //StatementsParameterMapper.getSqlParameter(recipient);
		if (id > 0 && sqlParameter.isIdUpdate())
			recipient.setId(id);

		return statementType;
	}

}
