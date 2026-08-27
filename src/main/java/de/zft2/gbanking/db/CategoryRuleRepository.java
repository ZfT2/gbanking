package de.zft2.gbanking.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;

final class CategoryRuleRepository extends JdbcDaoRepository<CategoryRule> {

	private static final String RELATION_PARENT_ID = "relationParentId";
	private final BankAccountRepository bankAccountRepository;

	CategoryRuleRepository(DbSession session, BankAccountRepository bankAccountRepository) {
		super(CategoryRule.class, session);
		this.bankAccountRepository = bankAccountRepository;
	}

	@Override
	protected String selectByIdSql(ResultType resultType) {
		return resultType.isWithRelations()
				? DaoSqlStatements.SQL_SELECT_CATEGORYRULE_FULL_BY_ID
				: super.selectByIdSql(resultType);
	}

	@Override
	protected void loadRelations(List<CategoryRule> categoryRules, Query query, Dao criteria) throws SQLException {
		Map<Integer, CategoryRule> ruleById = new HashMap<>(categoryRules.size());
		for (CategoryRule categoryRule : categoryRules) {
			categoryRule.setBankAccountList(new ArrayList<>());
			ruleById.put(categoryRule.getId(), categoryRule);
		}
		String sql = isCompleteResult(query, criteria)
				? DaoSqlStatements.SQL_SELECT_ALL_BANKACCOUNTS_WITH_CATEGORYRULE
				: DaoSqlStatements.SQL_SELECT_BANKACCOUNTS_BY_CATEGORYRULE_IDS;
		AbstractDaoMapper<BankAccount, ?> mapper = StatementsConfig.getMapperForDaoType(BankAccount.class);
		Map<Integer, BankAccount> accountById = new HashMap<>();
		JdbcOperations.ResultExtractor<Void> extractor = resultSet -> {
			mapBankAccounts(resultSet, ruleById, accountById, mapper);
			return null;
		};
		if (isCompleteResult(query, criteria)) {
			jdbc().query(sql, null, extractor);
		} else {
			consumeByIds(sql, ruleById.keySet(), extractor);
		}
		bankAccountRepository.loadFullRelations(new ArrayList<>(accountById.values()), false);
	}

	private static void mapBankAccounts(ResultSet resultSet, Map<Integer, CategoryRule> ruleById,
			Map<Integer, BankAccount> accountById, AbstractDaoMapper<BankAccount, ?> mapper) throws SQLException {
		while (resultSet.next()) {
			CategoryRule categoryRule = ruleById.get(resultSet.getInt(RELATION_PARENT_ID));
			if (categoryRule != null) {
				int accountId = resultSet.getInt("id");
				BankAccount account = accountById.get(accountId);
				if (account == null) {
					account = mapper.map(resultSet, ResultType.WITHOUT_RELATIONS);
					accountById.put(accountId, account);
				}
				categoryRule.getBankAccountList().add(account);
			}
		}
	}

}
