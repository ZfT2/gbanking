package de.zft2.gbanking.db;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.enu.StateType;

final class DaoRepositoryAdapter {

	private final DaoRepositoryCatalog catalog;

	DaoRepositoryAdapter(DaoRepositoryCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	<T extends Dao> T getById(Class<T> type, int id, ResultType resultType) throws SQLException {
		return repository(type).findById(id, resultType);
	}

	<T extends Dao> T find(Class<T> type, Dao criteria) throws SQLException {
		return criteria == null ? null : repository(type).find(criteria);
	}

	<T extends Dao> List<T> getAll(Class<T> type, Integer parentId, StatementType statementType,
			StateType stateFilter, Dao criteria, String sqlKey) throws SQLException {
		if (type == null) {
			return Collections.emptyList();
		}
		return repository(type).findAll(new DaoRepository.Query(parentId, statementType, stateFilter, sqlKey), criteria);
	}

	<T extends Dao> T executeWrite(T entity, StatementType statementType) throws SQLException {
		return repositoryForEntity(entity).executeWrite(entity, statementType);
	}

	int delete(Dao entity, StatementType statementType) throws SQLException {
		return repositoryForEntity(entity).delete(entity, statementType);
	}

	private <T extends Dao> DaoRepository<T> repository(Class<T> type) {
		return catalog.repository(type);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T extends Dao> DaoRepository<T> repositoryForEntity(T entity) {
		return (DaoRepository) catalog.repository(entity.getClass());
	}
}
