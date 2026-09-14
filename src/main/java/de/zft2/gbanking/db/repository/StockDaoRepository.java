package de.zft2.gbanking.db.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.zft2.gbanking.db.DaoRepository.Query;
import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.DbSession;
import de.zft2.gbanking.db.JdbcOperations;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.mapper.StockDaoMapper;
import de.zft2.gbanking.db.dao.stock.StockDao;
import de.zft2.gbanking.db.dao.stock.StockDaoMetadata;
import de.zft2.gbanking.db.dao.stock.StockWriteMode;
import de.zft2.gbanking.exception.GBankingException;

public final class StockDaoRepository<T extends StockDao> extends JdbcDaoRepository<T> {

	private final StockDaoMetadata<T> metadata;
	private final StockDaoMapper<T> stockMapper;

	public StockDaoRepository(Class<T> type, DbSession session) {
		super(type, session);
		metadata = StockDaoMetadata.of(type);
		stockMapper = new StockDaoMapper<>(type);
	}

	@Override
	public T findById(int id, ResultType resultType) throws SQLException {
		if (metadata.idColumn().isBlank()) {
			throw new GBankingException("DAO has no single primary key: " + type().getName());
		}
		String sql = "SELECT * FROM " + metadata.tableName() + " WHERE " + metadata.idColumn() + " = ?";
		List<T> results = query(sql, type(), ResultType.WITHOUT_RELATIONS, statement -> statement.setInt(1, id));
		if (results.size() > 1) {
			throw new GBankingException("Primary-key query returned multiple rows for " + type().getName());
		}
		return results.isEmpty() ? null : results.get(0);
	}

	@Override
	public T find(Dao criteria) throws SQLException {
		T stockCriteria = type().cast(criteria);
		return stockCriteria.getId() > 0 ? findById(stockCriteria.getId(), ResultType.WITHOUT_RELATIONS) : null;
	}

	@Override
	public List<T> findAll(Query query, Dao criteria) throws SQLException {
		if (criteria != null || query.stateFilter() != null) {
			throw new GBankingException("Stock DAO filtering requires a dedicated query");
		}
		String sql = query.sqlKey() != null ? DaoSqlStatements.dml(query.sqlKey()) : selectSql(query);
		Integer parentId = isParentQuery(query.statementType()) ? query.parentId() : null;
		return query(sql, type(), ResultType.WITHOUT_RELATIONS,
				parentId != null ? statement -> statement.setInt(1, parentId) : null);
	}

	@Override
	public T executeWrite(T entity, StatementType statementType) throws SQLException {
		verifyWriteAllowed(statementType);
		if (statementType == StatementType.INSERT) {
			int id = jdbc().insertReturningKey(insertSql(entity), statement -> stockMapper.bind(entity, statementType, statement));
			if (metadata.generatedId()) {
				entity.setId(id);
			}
			return entity;
		}
		if (statementType == StatementType.UPDATE) {
			JdbcOperations.validateSingleRowUpdate(
					jdbc().update(updateSql(), statement -> stockMapper.bind(entity, statementType, statement)));
			return entity;
		}
		throw new GBankingException("Unsupported stock DAO write type: " + statementType);
	}

	@Override
	public int delete(T entity, StatementType statementType) throws SQLException {
		if (metadata.writeMode() != StockWriteMode.MUTABLE) {
			throw new GBankingException("Repository does not allow deletion for DAO type: " + type().getName());
		}
		if (statementType != null && statementType != StatementType.DELETE) {
			throw new GBankingException("Unsupported stock DAO delete type: " + statementType);
		}
		return jdbc().update("DELETE FROM " + metadata.tableName() + " WHERE " + requiredIdColumn() + " = ?",
				statement -> statement.setInt(1, entity.getId()));
	}

	private String selectSql(Query query) {
		boolean parentQuery = isParentQuery(query.statementType());
		if (!parentQuery && query.statementType() != StatementType.SELECT_ALL
				&& query.statementType() != StatementType.SELECT_FULL_DATA) {
			throw new GBankingException("Unsupported stock DAO query type: " + query.statementType());
		}
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(metadata.tableName());
		if (parentQuery) {
			if (metadata.parentColumn().isBlank() || query.parentId() == null || query.parentId() <= 0) {
				throw new GBankingException("Stock DAO parent query needs a configured positive parent ID");
			}
			sql.append(" WHERE ").append(metadata.parentColumn()).append(" = ?");
		}
		if (!metadata.idColumn().isBlank()) {
			sql.append(" ORDER BY ").append(metadata.idColumn());
		}
		return sql.toString();
	}

	private String insertSql(T entity) {
		List<String> columns = new ArrayList<>();
		if (!metadata.hasMappedIdColumn() && (!metadata.generatedId() || entity.getId() > 0)) {
			columns.add(requiredIdColumn());
		}
		metadata.columns().forEach(column -> columns.add(column.name()));
		if (metadata.hasCreatedAt()) {
			columns.add("createdAt");
		}
		if (metadata.hasUpdatedAt()) {
			columns.add("updatedAt");
		}
		String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
		return "INSERT INTO " + metadata.tableName() + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
	}

	private String updateSql() {
		List<String> assignments = new ArrayList<>();
		metadata.columns().stream()
				.filter(column -> !column.name().equals(metadata.idColumn()))
				.map(column -> column.name() + " = ?")
				.forEach(assignments::add);
		if (metadata.hasUpdatedAt()) {
			assignments.add("updatedAt = ?");
		}
		if (assignments.isEmpty()) {
			throw new GBankingException("Stock DAO has no updatable columns: " + type().getName());
		}
		return "UPDATE " + metadata.tableName() + " SET " + String.join(", ", assignments)
				+ " WHERE " + requiredIdColumn() + " = ?";
	}

	private void verifyWriteAllowed(StatementType statementType) {
		if (metadata.writeMode() == StockWriteMode.READ_ONLY) {
			throw new GBankingException("Repository is read-only for DAO type: " + type().getName());
		}
		if (metadata.writeMode() == StockWriteMode.APPEND_ONLY && statementType != StatementType.INSERT) {
			throw new GBankingException("Repository is append-only for DAO type: " + type().getName());
		}
	}

	private String requiredIdColumn() {
		if (metadata.idColumn().isBlank()) {
			throw new GBankingException("DAO has no single primary key: " + type().getName());
		}
		return metadata.idColumn();
	}

	private static boolean isParentQuery(StatementType statementType) {
		return statementType == StatementType.SELECT_WITH_PARENT
				|| statementType == StatementType.SELECT_WITH_PARENT_AND_FULL_DATA;
	}
}
