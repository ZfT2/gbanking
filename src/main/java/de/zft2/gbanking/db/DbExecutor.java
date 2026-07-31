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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.logic.StatementsLogic;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.StatementsResultMapper;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.db.enu.StateType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.messages.MessageConstants;

public abstract class DbExecutor extends DbConnectionHandler implements BaseMessages {

	private static final Logger log = LogManager.getLogger(DbExecutor.class);

	protected DbExecutor() {
	}

	public <T extends Dao> T getById(Class<T> type, int id) {
		return withDbAccess(() -> getResult(type, id, ResultType.WITHOUT_RELATIONS));
	}

	public <T extends Dao> T getByIdFull(Class<T> type, int id) {

		return withDbAccess(() -> getResult(type, id, ResultType.FULL));
	}

	public <T> T getSingleResultField(Dao dao, StatementType statementType, Class<T> resultType) {

		return withDbAccess(() -> {
			String sql = StatementsConfig.getSqlStatement(dao.getClass(), statementType);
			return executeSelectSimpleField(sql, dao, null, null, /* "lastBookingDate", */ resultType);
		});
	}

	public <T extends Dao> T find(Class<T> type, Dao entity) {

		return withDbAccess(() -> {
			if (entity == null)
				return null;

			String sql = StatementsConfig.getSqlStatement(entity.getClass(), StatementType.SELECT_FIND);

			T returnDao = null;
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				getMapper(entity).setParamsFind(entity, ps);

				ResultSet rs = ps.executeQuery();

				int count = 0;
				while (rs.next()) {
					++count;
					if (count > 1) {
						throw new GBankingException("single SELECT / FIND returned more than one result!");
					}
					returnDao = toDao(rs, type, ResultType.SINGLE_FIELD);
				}

				rs.close();
			} catch (SQLException | RuntimeException e) {
				throw databaseReadFailure(getText(SqlErrors.ERROR_DB_FIND), e);
			}
			return returnDao;
		});
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
				log.error("Enitiy to delete is null! (StatementType: {})", statementType);
				return false;
			}

			StatementType effectiveStatementType = statementType != null ? statementType : StatementType.DELETE;
			String sql = StatementsConfig.getSqlStatement(entity.getClass(), effectiveStatementType);
			return executeSqlDeleteStatement(sql, entity) > 0;
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

	private <T extends Dao> void setStatementParamsUpdateListWithId(Set<Integer> idList, T targetDao, PreparedStatement ps)
			throws SQLException {
		AbstractDaoMapper<T, ?> mapper = getMapper(Dao.class);
		mapper.setParamsForeignKeyUpdate(idList, targetDao, ps);
	}

	protected int executeSelectId(String sql, Map<Object, Integer> criteriaParamMap) {

		int id = -1;
		try (PreparedStatement psSelect = connection.prepareStatement(sql)) {

			int i = 1;
			for (Entry<Object, Integer> paramEntry : criteriaParamMap.entrySet()) {
				psSelect.setObject(i, paramEntry.getKey(), paramEntry.getValue());
				i++;
			}

			ResultSet rs = psSelect.executeQuery();

			while (rs.next()) {
				id = rs.getInt("id");
			}
			rs.close();

		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}
		return id;
	}

	protected <T extends Dao> List<T> executeSqlSelectStatementForList(String sql, Class<T> type, List<?> criteriaParams) {
		List<T> resultList = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			int parameterIndex = 1;
			for (Object criteriaParam : criteriaParams) {
				ps.setObject(parameterIndex++, criteriaParam);
			}

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					resultList.add(toDao(rs, type, ResultType.WITHOUT_RELATIONS));
				}
			}
		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}
		return resultList;
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
		return withDbAccess(() -> {
			List<T> entityListDB = getResultList(type, parentObjectId, statementType, stateTypeTofilter, specificCriteria, specificSqlKey);

			for (T entity : entityListDB) {
				ResultType resultType = statementType.getResultType();
				if (resultType.isWithRelations()) {
					addOneToManyRelations(entity);
					addOneToOneRelations(entity);
				}
			}

			return entityListDB;
		});
	}

	protected boolean updateDaoListWithDetailIdList(Map<? extends Dao, Set<Integer>> daoBookingMap, String sql) {
		for (Entry<? extends Dao, Set<Integer>> daoEntry : daoBookingMap.entrySet()) {
			String sqlListStatement = String.format(sql, daoEntry.getValue().stream().map(v -> "?").collect(Collectors.joining(", ")));
			int affectedRows = executeSqlUpdateStatementForeignKeyForList(sqlListStatement, daoEntry.getKey(), daoEntry.getValue());
			if (affectedRows <= 0) {
				throw new GBankingException("Database update did not affect all requested records");
			}
		}

		return true;
	}

	/* START for logic... */

	/**
	 * @param <K> mapKey
	 * @param <V> mapValue
	 * 
	 **/
	protected <K, V> Map<K, V> executeSqlSelectStatementForMap(String sql, Dao dao, final String keyName, Class<K> keyType, final String valueName,
			Class<V> valueType) {

		Map<K, V> resultMap = null;

		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			if (dao != null)
				ps.setInt(1, dao.getId());
			ResultSet rs = ps.executeQuery();
			resultMap = new HashMap<>(rs.getFetchSize());
			while (rs.next()) {
				resultMap.put(rs.getObject(keyName, keyType), rs.getObject(valueName, valueType));
			}
			rs.close();

		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}
		return resultMap;
	}

	protected int executeSqlDeleteStatement(String sql, Dao dao) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			if (dao != null)
				getMapper(dao).setParamsDelete(dao, ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB_DELETE), e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB_DELETE), e);
		}
	}

	protected <T extends Dao> int executeSqlUpdateStatementForList(String sql, StatementType statementType, Class<? extends Dao> typeToUpdate, List<T> daoList) {
		
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			
			if(statementType.isSimpleField()) {
				AbstractDaoMapper<T, ?> mapper = getMapper(detectListType(daoList));
				mapper.setParamsForUpdateSimpleField(daoList, typeToUpdate, ps);
			} else
				setStatementParamsUpdateList(daoList, ps);
			
			return ps.executeUpdate();
			
		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB_UPDATE), e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), e);
		}
	}

	protected <T extends Dao> int executeSqlUpdateStatementForeignKeyForList(String sql, T targetDao, Set<Integer> pkIdList) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			setStatementParamsUpdateListWithId(pkIdList, targetDao, /* type, */ ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB_UPDATE), e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), e);
		}
	}

	protected <V extends Dao, T extends Dao> int executeSqlUpdateStatementForList(String sql, List<V> daoList, Dao mTable, Class<T> mapperType) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			mapStatementParams(daoList, mTable, mapperType, ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB_UPDATE), ", SQL: " + sql, e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB_UPDATE), e);
		}
	}

	/** replacement for executePreparedStatement?? **/
	protected <T extends Dao> T executeInsertUpdateStatement(StatementType statementType, T entity) {
		String sql = StatementsConfig.getSqlStatement(entity.getClass(), statementType);
		SQLMode mode = statementType.getSqlMode();

		boolean needsGeneratedKeys = mode == SQLMode.INSERT || mode == SQLMode.INSERT_BATCH;
		try (PreparedStatement ps = needsGeneratedKeys ? connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : connection.prepareStatement(sql)) {
			mapStatementParams(statementType, entity, null, ps);

			int affectedRows = 0;
			if (mode == SQLMode.INSERT_BATCH) {
				int[] batchResult = ps.executeBatch();
				ensureBatchSucceeded(batchResult);
				for (int count : batchResult) {
					if (count > 0) {
						affectedRows += count;
					}
				}
			} else {
				affectedRows = ps.executeUpdate();
			}
			if (needsGeneratedKeys) {
				setGeneratedDbIds(entity, ps);
			} else {
				log.debug("{} for {}, count: {}", mode, entity.getClass().getName(), affectedRows);
			}

		} catch (SQLException e) {
			String errorMessage = getText(mode == SQLMode.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT, entity.getId());
			log.error(errorMessage, e);
			throw new GBankingException(errorMessage, e);
		}

		return entity;
	}

	/**
	 * replacement for public <T extends Dao> Set<T> insertOrUpdateAll(Set<T> entitySet)??
	 **/
	protected <T extends Dao> Set<T> executeStatementList(StatementType statementType, Set<T> entitySet) {

		if (entitySet == null || entitySet.isEmpty())
			return Collections.emptySet();
		
		T firstEntity = entitySet.iterator().next();
		String sql = StatementsConfig.getSqlStatement(firstEntity.getClass(), statementType);

		try (PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" })) {

			AbstractDaoMapper<T, ?> mapper = getMapper(firstEntity);
			mapper.setParamsFull(entitySet, ps);

			ensureBatchSucceeded(ps.executeBatch());

		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB), e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), e);
		} catch (Exception e) {
			log.error(getText(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
			throw new GBankingException(getText(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
		}
		return entitySet;
	}

	protected <T extends Dao> Set<Integer> executeStatementList(String sql, Set<Integer> entitySet, Dao mTable, Class<T> mapperType) {
		if (entitySet == null || entitySet.isEmpty())
			return Collections.emptySet();

		try (PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" })) {

			AbstractDaoMapper<T, ?> mapper = getMapper(/* firstEntity */ mapperType);
			mapper.setParamsMn(mTable, entitySet, ps);

			ensureBatchSucceeded(ps.executeBatch());

		} catch (SQLException e) {
			log.error(getText(SqlErrors.ERROR_DB), e);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), e);
		} catch (Exception e) {
			log.error(getText(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
			throw new GBankingException(getText(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
		}
		return entitySet;
	}

	private void ensureBatchSucceeded(int[] updateCounts) {
		for (int updateCount : updateCounts) {
			if (updateCount == Statement.EXECUTE_FAILED) {
				throw new GBankingException("Database batch update failed");
			}
		}
	}

	/** END for logic... **/

	protected <T extends Dao> T getResult(Class<T> type, int id, ResultType resultType) {

		T entity = null;

		String sql = StatementsConfig.getSelectByIdSqlStatement(type);
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					entity = toDao(rs, type, ResultType.WITHOUT_RELATIONS);
				}
			}

			if (entity != null && resultType.isWithRelations()) {
				addOneToManyRelations(entity);
				addOneToOneRelations(entity);
			}
		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}

		return entity;
	}

	protected <T extends Dao> List<T> getResultList(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter,
			String specificSqlKey) {
		return getResultList(type, parentObjectId, statementType, stateTypeTofilter, null, specificSqlKey);
	}

	protected <T extends Dao> List<T> getResultList(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter,
			Dao specificCriteria, String specificSqlKey) {

		if (type == null)
			return Collections.emptyList();

		List<T> entityListDB = new ArrayList<>();

		String sql = DaoSqlStatements.dml(specificSqlKey);
		if (sql == null)
			sql = StatementsConfig.getSqlStatement(type, statementType);

		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			
			int parameterIndex = 1;
			if (parentObjectId != null && parentObjectId > 0) {
				ps.setInt(parameterIndex++, parentObjectId);
			}

			if (specificCriteria != null) {
				parameterIndex = setSpecificParams(specificCriteria, statementType, parameterIndex, ps);
			}

			if (stateTypeTofilter != null) {
				setStateTypeFilter(ps, parameterIndex, stateTypeTofilter);
			}

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					entityListDB.add(toDao(rs, type, statementType.getResultType()));
				}
			}

		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}

		return entityListDB;
	}

	private int setSpecificParams(Dao specificCriteria, StatementType statementType, int parameterIndex, PreparedStatement ps) throws SQLException {
		AbstractDaoMapper<Dao, ?> mapper = getMapper(specificCriteria);
		return mapper.setParamsSpecific(specificCriteria, statementType, parameterIndex, ps);
	}

	private void setStateTypeFilter(PreparedStatement ps, int parameterIndex, StateType stateTypeTofilter) throws SQLException {
		if (stateTypeTofilter instanceof IdType idType) {
			ps.setInt(parameterIndex, idType.getDbStateId());
		} else {
			ps.setString(parameterIndex, stateTypeTofilter.name());
		}
	}

	protected <T, C extends Collection<T>> C convertToTypedList(Iterable<? extends Dao> from, C to, Class<T> collectionClass) {
		for (Dao item : from) {
			to.add(collectionClass.cast(item));
		}
		/** from = null; **/
		return to;
	}
	
	protected Class<? extends Dao> detectListType(Collection<? extends Dao> list) {
		return list.iterator().next().getClass();
	}

	private <T extends Dao> void setGeneratedDbIds(T entity, PreparedStatement ps) throws SQLException {
		try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
			if (generatedKeys.next()) {
				entity.setId(generatedKeys.getInt(1));
			} else {
				throw new SQLException(getText(SqlErrors.ERROR_DB_NO_ID, entity.getClass().getName()));
			}
		}
	}

	private <T> T executeSelectSimpleField(String sql, Dao dao, String criteriaParamOptional, String resultField, Class<T> type) {

		T result = null;

		try (PreparedStatement psSelect = connection.prepareStatement(sql);) {
			
			psSelect.setInt(1, dao.getId());
			if (criteriaParamOptional != null)
				psSelect.setString(2, criteriaParamOptional);

			ResultSet rs = psSelect.executeQuery();

			if (!rs.isBeforeFirst()) {
				result = convertToSimpleResult(null, null, type);
			}

			while (rs.next()) {
				result = convertToSimpleResult(rs, resultField, type);
			}
			rs.close();

		} catch (SQLException | RuntimeException e) {
			throw databaseReadFailure(getText(SqlErrors.ERROR_DB_SELECT), e);
		}
		return result;
	}

	private <T> T convertToSimpleResult(ResultSet rs, String resultField, Class<T> type) throws SQLException {
		if (rs == null) {
			return Boolean.class.equals(type) ? type.cast(false) : null;
		}

		Object rawResult = getResultObject(rs, resultField, null);
		if (rawResult == null) {
			return Boolean.class.equals(type) ? type.cast(false) : null;
		}

		T convertedResult = type.cast(getResultObject(rs, resultField, type));
		if (convertedResult == null) {
			throw new SQLException("Could not map non-null database value to " + type.getName());
		}
		return convertedResult;
	}

	private <T> Object getResultObject(ResultSet rs, String resultField, Class<T> type) throws SQLException {
		if (type == null) {
			return resultField == null ? rs.getObject(1) : rs.getObject(resultField);
		}
		return resultField == null ? rs.getObject(1, type) : rs.getObject(resultField, type);
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

	private <T extends Dao> void addOneToManyRelations(T parentEntity) {
		Class<? extends Dao> childType = StatementsConfig.getChildType(parentEntity.getClass());
		if (childType == null) {
			return;
		}

		List<? extends Dao> childrenListDB = getResultList(childType, parentEntity.getId(), StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, null, null);

		StatementsLogic<T> logic = StatementsConfig.getLogicForDaoType(parentEntity.getClass());
		logic.addOneToManyRelations(parentEntity, childrenListDB);
	}

	private <T extends Dao> void addOneToOneRelations(T entity) {

		StatementsLogic<T> logic = StatementsConfig.getLogicForDaoType(entity.getClass());
		logic.addOneToOneRelations(entity);
	}

	private <T extends Dao> T toDao(ResultSet rs, Class<T> type, ResultType resultType) throws SQLException {

		return type.cast(StatementsResultMapper.toDao(type, rs, resultType));
	}

	private <T extends Dao, V> AbstractDaoMapper<T, V> getMapper(Class<? extends Dao> type) {
		return StatementsConfig.getMapperForDaoType(type);
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
	
	private <T extends Dao, V> void mapStatementParams(StatementType statementType, T entity, Class<V> typeToUpdate, PreparedStatement ps) throws SQLException {
		
		AbstractDaoMapper<T, ?> mapper = getMapper(entity);
		switch (statementType.getResultType()) {
		case FULL:
			mapper.setParamsFull(entity, ps);
			break;
		case SINGLE_FIELD:
			mapper.setParamsForUpdateSimpleField(entity, typeToUpdate, ps);
			break;
		default:
		}
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

}
