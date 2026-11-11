package de.zft2.gbanking.db.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.DbSession;
import de.zft2.gbanking.db.JdbcOperations;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.BankAccessTanMechanism;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;

public final class BankAccessRepository extends JdbcDaoRepository<BankAccess> {

	private final BankAccountRepository bankAccountRepository;

	public BankAccessRepository(DbSession session, BankAccountRepository bankAccountRepository) {
		super(BankAccess.class, session);
		this.bankAccountRepository = bankAccountRepository;
	}

	@Override
	public BankAccess findById(int id, ResultType resultType) throws SQLException {
		return withTanMechanisms(super.findById(id, resultType));
	}

	@Override
	public List<BankAccess> findAll(Query query, Dao criteria) throws SQLException {
		List<BankAccess> bankAccesses = super.findAll(query, criteria);
		loadTanMechanisms(bankAccesses);
		return bankAccesses;
	}

	public BankAccess findFirst(String sql, JdbcOperations.StatementBinder binder) throws SQLException {
		AbstractDaoMapper<BankAccess, ?> mapper = StatementsConfig.getMapperForDaoType(BankAccess.class);
		BankAccess bankAccess = jdbc().query(sql, binder, resultSet -> resultSet.next()
				? mapper.map(resultSet, ResultType.WITHOUT_RELATIONS) : null);
		return withTanMechanisms(bankAccess);
	}

	private BankAccess withTanMechanisms(BankAccess bankAccess) throws SQLException {
		if (bankAccess != null) {
			loadTanMechanisms(List.of(bankAccess));
		}
		return bankAccess;
	}

	private void loadTanMechanisms(List<BankAccess> bankAccesses) throws SQLException {
		Map<Integer, BankAccessFints> fintsById = new HashMap<>();
		for (BankAccess bankAccess : bankAccesses) {
			if (bankAccess.getAccessType() == BankAccessType.HBCI) {
				bankAccess.getFints().setAllowedTwostepMechanisms(new ArrayList<>());
				fintsById.put(bankAccess.getId(), bankAccess.getFints());
			}
		}
		consumeByIds(DaoSqlStatements.SQL_SELECT_BANKACCESS_TAN_MECHANISMS_BY_IDS, fintsById.keySet(), resultSet -> {
			while (resultSet.next()) {
				BankAccessTanMechanism mechanism = new BankAccessTanMechanism(
						resultSet.getString("mechanismCode"), resultSet.getString("description"));
				fintsById.get(resultSet.getInt("bankAccess_id")).getAllowedTwostepMechanisms().add(mechanism.toBankValue());
			}
			return null;
		});
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
