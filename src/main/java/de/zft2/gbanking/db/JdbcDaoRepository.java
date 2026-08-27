package de.zft2.gbanking.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.db.enu.StateType;
import de.zft2.gbanking.exception.GBankingException;

class JdbcDaoRepository<T extends Dao> implements DaoRepository<T> {

	private static final int MAX_IDS_PER_QUERY = 900;

	private final Class<T> type;
	private final JdbcOperations jdbc;
	private final AbstractDaoMapper<T, ?> mapper;

	JdbcDaoRepository(Class<T> type, DbSession session) {
		this.type = Objects.requireNonNull(type, "type");
		Objects.requireNonNull(session, "session");
		jdbc = session.jdbc();
		mapper = StatementsConfig.getMapperForDaoType(type);
	}

	@Override
	public final Class<T> type() {
		return type;
	}

	@Override
	public T findById(int id, ResultType resultType) throws SQLException {
		Objects.requireNonNull(resultType, "resultType");
		String sql = selectByIdSql(resultType);
		T result = queryOne(sql, statement -> statement.setInt(1, id), rowResultType(resultType));
		if (result != null && resultType.isWithRelations()) {
			loadRelations(List.of(result), null, null);
		}
		return result;
	}

	@Override
	public T find(Dao criteria) throws SQLException {
		Objects.requireNonNull(criteria, "criteria");
		String sql = StatementsConfig.getSqlStatement(type, StatementType.SELECT_FIND);
		T typedCriteria = type.cast(criteria);
		return queryOne(sql, statement -> mapper.setParamsFind(typedCriteria, statement),
				ResultType.SINGLE_FIELD);
	}

	@Override
	public List<T> findAll(Query query, Dao criteria) throws SQLException {
		Objects.requireNonNull(query, "query");
		String sql = query.sqlKey() != null
				? DaoSqlStatements.dml(query.sqlKey())
				: StatementsConfig.getSqlStatement(type, query.statementType());
		List<T> results = jdbc.query(sql, statement -> bindQuery(statement, query, criteria),
				resultSet -> mapAll(resultSet, query.statementType().getResultType()));
		ResultType resultType = query.statementType().getResultType();
		if (resultType != null && resultType.isWithRelations() && !results.isEmpty()) {
			loadRelations(results, query, criteria);
		}
		return results;
	}

	@Override
	public T executeWrite(T entity, StatementType statementType) throws SQLException {
		Objects.requireNonNull(entity, "entity");
		Objects.requireNonNull(statementType, "statementType");
		String sql = StatementsConfig.getSqlStatement(type, statementType);
		SQLMode mode = statementType.getSqlMode();
		if (mode == SQLMode.INSERT || mode == SQLMode.INSERT_BATCH) {
			entity.setId(jdbc.insertReturningKey(sql, statement -> mapper.setParamsFull(entity, statement)));
		} else if (mode == SQLMode.UPDATE) {
			DbExecutor.validateSingleRowUpdate(
					jdbc.update(sql, statement -> mapper.setParamsFull(entity, statement)));
		} else {
			throw new GBankingException("Unsupported repository write mode: " + mode);
		}
		return entity;
	}

	@Override
	public int delete(T entity, StatementType statementType) throws SQLException {
		Objects.requireNonNull(entity, "entity");
		StatementType effectiveType = statementType != null ? statementType : StatementType.DELETE;
		String sql = StatementsConfig.getSqlStatement(type, effectiveType);
		return jdbc.update(sql, statement -> mapper.setParamsDelete(entity, statement));
	}

	protected String selectByIdSql(ResultType resultType) {
		return StatementsConfig.getSelectByIdSqlStatement(type);
	}

	protected ResultType rowResultType(ResultType requestedResultType) {
		return ResultType.WITHOUT_RELATIONS;
	}

	protected void loadRelations(List<T> entities, Query query, Dao criteria) throws SQLException {
		// Most entity types have no relations. Specialized repositories override this.
	}

	protected final JdbcOperations jdbc() {
		return jdbc;
	}

	protected static boolean isCompleteResult(Query query, Dao criteria) {
		return query != null && query.parentId() == null && criteria == null && query.sqlKey() == null;
	}

