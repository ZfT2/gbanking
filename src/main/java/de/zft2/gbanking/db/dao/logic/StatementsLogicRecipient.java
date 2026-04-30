package de.zft2.gbanking.db.dao.logic;

import java.util.LinkedHashMap;
import java.util.Map;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.SourceGroup;

public class StatementsLogicRecipient extends StatementsLogicDefault<Recipient, Void> implements StatementsLogic<Recipient, Void> {
	
	@Override
	public SqlParameter getSqlParameter(Recipient rp) {
		return new SqlParameter(String.valueOf(rp.getId()),rp.getNote());
	}

	@Override
	public StatementType getStatementTypeForInsertOrUpdate(Recipient recipient) {

		int id;
		StatementType statementType = StatementType.INSERT;

		Map<Object, Integer> paramMap = new LinkedHashMap<>();
		StringBuilder sqlBuilder = new StringBuilder(DaoSqlStatements.SQL_SELECT_ID_RECIPIENT_BY_ARGS);

		boolean hasParams = false;

		hasParams |= addParam(sqlBuilder, paramMap, "iban", recipient.getIban(), java.sql.Types.VARCHAR);
		hasParams |= addParam(sqlBuilder, paramMap, "accountNumber", recipient.getAccountNumber(), java.sql.Types.VARCHAR);
		if (recipient.getSource().getGroup() == SourceGroup.GROUP_IMPORT) {
			hasParams |= addParam(sqlBuilder, paramMap, "name", recipient.getName(), java.sql.Types.VARCHAR);
			hasParams |= addParam(sqlBuilder, paramMap, "blz", recipient.getBlz(), java.sql.Types.VARCHAR);
			hasParams |= addParam(sqlBuilder, paramMap, "bic", recipient.getBic(), java.sql.Types.VARCHAR);
		}

		Recipient recipientDb = hasParams ? getById(Recipient.class, executeSelectId(sqlBuilder.toString(), paramMap)) : null;
		if (recipientDb != null) {
			id = recipientDb.getId();
		} else {
//			if (hasParams) {
//				/* check if any unreferenced recipient (with accountNr or iban) is there. */
//				hasParams = false;
//				sqlBuilder.setLength(0);
//				sqlBuilder.append(DaoSqlStatements.SQL_SELECT_ID_RECIPIENT_BY_ARGS);
//				paramMap.clear();
//				hasParams |= addParam(sqlBuilder, paramMap, "iban", recipient.getIban(), java.sql.Types.VARCHAR);
//				hasParams |= addParam(sqlBuilder, paramMap, "accountNumber", recipient.getAccountNumber(), java.sql.Types.VARCHAR);
//				recipientDb = hasParams ? getById(Recipient.class, executeSelectId(sqlBuilder.toString(), paramMap)) : null;
//				if (recipientDb != null) {
//					paramMap.clear();
//					paramMap.put(recipientDb.getId(), java.sql.Types.INTEGER);
//					id = executeSelectId(DaoSqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED, paramMap);
//				} else {
//					id = 0;
//				}
//			} else {
				id = 0;
//			}
		}
		if (id > 0) {
			paramMap.clear();
			paramMap.put(recipientDb.getId(), java.sql.Types.INTEGER);
			if (executeSelectId(DaoSqlStatements.SQL_SELECT_RECIPIENT_BY_ID_IF_NOT_REFERENCED, paramMap) > 0) {
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
