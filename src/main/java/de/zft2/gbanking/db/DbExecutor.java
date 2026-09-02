package de.zft2.gbanking.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.logic.StatementsLogic;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.enu.StateType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.messages.MessageConstants;

public abstract class DbExecutor extends DbConnectionHandler implements BaseMessages {

	private static final Logger log = LogManager.getLogger(DbExecutor.class);
	private static final int MAX_IDS_PER_UPDATE = 900;

	protected DbExecutor() {
	}

	public <T extends Dao> T getById(Class<T> type, int id) {
		return withDbAccess(() -> repositoryRead(
				() -> repositories().getById(type, id, ResultType.WITHOUT_RELATIONS),
				SqlErrors.ERROR_DB_SELECT));
	}

	public <T extends Dao> T getByIdFull(Class<T> type, int id) {
		return withDbAccess(() -> repositoryRead(
				() -> repositories().getById(type, id, ResultType.FULL),
				SqlErrors.ERROR_DB_SELECT));
	}

	public <T> T getSingleResultField(Dao dao, StatementType statementType, Class<T> resultType) {

		return withDbAccess(() -> {
			String sql = StatementsConfig.getSqlStatement(dao.getClass(), statementType);
			return executeSelectSimpleField(sql, dao, resultType);
		});
	}

	public <T extends Dao> T find(Class<T> type, Dao entity) {
		return withDbAccess(() -> repositoryRead(
				() -> repositories().find(type, type.cast(entity)),
				SqlErrors.ERROR_DB_FIND));
	}

	public <T extends Dao> List<T> getAll(Class<T> type) {
		return getAll(type, null, StatementType.SELECT_ALL, null);
	}
	
	public <T extends Dao> List<T> getAllFull(Class<T> type) {
		return getAll(type, null, StatementType.SELECT_FULL_DATA, null);
	}

	public <T extends Dao> List<T> getAllSpecific(Class<T> type, StatementType statementType) {
		return getAll(type, null, statementType, null);
	}

	public <T extends Dao> List<T> getAllWithFilter(Class<T> type, StateType stateTypeTofilter) {
		return getAll(type, null, StatementType.SELECT_WITH_FILTER, stateTypeTofilter);
	}

	public <T extends Dao> List<T> getAllByParent(Class<T> type, Integer parentObjectId) {
		return getAll(type, parentObjectId, StatementType.SELECT_WITH_PARENT, null);
	}
	
	public <T extends Dao> List<T> getAllByParentFull(Class<T> type, Integer parentObjectId) {
		return getAll(type, parentObjectId, StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, null);
	}

	public <T extends Dao> List<T> getAllByParentWithFilter(Class<T> type, Integer parentObjectId, StateType stateTypeTofilter) {
		return getAll(type, parentObjectId, StatementType.SELECT_WITH_PARENT_AND_FILTER, stateTypeTofilter);
	}

	public <T extends Dao> List<T> getAllByParentSpecific(Class<T> type, Integer parentObjectId, StatementType statementType) {
		return getAll(type, parentObjectId, statementType, null);
	}

	public <T extends Dao> List<T> getAll(Class<T> type, String statementKey) {
		return getAll(type, null, StatementType.SELECT_ALL, null, statementKey);
	}

	@SuppressWarnings("unchecked")
	public <T extends Dao> List<T> getAllByParentSpecific(T criteria, Integer parentObjectId, StatementType statementType) {
		if (criteria == null) {
			return Collections.emptyList();
		}
		return getAll((Class<T>) criteria.getClass(), parentObjectId, statementType, null, criteria, null);
	}

	public <T extends Dao> T insertOrUpdate(T entity) {
		int originalId = entity.getId();
		return withDbTransaction(() -> {
			DbTransactionManager.onRollback(() -> entity.setId(originalId));
			StatementsLogic<T> logic = StatementsConfig.getLogicForDaoType(entity.getClass());
			return logic.insertOrUpdateSingle(entity);
		});
	}

