package de.zft2.gbanking.db.dao.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Dao;

public class StatementsLogicBankAccount extends StatementsLogicDefault<BankAccount, Void> implements StatementsLogic<BankAccount, Void> {

	private static Logger log = LogManager.getLogger(StatementsLogicBankAccount.class);
	
	@Override
	public SqlParameter getSqlParameter(BankAccount bC) {
		if (bC.getId() > 0) {
			return new SqlParameter(null, null, false, false);
		}
		return new SqlParameter(bC.getIban(), bC.getNumber());
	}

	@Override
	public Map<String, Integer> getTableIds(Class<BankAccount> type, String field, String optionalField) {

		Map<String, Integer> idMap;

		String statement = "SELECT id, " + field + " AS identifier FROM " + StatementsConfig.getTableViewName(type) + " WHERE identifier IS NOT NULL ";
		if (optionalField != null) {
			statement = statement + "UNION SELECT id, " + optionalField + " AS identifier FROM " + StatementsConfig.getTableViewName(type) + " WHERE identifier IS NOT NULL";
		}
		log.debug("getTableIds: {}", statement);
		idMap = executeSqlSelectStatementForMap(statement, null, "identifier", String.class, "id", Integer.class);

		return idMap;
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
	
	@Override
	public void addOneToManyRelations(BankAccount bankAccount, List<? extends Dao> childrenList) {
		
		bankAccount.setBookings(convertToTypedList(childrenList, new ArrayList<Booking>(), Booking.class));
		bankAccount.setAllowedBusinessCases(getResultList(BusinessCase.class, bankAccount.getId(), StatementType.SELECT_WITH_PARENT, null));
	}
}
