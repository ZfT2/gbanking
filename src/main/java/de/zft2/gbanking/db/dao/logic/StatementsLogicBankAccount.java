package de.zft2.gbanking.db.dao.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicBankAccount extends StatementsLogicDefault<BankAccount> implements StatementsLogic<BankAccount> {

	@Override
	public SqlParameter getSqlParameter(BankAccount bC) {
		if (bC.getId() > 0 || bC.getIban() == null || bC.getNumber() == null) {
			return new SqlParameter(null, null, false, false);
		}
		return new SqlParameter(bC.getIban(), bC.getNumber());
	}

	@Override
	public Map<String, Integer> getTableIds(Class<BankAccount> type, String field, String optionalField) {
		if (type != BankAccount.class) {
			throw new GBankingException("Unsupported DAO type for bank account identifiers: " + type);
		}
		String sql = getTableIdsSql(field, optionalField);
		return executeSqlSelectStatementForMap(sql, null, "identifier", String.class, "id", Integer.class);
	}

	private String getTableIdsSql(String field, String optionalField) {
		if (SqlFields.ACCOUNT_ACCOUNTNAME.equals(field) && optionalField == null) {
			return DaoSqlStatements.SQL_SELECT_BANKACCOUNT_IDS_BY_ACCOUNT_NAME;
		}
		if ("iban".equals(field) && "number".equals(optionalField)) {
			return DaoSqlStatements.SQL_SELECT_BANKACCOUNT_IDS_BY_IBAN_OR_NUMBER;
		}
		throw new GBankingException("Unsupported bank account identifier fields: " + field + ", " + optionalField);
	}

	@Override
	public boolean insertSpecific(BankAccount bankAccount) {

		boolean result = true;

		List<BusinessCase> businessCaseListDB = getAll(BusinessCase.class);

		List<BusinessCase> businessCaseListNewFromAccount = new ArrayList<>();
		for (BusinessCase accountBusinessCase : bankAccount.getAllowedBusinessCases()) {
			if (!businessCaseListDB.contains(accountBusinessCase)) {
				businessCaseListNewFromAccount.add(accountBusinessCase);
			}
		}

		if (!businessCaseListNewFromAccount.isEmpty()) {
			insertBusinessCases(businessCaseListNewFromAccount);
		}

		Map<String, Integer> businessCaseMapDB = getBusinessCasesMap();

		insertAccountBusinessCases(bankAccount, businessCaseMapDB);
		return result;
	}

	private void insertBusinessCases(List<BusinessCase> businessCaseList) {

		executeStatementList(StatementType.INSERT, new HashSet<BusinessCase>(businessCaseList));
	}

	private Map<String, Integer> getBusinessCasesMap() {

		Map<String, Integer> businessCaseMapDB = null;

		businessCaseMapDB = executeSqlSelectStatementForMap(DaoSqlStatements.SQL_SELECT_ALL_BUSINESSCASES, null, "caseValue", String.class, "id", Integer.class);

		return businessCaseMapDB;
	}

	private void insertAccountBusinessCases(BankAccount bankAccount, Map<String, Integer> businessCaseMapDB) {

		Set<Integer> businessCaseIdList = new HashSet<>();
		for (BusinessCase businessCaseAcc : bankAccount.getAllowedBusinessCases()) {
			businessCaseIdList.add(businessCaseMapDB.get(businessCaseAcc.getCaseValue()));
		}

		executeStatementList(DaoSqlStatements.SQL_INSERT_BANKACCOUNT_BUSINESSCASE, businessCaseIdList, bankAccount, MnDao.class);
	}
	
}