	public boolean delete(Dao entity, StatementType statementType) {

		return withDbTransaction(() -> {
			if (entity == null) {
				log.error("Entity to delete is null (StatementType: {})", statementType);
				return false;
			}
			try {
				return repositories().delete(entity, statementType) > 0;
			} catch (SQLException exception) {
				String message = getText(SqlErrors.ERROR_DB_DELETE);
				log.error(message, exception);
				throw new GBankingException(message, exception);
			}
		});
	}

	public int executeSimpleUpdate(List<? extends Dao> daoList, StatementType statementType, Class<? extends Dao> typeToUpdate) {

		return withDbTransaction(() -> {
			String sql = StatementsConfig.getSqlStatement(typeToUpdate != null ? typeToUpdate : detectListType(daoList), statementType);
			return executeSqlUpdateStatementForList(sql, statementType, typeToUpdate, daoList);
		});
	}

	public <T extends Dao> void setStatementParamsUpdateList(List<T> daoList, PreparedStatement ps) throws SQLException {
		AbstractDaoMapper<T, ?> mapper = getMapper(daoList.iterator().next());
		mapper.setParamsFull(daoList, ps);
	}

	private <T extends Dao> void setStatementParamsUpdateListWithId(List<Integer> idList, T targetDao, PreparedStatement ps)
			throws SQLException {
		AbstractDaoMapper<T, ?> mapper = getMapper(Dao.class);
		mapper.setParamsForeignKeyUpdate(idList, targetDao, ps);
	}

