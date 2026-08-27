package de.zft2.gbanking.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Dao;

final class BankAccessRepository extends JdbcDaoRepository<BankAccess> {

	private final BankAccountRepository bankAccountRepository;

	BankAccessRepository(DbSession session, BankAccountRepository bankAccountRepository) {
		super(BankAccess.class, session);
		this.bankAccountRepository = bankAccountRepository;
	}

	@Override
	protected void loadRelations(List<BankAccess> bankAccesses, Query query, Dao criteria) throws SQLException {
		Map<Integer, BankAccess> accessById = new HashMap<>(bankAccesses.size());
		for (BankAccess bankAccess : bankAccesses) {
			bankAccess.setAccounts(new ArrayList<>());
			accessById.put(bankAccess.getId(), bankAccess);
		}

		boolean completeResult = isCompleteResult(query, criteria);
		List<BankAccount> accounts = completeResult
				? query(DaoSqlStatements.SQL_SELECT_ALL_BANKACCOUNTS, BankAccount.class,
						ResultType.WITHOUT_RELATIONS, null)
				: queryByIds(DaoSqlStatements.SQL_SELECT_BANKACCOUNTS_BY_BANKACCESS_IDS,
						accessById.keySet(), BankAccount.class, ResultType.WITHOUT_RELATIONS);
		bankAccountRepository.loadFullRelations(accounts, completeResult);
		addAccounts(accessById, accounts);
	}

	private static void addAccounts(Map<Integer, BankAccess> accessById, List<BankAccount> accounts) {
		for (BankAccount account : accounts) {
			BankAccess parent = accessById.get(account.getBankAccessId());
			if (parent != null) {
				parent.getAccounts().add(account);
			}
		}
	}

}
