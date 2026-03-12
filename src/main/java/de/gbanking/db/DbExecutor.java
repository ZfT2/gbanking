package de.gbanking.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.Dao;
import de.gbanking.db.dao.logic.StatementsLogic;
import de.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.gbanking.db.dao.mapper.StatementsResultMapper;
import de.gbanking.db.enu.StateType;
import de.gbanking.exception.GBankingException;
import de.gbanking.messages.MessageConstants;
import de.gbanking.util.TypeConverter;

public class DbExecutor extends DbConnectionHandler {

	private static Logger log = LogManager.getLogger(DbExecutor.class);

	private static DbExecutor dbExecutor;

	public static DbExecutor getInstance() {

		if (dbExecutor == null) {
			dbExecutor = new DbExecutor();
			log.debug("created new {}", DbExecutor.class.getSimpleName());
		}
		return dbExecutor;

	}

	public <T extends Dao> T getById(Class<T> type, int id) {

		return getResult(type, id, ResultType.WITHOUT_RELATIONS);
	}

	public <T extends Dao> T getByIdFull(Class<T> type, int id) {

		return getResult(type, id, ResultType.FULL);
	}

	public <T> T getSingleResultField(Dao dao, StatementType statementType, Class<T> resultType) {

		T result = null;
		String sql = StatementsConfig.getSqlStatement(dao.getClass(), statementType);
		result = executeSelectSimpleField(sql, dao, null, null, /* "lastBookingDate", */ resultType);

		return result;
	}