	protected final <D extends Dao> List<D> query(String sql, Class<D> resultType, ResultType mappingType,
			JdbcOperations.StatementBinder binder) throws SQLException {
		AbstractDaoMapper<D, ?> resultMapper = StatementsConfig.getMapperForDaoType(resultType);
		return jdbc.query(sql, binder, resultSet -> mapAll(resultSet, resultMapper, mappingType));
	}

	protected final <D extends Dao> List<D> queryByIds(String sqlTemplate, Collection<Integer> ids,
			Class<D> resultType, ResultType mappingType) throws SQLException {
		AbstractDaoMapper<D, ?> resultMapper = StatementsConfig.getMapperForDaoType(resultType);
		List<Integer> sortedIds = normalizeIds(ids);
		if (sortedIds.isEmpty()) {
			return List.of();
		}
		List<D> results = new ArrayList<>();
		forEachIdChunk(sqlTemplate, sortedIds, (sql, chunk) -> results.addAll(jdbc.query(sql,
				statement -> bindIds(statement, chunk),
				resultSet -> mapAll(resultSet, resultMapper, mappingType))));
		return results;
	}

	protected final void consumeByIds(String sqlTemplate, Collection<Integer> ids,
			JdbcOperations.ResultExtractor<Void> extractor) throws SQLException {
		List<Integer> sortedIds = normalizeIds(ids);
		forEachIdChunk(sqlTemplate, sortedIds, (sql, chunk) -> jdbc.query(sql,
				statement -> bindIds(statement, chunk), extractor));
	}

	private static List<Integer> normalizeIds(Collection<Integer> ids) {
		return ids.stream()
				.filter(Objects::nonNull)
				.filter(id -> id > 0)
				.distinct()
				.sorted()
				.toList();
	}

	private static void forEachIdChunk(String sqlTemplate, List<Integer> ids, IdChunkConsumer consumer)
			throws SQLException {
		for (int start = 0; start < ids.size(); start += MAX_IDS_PER_QUERY) {
			List<Integer> chunk = ids.subList(start, Math.min(start + MAX_IDS_PER_QUERY, ids.size()));
			String placeholders = String.join(", ", Collections.nCopies(chunk.size(), "?"));
			consumer.accept(String.format(sqlTemplate, placeholders), chunk);
		}
	}

	private T queryOne(String sql, JdbcOperations.StatementBinder binder, ResultType resultType) throws SQLException {
		return jdbc.query(sql, binder, resultSet -> {
			if (!resultSet.next()) {
				return null;
			}
			T result = mapper.map(resultSet, resultType);
			if (resultSet.next()) {
				throw new GBankingException("Single database query returned more than one result for " + type.getName());
			}
			return result;
		});
	}

	private List<T> mapAll(ResultSet resultSet, ResultType resultType) throws SQLException {
		return mapAll(resultSet, mapper, resultType);
	}

	private static <D extends Dao> List<D> mapAll(ResultSet resultSet, AbstractDaoMapper<D, ?> resultMapper,
			ResultType resultType) throws SQLException {
		List<D> results = new ArrayList<>();
		while (resultSet.next()) {
			results.add(resultMapper.map(resultSet, resultType));
		}
		return results;
	}

	private void bindQuery(PreparedStatement statement, Query query, Dao criteria) throws SQLException {
		int parameterIndex = 1;
		if (query.parentId() != null && query.parentId() > 0) {
			statement.setInt(parameterIndex++, query.parentId());
		}
		if (criteria != null) {
			parameterIndex = bindCriteria(statement, criteria, query.statementType(), parameterIndex);
		}
		if (query.stateFilter() != null) {
			bindState(statement, parameterIndex, query.stateFilter());
		}
	}

	private int bindCriteria(PreparedStatement statement, Dao criteria, StatementType statementType, int parameterIndex)
			throws SQLException {
		return mapper.setParamsSpecific(type.cast(criteria), statementType, parameterIndex, statement);
	}

	private static void bindState(PreparedStatement statement, int parameterIndex, StateType state) throws SQLException {
		if (state instanceof IdType idType) {
			statement.setInt(parameterIndex, idType.getDbStateId());
		} else {
			statement.setString(parameterIndex, state.name());
		}
	}

	private static void bindIds(PreparedStatement statement, List<Integer> ids) throws SQLException {
		for (int index = 0; index < ids.size(); index++) {
			statement.setInt(index + 1, ids.get(index));
		}
	}

	@FunctionalInterface
	private interface IdChunkConsumer {

		void accept(String sql, List<Integer> ids) throws SQLException;
	}
}