	protected int executeSelectId(String sql, List<SqlParameterValue> criteriaParameters) {
		try {
			return jdbc().query(sql, statement -> {
				int index = 1;
				for (SqlParameterValue parameter : criteriaParameters) {
					statement.setObject(index++, parameter.value(), parameter.sqlType());
				}
			}, resultSet -> {
				if (!resultSet.next()) {
					return -1;
				}
				int id = resultSet.getInt("id");
				if (resultSet.next()) {
					throw new GBankingException("Database identity lookup returned more than one result");
				}
				return id;
			});
		} catch (SQLException | RuntimeException exception) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
		}
	}

	protected int executeSelectId(String sql, Map<Object, Integer> criteriaParamMap) {
		List<SqlParameterValue> parameters = new ArrayList<>(criteriaParamMap.size());
		for (Entry<Object, Integer> parameter : criteriaParamMap.entrySet()) {
			parameters.add(sqlParameterValue(parameter.getKey(), parameter.getValue()));
		}
		return executeSelectId(sql, parameters);
	}

	protected <T extends Dao> List<T> executeSqlSelectStatementForList(String sql, Class<T> type, List<?> criteriaParams) {
		AbstractDaoMapper<T, ?> mapper = getMapper(type);
		try {
			return jdbc().query(sql, statement -> {
				int parameterIndex = 1;
				for (Object criteriaParam : criteriaParams) {
					statement.setObject(parameterIndex++, criteriaParam);
				}
			}, resultSet -> mapAll(resultSet, mapper, ResultType.WITHOUT_RELATIONS));
		} catch (SQLException | RuntimeException exception) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
		}
	}

	protected <T extends Dao, V> AbstractDaoMapper<T, V> getMapper(Dao dao) {
		return getMapper(dao.getClass());
	}

	protected <T extends Dao> List<T> getAll(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter) {

		return getAll(type, parentObjectId, statementType, stateTypeTofilter, null, null);
	}

	protected <T extends Dao> List<T> getAll(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter,
			String specificSqlKey) {

		return getAll(type, parentObjectId, statementType, stateTypeTofilter, null, specificSqlKey);
	}

	protected <T extends Dao> List<T> getAll(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter,
			Dao specificCriteria, String specificSqlKey) {
		return withDbAccess(() -> repositoryRead(
				() -> repositories().getAll(type, parentObjectId, statementType, stateTypeTofilter,
						specificCriteria, specificSqlKey),
				SqlErrors.ERROR_DB_SELECT));
	}

	protected boolean updateDaoListWithDetailIdList(Map<? extends Dao, Set<Integer>> daoBookingMap, String sql) {
		for (Entry<? extends Dao, Set<Integer>> daoEntry : daoBookingMap.entrySet()) {
			Dao target = Objects.requireNonNull(daoEntry.getKey(), "Update target must not be null");
			if (target.getId() <= 0) {
				throw new GBankingException("Database update target must have a persisted ID");
			}
			Set<Integer> requestedIds = Objects.requireNonNull(daoEntry.getValue(), "Update IDs must not be null");
			List<Integer> ids = new ArrayList<>(requestedIds.size());
			for (Integer id : requestedIds) {
				if (id == null || id <= 0) {
					throw new GBankingException("Database update IDs must be positive");
				}
				ids.add(id);
			}
			for (int start = 0; start < ids.size(); start += MAX_IDS_PER_UPDATE) {
				List<Integer> chunk = ids.subList(start, Math.min(start + MAX_IDS_PER_UPDATE, ids.size()));
				String placeholders = String.join(", ", Collections.nCopies(chunk.size(), "?"));
				String sqlListStatement = String.format(sql, placeholders);
				int affectedRows = executeSqlUpdateStatementForeignKeyForList(sqlListStatement, target, chunk);
				if (affectedRows != chunk.size()) {
					throw new GBankingException("Database update did not affect all requested records");
				}
			}
		}

		return true;
	}

	protected <K, V> Map<K, V> executeSqlSelectStatementForMap(String sql, Dao dao, final String keyName, Class<K> keyType, final String valueName,
			Class<V> valueType) {
		try {
			return jdbc().query(sql,
					dao == null ? null : statement -> statement.setInt(1, dao.getId()),
					resultSet -> {
						Map<K, V> results = new HashMap<>();
						while (resultSet.next()) {
							K key = resultSet.getObject(keyName, keyType);
							V value = resultSet.getObject(valueName, valueType);
							if (results.containsKey(key) && !Objects.equals(results.get(key), value)) {
								throw new GBankingException("Database map query returned an ambiguous key: " + key);
							}
							results.putIfAbsent(key, value);
						}
						return results;
					});
		} catch (SQLException | RuntimeException exception) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
		}
	}

	protected int executeSqlDeleteStatement(String sql, Dao dao) {
		try {
			return jdbc().update(sql,
					dao == null ? null : statement -> getMapper(dao).setParamsDelete(dao, statement));
		} catch (SQLException exception) {
			String message = getText(SqlErrors.ERROR_DB_DELETE);
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
	}

	protected <T extends Dao> int executeSqlUpdateStatementForList(String sql, StatementType statementType, Class<? extends Dao> typeToUpdate, List<T> daoList) {
		try {
			if (!statementType.isSimpleField()) {
				return jdbc().update(sql, statement -> setStatementParamsUpdateList(daoList, statement));
			}

			Class<? extends Dao> effectiveType = typeToUpdate != null ? typeToUpdate : detectListType(daoList);
			List<Dao> updateTargets = getSimpleFieldUpdateTargets(daoList, effectiveType);
			AbstractDaoMapper<Dao, ?> targetMapper = getMapper(effectiveType);
			int[] updateCounts = jdbc().batch(sql, updateTargets,
					(statement, dao) -> targetMapper.setParamsForUpdateSimpleField(dao, effectiveType, statement));
			return countUpdatedRows(updateCounts);
		} catch (SQLException exception) {
			String message = getText(SqlErrors.ERROR_DB_UPDATE);
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
	}

	private <T extends Dao> List<Dao> getSimpleFieldUpdateTargets(List<T> daoList, Class<? extends Dao> typeToUpdate) {
		List<Dao> updateTargets = new ArrayList<>(daoList.size());
		AbstractDaoMapper<T, ?> sourceMapper = getMapper(detectListType(daoList));
		for (T dao : daoList) {
			updateTargets.addAll(sourceMapper.getSimpleFieldUpdateTargets(dao, typeToUpdate));
		}
		return updateTargets;
	}

	private static int countUpdatedRows(int[] updateCounts) {
		int updatedRows = 0;
		for (int updateCount : updateCounts) {
			updatedRows += updateCount == Statement.SUCCESS_NO_INFO ? 1 : updateCount;
		}
		return updatedRows;
	}

	protected <T extends Dao> int executeSqlUpdateStatementForeignKeyForList(String sql, T targetDao, List<Integer> pkIdList) {
		try {
			return jdbc().update(sql,
					statement -> setStatementParamsUpdateListWithId(pkIdList, targetDao, statement));
		} catch (SQLException exception) {
			String message = getText(SqlErrors.ERROR_DB_UPDATE);
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
	}

	protected <T extends Dao> int executeSqlUpdateStatementForeignKeyForList(String sql, T targetDao,
			Set<Integer> pkIdList) {
		return executeSqlUpdateStatementForeignKeyForList(sql, targetDao, new ArrayList<>(pkIdList));
	}

	protected <V extends Dao, T extends Dao> int executeSqlUpdateStatementForList(String sql, List<V> daoList, Dao mTable, Class<T> mapperType) {
		try {
			return jdbc().update(sql,
					statement -> mapStatementParams(daoList, mTable, mapperType, statement));
		} catch (SQLException exception) {
			String message = getText(SqlErrors.ERROR_DB_UPDATE);
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
	}

	protected <T extends Dao> T executeInsertUpdateStatement(StatementType statementType, T entity) {
		SQLMode mode = statementType.getSqlMode();
		try {
			return repositories().executeWrite(entity, statementType);
		} catch (SQLException exception) {
			String errorMessage = getText(mode == SQLMode.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT, entity.getId());
			log.error(errorMessage, exception);
			throw new GBankingException(errorMessage, exception);
		}
	}

	protected <T extends Dao> Set<T> executeStatementList(StatementType statementType, Set<T> entitySet) {

		if (entitySet == null || entitySet.isEmpty())
			return Collections.emptySet();
		
		T firstEntity = entitySet.iterator().next();
		String sql = StatementsConfig.getSqlStatement(firstEntity.getClass(), statementType);

		try {
			AbstractDaoMapper<T, ?> mapper = getMapper(firstEntity);
			jdbc().batch(sql, entitySet,
					(statement, entity) -> mapper.setParamsFull(entity, statement));
		} catch (SQLException exception) {
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		} catch (RuntimeException exception) {
			String message = getText(MessageConstants.ERROR_GENERAL, exception.getMessage());
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
		return entitySet;
	}

	protected <T extends Dao> Set<Integer> executeStatementList(String sql, Set<Integer> entitySet, Dao mTable, Class<T> mapperType) {
		if (entitySet == null || entitySet.isEmpty())
			return Collections.emptySet();

		try {
			AbstractDaoMapper<T, ?> mapper = getMapper(mapperType);
			jdbc().batch(sql, entitySet,
					(statement, entityId) -> mapper.setParamsMn(mTable, entityId, statement));
		} catch (SQLException exception) {
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		} catch (RuntimeException exception) {
			String message = getText(MessageConstants.ERROR_GENERAL, exception.getMessage());
			log.error(message, exception);
			throw new GBankingException(message, exception);
		}
		return entitySet;
	}

	protected <T extends Dao> T getResult(Class<T> type, int id, ResultType resultType) {
		return resultType.isWithRelations() ? getByIdFull(type, id) : getById(type, id);
	}

	protected <T extends Dao> List<T> getResultList(Class<T> type, Integer parentObjectId,
			StatementType statementType, StateType stateTypeTofilter, String specificSqlKey) {
		return getResultList(type, parentObjectId, statementType, stateTypeTofilter, null, specificSqlKey);
	}

	protected <T extends Dao> List<T> getResultList(Class<T> type, Integer parentObjectId,
			StatementType statementType, StateType stateTypeTofilter, Dao specificCriteria, String specificSqlKey) {
		return getAll(type, parentObjectId, statementType, stateTypeTofilter, specificCriteria, specificSqlKey);
	}

	protected <T, C extends Collection<T>> C convertToTypedList(Iterable<? extends Dao> from, C to,
			Class<T> collectionClass) {
		for (Dao item : from) {
			to.add(collectionClass.cast(item));
		}
		return to;
	}

	protected Class<? extends Dao> detectListType(Collection<? extends Dao> list) {
		return list.iterator().next().getClass();
	}

	private <T> T executeSelectSimpleField(String sql, Dao dao, Class<T> type) {
		try {
			return jdbc().query(sql, statement -> statement.setInt(1, dao.getId()), resultSet -> {
				T result = convertToSimpleResult(null, type);
				while (resultSet.next()) {
					result = convertToSimpleResult(resultSet, type);
				}
				return result;
			});
		} catch (SQLException | RuntimeException exception) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), exception);
		}
	}

	private <T> T convertToSimpleResult(ResultSet rs, Class<T> type) throws SQLException {
		if (rs == null) {
			return Boolean.class.equals(type) ? type.cast(false) : null;
		}

		Object rawResult = rs.getObject(1);
		if (rawResult == null) {
			return Boolean.class.equals(type) ? type.cast(false) : null;
		}

		T convertedResult = type.cast(rs.getObject(1, type));
		if (convertedResult == null) {
			throw new SQLException("Could not map non-null database value to " + type.getName());
		}
		return convertedResult;
	}

	private static <T extends Dao> List<T> mapAll(ResultSet resultSet, AbstractDaoMapper<T, ?> mapper,
			ResultType resultType) throws SQLException {
		List<T> results = new ArrayList<>();
		while (resultSet.next()) {
			results.add(mapper.map(resultSet, resultType));
		}
		return results;
	}

	protected static RuntimeException databaseReadFailure(String message, Exception exception) {
		if (exception instanceof CancellationException cancellationException) {
			return cancellationException;
		}
		if (exception instanceof GBankingException bankingException) {
			return bankingException;
		}
		log.error(message, exception);
		return new GBankingException(message, exception);
	}

	private <T extends Dao, V> AbstractDaoMapper<T, V> getMapper(Class<? extends Dao> type) {
		return StatementsConfig.getMapperForDaoType(type);
	}

	protected final JdbcOperations jdbc() {
		return requireSession().jdbc();
	}

	protected final int executeCachedUpdate(String sql, SqlStatementBinder binder) throws SQLException {
		return jdbc().update(sql, binder::bind);
	}

	protected final void executeSetupStatementOncePerSession(String key, String sql) throws SQLException {
		DbSession session = requireSession();
		if (session.hasCompletedSetupStatement(key)) {
			return;
		}
		DbTransactionManager.onRollback(() -> session.forgetCompletedSetupStatement(key));
		session.jdbc().execute(sql);
		session.rememberCompletedSetupStatement(key);
	}

	protected final void executeCachedUpdateExactlyOnce(String sql, SqlStatementBinder binder) throws SQLException {
		validateSingleRowUpdate(executeCachedUpdate(sql, binder));
	}

	protected final <T> int[] executeCachedBatch(String sql, Iterable<T> items, SqlBatchBinder<T> binder)
			throws SQLException {
		return jdbc().batch(sql, items, (statement, item) -> binder.bind(item, statement));
	}

	protected final <T> void executeCachedBatchExactlyOnce(String sql, List<T> items, SqlBatchBinder<T> binder)
			throws SQLException {
		validateSingleRowBatch(executeCachedBatch(sql, items, binder), items.size());
	}

	protected final <T extends Dao> void executeInsertBatchWithReservedIds(String selectMaximumIdSql,
			String insertSql, List<T> items,
			SqlBatchBinder<T> binder) throws SQLException {
		if (items.isEmpty()) {
			return;
		}
		long maximumId = jdbc().query(selectMaximumIdSql, null, resultSet -> {
			if (!resultSet.next()) {
				throw new SQLException("Database maximum-ID query returned no result");
			}
			long result = resultSet.getLong(1);
			if (resultSet.next()) {
				throw new SQLException("Database maximum-ID query returned more than one result");
			}
			return result;
		});
		if (maximumId < 0 || maximumId > (long) Integer.MAX_VALUE - items.size()) {
			throw new SQLException("Database ID range exhausted");
		}
		for (int index = 0; index < items.size(); index++) {
			items.get(index).setId((int) maximumId + index + 1);
		}
		int[] updateCounts = executeCachedBatch(insertSql, items, binder);
		if (updateCounts.length != items.size()) {
			throw new SQLException("Database insert batch returned an unexpected update count");
		}
		for (int updateCount : updateCounts) {
			if (updateCount != 1) {
				throw new SQLException("Database insert batch did not insert exactly one row per entity");
			}
		}
	}

	static void validateSingleRowUpdate(int updateCount) throws SQLException {
		if (updateCount != 1) {
			throw new SQLException("Database update did not affect exactly one row");
		}
	}

	static void validateSingleRowBatch(int[] updateCounts, int expectedCount) throws SQLException {
		if (updateCounts.length != expectedCount) {
			throw new SQLException("Database update batch returned an unexpected update count");
		}
		for (int updateCount : updateCounts) {
			if (updateCount != 1 && updateCount != Statement.SUCCESS_NO_INFO) {
				throw new SQLException("Database update batch did not affect exactly one row per entity");
			}
		}
	}

	protected final <T> T executeCachedQuery(String sql, SqlStatementBinder binder,
			SqlResultExtractor<T> extractor) throws SQLException {
		return jdbc().query(sql, binder == null ? null : binder::bind, extractor::extract);
	}

	private DaoRepositoryAdapter repositories() {
		return requireSession().repositories();
	}

	private DbSession requireSession() {
		DbSession session = getSession();
		if (session == null) {
			throw new GBankingException("No open database session");
		}
		return session;
	}

	private <T> T repositoryRead(SqlSupplier<T> operation, String errorKey) {
		try {
			return operation.get();
		} catch (SQLException | RuntimeException exception) {
			throw databaseReadFailure(getText(errorKey), exception);
		}
	}

	protected <T> T withDbAccess(Supplier<T> operation) {
		return DbTransactionManager.withAccess(operation);
	}

	protected void withDbAccess(Runnable operation) {
		DbTransactionManager.withAccess(operation);
	}

	protected <T> T withDbTransaction(Supplier<T> operation) {
		return DbTransactionManager.inTransaction(operation);
	}

	protected void withDbTransaction(Runnable operation) {
		DbTransactionManager.inTransaction(operation);
	}
	
	private <T extends Dao> void mapStatementParams(List<T> daoList, Dao mTable, Class<? extends Dao> mapperType, PreparedStatement ps) throws SQLException {

		if (mapperType == null)
			throw new GBankingException("mapperType missing!");

		AbstractDaoMapper<T, T> mapper = getMapper(mapperType);
		if (mTable == null) {
			mapper.setParamsFull(daoList, ps);
		} else {
			mapper.setParamsFull(daoList, mTable, ps);
		}
	}

	protected record SqlParameterValue(Object value, int sqlType) {
	}

	protected static SqlParameterValue sqlParameterValue(Object value, int sqlType) {
		return new SqlParameterValue(value, sqlType);
	}

	@FunctionalInterface
	private interface SqlSupplier<T> {

		T get() throws SQLException;
	}

	@FunctionalInterface
	protected interface SqlStatementBinder {

		void bind(PreparedStatement statement) throws SQLException;
	}

	@FunctionalInterface
	protected interface SqlBatchBinder<T> {

		void bind(T item, PreparedStatement statement) throws SQLException;
	}

	@FunctionalInterface
	protected interface SqlResultExtractor<T> {

		T extract(ResultSet resultSet) throws SQLException;
	}

}