	public <T extends Dao> T find(Class<T> type, Dao entity) {

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
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_FIND), e);
		}
		return returnDao;
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
		return getAll(type, null, StatementType.SELECT_WITH_PARENT_AND_FILTER, stateTypeTofilter);
	}

	public <T extends Dao> List<T> getAllByParent(Class<T> type, Integer parentObjectId) {
		return getAll(type, parentObjectId, StatementType.SELECT_WITH_PARENT, null);
	}
	
	public <T extends Dao> List<T> getAllByParentFull(Class<T> type, Integer parentObjectId) {
		return getAll(type, parentObjectId, StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, null);
	}

	public <T extends Dao> List<T> getAllByParentSpecific(Class<T> type, Integer parentObjectId, StatementType statementType) {
		return getAll(type, parentObjectId, statementType, null);
	}

	public <T extends Dao> T insertOrUpdate(T entity) {
		StatementsLogic<T,?> logic = StatementsConfig.getLogicForDaoType(entity.getClass());
		return logic.insertOrUpdateSingle(entity);
	}

	public boolean delete(Dao entity, StatementType statementType) {

		statementType = statementType != null ? statementType : StatementType.DELETE;

		String sql = StatementsConfig.getSqlStatement(entity.getClass(), statementType);
		
		return executeSqlDeleteStatement(sql,  entity) > 0;
	}

	public boolean executeSimpleUpdate(List<? extends Dao> daoList, StatementType statementType, Class<? extends Dao> typeToUpdate) {

		boolean result = true;

		String sql = StatementsConfig.getSqlStatement(typeToUpdate != null ? typeToUpdate : detectListType(daoList), statementType);
		executeSqlUpdateStatementForList(sql, statementType, typeToUpdate, daoList);

		return result;
	}

	public <T extends Dao> void setStatementParamsUpdateList(List<T> daoList, PreparedStatement ps) throws SQLException {
		AbstractDaoMapper<T, ?> mapper = getMapper(daoList.iterator().next());
		mapper.setParamsFull(daoList, ps);
	}

	public <T extends Dao, V extends Dao> void setStatementParamsUpdateListWithId(Set<Integer> idList, T targetDao, /* Class<V> type, */ PreparedStatement ps)
			throws SQLException {
		AbstractDaoMapper<T, ?> mapper = getMapper(/* type */Dao.class);
		mapper.setParamsForeignKeyUpdate(idList, targetDao, ps);
	}

	protected int executeSelectId(String sql, String criteriaParam, String criteriaParamOptional) {

		int id = -1;
		try (PreparedStatement psSelect = connection.prepareStatement(sql)) {

			psSelect.setString(1, criteriaParam);
			if (criteriaParamOptional != null)
				psSelect.setString(2, criteriaParamOptional);

			ResultSet rs = psSelect.executeQuery();

			while (rs.next()) {
				id = rs.getInt("id");
			}
			rs.close();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}
		return id;
	}

	protected <T, V> AbstractDaoMapper<T, V> getMapper(Dao dao) {
		return getMapper(dao.getClass());
	}

	protected <T extends Dao> List<T> getAll(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter) {

		List<T> entityListDB = getResultList(type, parentObjectId, statementType, stateTypeTofilter);

		for (T entity : entityListDB) {
			ResultType resultType = statementType.getResultType();
			if (resultType.isWithRelations()) {
				addOneToManyRelations(entity);
				addOneToOneRelations(entity);
			}
		}

		return entityListDB;
	}

	protected boolean updateDaoListWithDetailIdList(Map<? extends Dao, Set<Integer>> daoBookingMap, String sql) {

		boolean result = true;

		for (Entry<? extends Dao, Set<Integer>> daoEntry : daoBookingMap.entrySet()) {
			String sqlListStatement = String.format(sql, daoEntry.getValue().stream().map(v -> "?").collect(Collectors.joining(", ")));
			result = result && executeSqlUpdateStatementForeignKeyForList(sqlListStatement, daoEntry.getKey(), daoEntry.getValue()) > 0;
		}

		return result;
	}

	/** START for logic... **/

	/**
	 * @param <K>
	 * @param <V>
	 * 
	 **/
	protected <K, V> Map<K, V> executeSqlSelectStatementForMap(String sql, Dao dao, final String keyName, Class<K> keyType, final String valueName,
			Class<V> valueType) {

		Map<K, V> resultMap = null;

		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			if (dao != null)
				ps.setInt(1, dao.getId());
			ResultSet rs = ps.executeQuery();
			resultMap = HashMap.newHashMap(rs.getFetchSize());
			while (rs.next()) {
				resultMap.put(rs.getObject(keyName, keyType), rs.getObject(valueName, valueType));
			}
			rs.close();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}
		return resultMap;
	}

	protected int executeSqlDeleteStatement(String sql, Dao dao) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			if (dao != null)
				getMapper(dao).setParamsDelete(dao, ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_DELETE), e);
			return -1;
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
			log.error(messages.getMessage(SqlErrors.ERROR_DB_UPDATE), e);
			return -1;
		}
	}

	protected <T extends Dao> int executeSqlUpdateStatementForeignKeyForList(String sql, T targetDao, Set<Integer> pkIdList) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			setStatementParamsUpdateListWithId(pkIdList, targetDao, /* type, */ ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_UPDATE), e);
			return -1;
		}
	}

	protected <V extends Dao, T extends Dao> int executeSqlUpdateStatementForList(String sql, List<V> daoList, Dao mTable, Class<T> mapperType) {

		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			mapStatementParams(daoList, mTable, mapperType, ps);

			return ps.executeUpdate();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_UPDATE), e);
			return -1;
		}
	}

	/** replacement for executePreparedStatement?? **/
	protected <T extends Dao> T executeInsertUpdateStatement(StatementType statementType, T entity) {

		String sql = StatementsConfig.getSqlStatement(entity.getClass(), statementType);
		SQLMode mode = statementType.getSqlMode();
		
		try (PreparedStatement ps = connection.prepareStatement(sql)) {

			mapStatementParams(statementType, entity, null, ps);

			connection.setAutoCommit(false);
			int affectedRows = 0;
			if (mode == SQLMode.INSERT_BATCH) {
				ps.executeBatch();
			} else {
				affectedRows = ps.executeUpdate();
			}
			connection.setAutoCommit(true);

			if (mode != SQLMode.UPDATE && mode != SQLMode.DELETE) {
				try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						entity.setId(generatedKeys.getInt(1));
					} else {
						throw new SQLException(messages.getFormattedMessage(SqlErrors.ERROR_DB_NO_ID, entity.getClass().getName()));
					}
				}
			} else /** if (mode == SQLMode.DELETE) **/
			{
				log.info("{} for {}, count: {}", mode, entity.getClass().getName(), affectedRows);
			}
		} catch (SQLException e) {
			log.error(messages.getFormattedMessage(mode == SQLMode.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT, entity.getId()), e);
		}
		return entity;
	}

	/**
	 * replacement for public <T extends Dao> Set<T> insertOrUpdateAll(Set<T> entitySet)??
	 **/
	protected <T extends Dao> Set<T> executeStatementList(StatementType statementType, Set<T> entitySet) {

		if (entitySet == null)
			return Collections.emptySet();
		
		T firstEntity = entitySet.iterator().next();
		String sql = StatementsConfig.getSqlStatement(firstEntity.getClass(), statementType);

		try (PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" })) {

			AbstractDaoMapper<T, ?> mapper = getMapper(firstEntity);
			mapper.setParamsFull(entitySet, ps);
			//List<V> daoList, Dao mTable, Class<? extends Dao> mapperType, PreparedStatement ps
			//mapStatementParams(entitySet, null, null, ps);

			ps.executeBatch();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB), e);
			return Collections.emptySet();
		} catch (Exception e) {
			log.error(messages.getFormattedMessage(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
			return Collections.emptySet();
		}
		return entitySet;
	}

	protected <T extends Dao> Set<Integer> executeStatementList(String sql, Set<Integer> entitySet, Dao mTable, Class<T> mapperType) {
		if (entitySet == null)
			return Collections.emptySet();

		try (PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" })) {

			AbstractDaoMapper<T, ?> mapper = getMapper(/* firstEntity */ mapperType);
			mapper.setParamsMn(mTable, entitySet, ps);

			ps.executeBatch();

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB), e);
			return Collections.emptySet();
		} catch (Exception e) {
			log.error(messages.getFormattedMessage(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
			return Collections.emptySet();
		}
		return entitySet;
	}

	/** END for logic... **/

	protected <T extends Dao> T getResult(Class<T> type, int id, ResultType resultType) {

		T entity = null;
		ResultSet rs = null;

		try (Statement stmt = connection.createStatement()) {
			rs = stmt.executeQuery("SELECT * FROM " + StatementsConfig.getTableViewName(type) + " WHERE id = " + id);

			while (rs.next()) {
				entity = toDao(rs, type, ResultType.WITHOUT_RELATIONS);
			}
			rs.close();

			if (entity != null && resultType.isWithRelations()) {
				addOneToManyRelations(entity);
				addOneToOneRelations(entity);
			}
		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		} catch (Exception e) {
			log.error(messages.getFormattedMessage(MessageConstants.ERROR_GENERAL, e.getMessage()), e);
		}

		return entity;
	}

	protected <T extends Dao> List<T> getResultList(Class<T> type, Integer parentObjectId, StatementType statementType, StateType stateTypeTofilter) {

		if (type == null)
			return Collections.emptyList();

		List<T> entityListDB = null;
		
		ResultSet rs = null;
		try (PreparedStatement ps = connection.prepareStatement(StatementsConfig.getSqlStatement(type, statementType))){
			
			if (parentObjectId != null && parentObjectId > 0) {
				ps.setInt(1, parentObjectId);
			}
			
			if (stateTypeTofilter != null) {
				ps.setString(1, stateTypeTofilter.name());
			}
			
			rs = ps.executeQuery();
			
			entityListDB = new ArrayList<>();
			while (rs.next()) {
				entityListDB.add(toDao(rs, type, statementType.getResultType()));
			}
			rs.close();

		} catch (Exception e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), e);
		}

		return entityListDB;
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

		} catch (SQLException e) {
			log.error(messages.getMessage(SqlErrors.ERROR_DB_SELECT), new GBankingException(e.getMessage(), true));
		}
		return result;
	}

	private <T> T convertToSimpleResult(ResultSet rs, String resultField, Class<T> type) throws SQLException {
		T convertedResult = null;

		if (type.equals(Boolean.class) && isNullResult(rs, resultField)) {
			return type.cast(false);
		}

		Class<?> rsType = type.equals(Calendar.class) ? java.sql.Date.class : type;

		Object resultObject = resultField == null ? rs.getObject(1, rsType) : rs.getObject(resultField, rsType);

		if (type.equals(Calendar.class)) {
			convertedResult = type.cast(TypeConverter.toCalendarFromSqlDate((java.sql.Date) resultObject));
		} else {
			convertedResult = type.cast(resultObject);
		}

		return convertedResult;
	}

	private boolean isNullResult(ResultSet rs, String resultField) throws SQLException {
		return (rs == null || (resultField == null ? rs.getObject(1) : rs.getObject(resultField)) == null);
	}

	private <T extends Dao> void addOneToManyRelations(T parentEntity) {

		List<? extends Dao> childrenListDB = getResultList(StatementsConfig.childrenMap.get(parentEntity.getClass()), parentEntity.getId(), StatementType.SELECT_WITH_PARENT_AND_FULL_DATA, null);

		StatementsLogic<T,?> logic = StatementsConfig.getLogicForDaoType(parentEntity.getClass());
		logic.addOneToManyRelations(parentEntity, childrenListDB);

	}

	private <T extends Dao> void addOneToOneRelations(T entity) {

		StatementsLogic<T,?> logic = StatementsConfig.getLogicForDaoType(entity.getClass());
		logic.addOneToOneRelations(entity);
	}

	private <T extends Dao> T toDao(ResultSet rs, Class<T> type, ResultType resultType) throws SQLException {

		return type.cast(StatementsResultMapper.toDao(type, rs, resultType));
	}

	private <T, V> AbstractDaoMapper<T, V> getMapper(Class<? extends Dao> type) {
		return StatementsConfig.getMapperForDaoType(type);
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
			;
		}
	}
	
	private <T> void mapStatementParams(List<T> daoList, Dao mTable, Class<? extends Dao> mapperType, PreparedStatement ps) throws SQLException {

		if (mapperType == null)
			throw new GBankingException("mapperType missing!");

		AbstractDaoMapper<?, T> mapper = getMapper(mapperType);
		if (mTable == null) {
			getMapper(mapperType).setParamsFull(daoList, ps);
		} else {
			mapper.setParamsFull(daoList, mTable, ps);
		}
	}

}
