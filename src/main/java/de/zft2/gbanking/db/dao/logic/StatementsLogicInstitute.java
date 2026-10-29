package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.InstituteMapper;

public class StatementsLogicInstitute extends StatementsLogicDefault<Institute> implements StatementsLogic<Institute> {

	@Override
	public Institute insertOrUpdateSingle(Institute institute) {
		StatementType statementType = getStatementTypeForInsertOrUpdate(institute);
		AbstractDaoMapper<Institute, Void> mapperBase = StatementsConfig.getMapperForDaoType(Institute.class);
		InstituteMapper mapper = (InstituteMapper) mapperBase;

		try {
			executeInsertUpdateStatement(statementType, institute);
			persistInstituteDetails(institute, statementType, mapper);
		} catch (SQLException exception) {
			String errorKey = statementType == StatementType.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT;
			throw databaseFailure(getText(errorKey, institute.getId()), exception);
		}

		return institute;
	}

	@Override
	public Set<Institute> insertAll(Set<Institute> institutes) {
		if (institutes == null || institutes.isEmpty()) {
			return Collections.emptySet();
		}

		AbstractDaoMapper<Institute, Void> mapperBase = StatementsConfig.getMapperForDaoType(Institute.class);
		InstituteMapper mapper = (InstituteMapper) mapperBase;
		List<Institute> inserts = matching(institutes, institute -> institute.getId() <= 0);
		List<Institute> updates = matching(institutes, institute -> institute.getId() > 0);
		try {
			if (!inserts.isEmpty()) {
				executeInsertBatchWithReservedIds(DaoSqlStatements.SQL_SELECT_MAX_INSTITUTE_ID,
						DaoSqlStatements.SQL_INSERT_INSTITUTE_BATCH, inserts,
						(institute, statement) -> mapper.setParamsInsertWithId(institute, statement));
			}
			if (!updates.isEmpty()) {
				String updateSql = StatementsConfig.getSqlStatement(Institute.class, StatementType.UPDATE);
				executeCachedBatchExactlyOnce(updateSql, updates,
						(institute, statement) -> mapper.setParamsFull(institute, statement));
			}
			persistInstituteDetails(inserts, updates, mapper);
			return institutes;
		} catch (SQLException exception) {
			throw databaseFailure(getText(SqlErrors.ERROR_DB), exception);
		}
	}

	private void persistInstituteDetails(Institute institute, StatementType statementType, InstituteMapper mapper)
			throws SQLException {
		persistInstituteDetail(institute, statementType, InstituteMapper::hasDkData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DK, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DK, mapper::setParamsDK);
		persistInstituteDetail(institute, statementType, InstituteMapper::hasDbbData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB, mapper::setParamsDBB);
		persistInstituteDetail(institute, statementType, InstituteMapper::hasEpcData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_EPC, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_EPC, mapper::setParamsEPC);
		persistInstituteDetail(institute, statementType, InstituteMapper::hasDbbReachableData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB_REACHABLE, mapper::setParamsDbbReachable);
		persistInstituteDetail(institute, statementType, InstituteMapper::hasAdditionalData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_ADDITIONAL,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_ADDITIONAL, mapper::setParamsAdditional);
	}

	private void persistInstituteDetail(Institute institute, StatementType statementType,
			Predicate<Institute> hasDetails, String updateSql, String insertSql, String deleteSql,
			InstituteDetailBinder binder) throws SQLException {
		if (!hasDetails.test(institute)) {
			if (statementType == StatementType.UPDATE) {
				deleteDetailsById(deleteSql, institute);
			}
			return;
		}
		if (statementType == StatementType.INSERT) {
			executeCachedUpdateExactlyOnce(insertSql,
					statement -> binder.bind(institute, StatementType.INSERT, statement));
		} else {
			updateThenInsertInstituteDetail(institute, updateSql, insertSql, binder);
		}
	}

	private void updateThenInsertInstituteDetail(Institute institute, String updateSql, String insertSql,
			InstituteDetailBinder binder) throws SQLException {
		int updateCount = executeCachedUpdate(updateSql,
				statement -> binder.bind(institute, StatementType.UPDATE, statement));
		if (updateCount == 0) {
			executeCachedUpdateExactlyOnce(insertSql,
					statement -> binder.bind(institute, StatementType.INSERT, statement));
		} else if (updateCount != 1) {
			throw new SQLException("Database detail update did not affect at most one row");
		}
	}

	private void persistInstituteDetails(List<Institute> inserts, List<Institute> updates, InstituteMapper mapper)
			throws SQLException {
		insertInstituteDetails(inserts, InstituteMapper::hasDkData, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, InstituteMapper::hasDbbData, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, InstituteMapper::hasEpcData, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, InstituteMapper::hasDbbReachableData,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, InstituteMapper::hasAdditionalData,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL,
				(institute, statement) -> mapper.setParamsAdditional(institute, StatementType.INSERT, statement));

		persistDetailBatch(updates, InstituteMapper::hasDkData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DK, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DK,
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, InstituteMapper::hasDbbData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB,
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, InstituteMapper::hasEpcData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_EPC, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_EPC,
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, InstituteMapper::hasDbbReachableData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB_REACHABLE,
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, InstituteMapper::hasAdditionalData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_ADDITIONAL,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_ADDITIONAL,
				(institute, statement) -> mapper.setParamsAdditional(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsAdditional(institute, StatementType.INSERT, statement));
	}

	private void insertInstituteDetails(Iterable<Institute> institutes, Predicate<Institute> hasDetails,
			String insertSql, SqlBatchBinder<Institute> insertBinder) throws SQLException {
		List<Institute> detailInstitutes = matching(institutes, hasDetails);
		if (!detailInstitutes.isEmpty()) {
			executeCachedBatchExactlyOnce(insertSql, detailInstitutes, insertBinder);
		}
	}

	private void persistDetailBatch(Iterable<Institute> updates,
			Predicate<Institute> hasDetails, String updateSql, String insertSql, String deleteSql,
			SqlBatchBinder<Institute> updateBinder, SqlBatchBinder<Institute> insertBinder) throws SQLException {
		List<Institute> detailInstitutes = matching(updates, hasDetails);
		if (!detailInstitutes.isEmpty()) {
			int[] updateCounts = executeCachedBatch(updateSql, detailInstitutes, updateBinder);
			List<Institute> missingDetails = getMissingDetails(detailInstitutes, updateCounts);
			if (!missingDetails.isEmpty()) {
				executeCachedBatchExactlyOnce(insertSql, missingDetails, insertBinder);
			}
		}
		deleteDetailsById(deleteSql, matching(updates, hasDetails.negate()));
	}

	private static List<Institute> getMissingDetails(List<Institute> institutes, int[] updateCounts) throws SQLException {
		if (updateCounts.length != institutes.size()) {
			throw new SQLException("Database detail update batch returned an unexpected update count");
		}
		List<Institute> missingDetails = new ArrayList<>();
		for (int index = 0; index < updateCounts.length; index++) {
			int updateCount = updateCounts[index];
			if (updateCount == 0) {
				missingDetails.add(institutes.get(index));
			} else if (updateCount != 1) {
				throw new SQLException("Database detail update batch did not affect at most one row per entity");
			}
		}
		return missingDetails;
	}

	@FunctionalInterface
	private interface InstituteDetailBinder {

		void bind(Institute institute, StatementType statementType, PreparedStatement statement) throws SQLException;
	}

}
