package de.zft2.gbanking.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;

final class BankAccountRepository extends JdbcDaoRepository<BankAccount> {

	private static final String RELATION_PARENT_ID = "relationParentId";

	BankAccountRepository(DbSession session) {
		super(BankAccount.class, session);
	}

	@Override
	protected void loadRelations(List<BankAccount> bankAccounts, Query query, Dao criteria) throws SQLException {
		loadFullRelations(bankAccounts, isCompleteResult(query, criteria));
	}

	void loadFullRelations(List<BankAccount> bankAccounts, boolean completeResult) throws SQLException {
		if (bankAccounts.isEmpty()) {
			return;
		}
		Map<Integer, BankAccount> accountById = new HashMap<>(bankAccounts.size());
		for (BankAccount account : bankAccounts) {
			account.setBookings(new ArrayList<>());
			account.setAllowedBusinessCases(new ArrayList<>());
			accountById.put(account.getId(), account);
		}

		if (completeResult) {
			loadAllAccountRelations(accountById);
			return;
		}
		loadAccountRelationsByIds(accountById);
	}

	private void loadAllAccountRelations(Map<Integer, BankAccount> accountById) throws SQLException {
		for (Booking booking : query(DaoSqlStatements.SQL_SELECT_BOOKINGS_FULL_FOR_ACCOUNT_RELATIONS,
				Booking.class, ResultType.FULL, null)) {
			BankAccount account = accountById.get(booking.getAccountId());
			if (account != null) {
				account.getBookings().add(booking);
			}
		}
		AbstractDaoMapper<BusinessCase, ?> mapper = StatementsConfig.getMapperForDaoType(BusinessCase.class);
		jdbc().query(DaoSqlStatements.SQL_SELECT_ALL_BUSINESSCASES_WITH_BANKACCOUNT, null, resultSet -> {
			mapBusinessCases(resultSet, accountById, mapper);
			return null;
		});
	}

	private void loadAccountRelationsByIds(Map<Integer, BankAccount> accountById) throws SQLException {
		for (Booking booking : queryByIds(DaoSqlStatements.SQL_SELECT_BOOKINGS_FULL_BY_ACCOUNT_IDS,
				accountById.keySet(), Booking.class, ResultType.FULL)) {
			BankAccount account = accountById.get(booking.getAccountId());
			if (account != null) {
				account.getBookings().add(booking);
			}
		}
		AbstractDaoMapper<BusinessCase, ?> mapper = StatementsConfig.getMapperForDaoType(BusinessCase.class);
		consumeByIds(DaoSqlStatements.SQL_SELECT_BUSINESSCASES_BY_BANKACCOUNT_IDS, accountById.keySet(), resultSet -> {
			mapBusinessCases(resultSet, accountById, mapper);
			return null;
		});
	}

	private static void mapBusinessCases(ResultSet resultSet, Map<Integer, BankAccount> accountById,
			AbstractDaoMapper<BusinessCase, ?> mapper) throws SQLException {
		while (resultSet.next()) {
			BankAccount account = accountById.get(resultSet.getInt(RELATION_PARENT_ID));
			if (account != null) {
				account.getAllowedBusinessCases().add(mapper.map(resultSet, ResultType.WITHOUT_RELATIONS));
			}
		}
	}

}
