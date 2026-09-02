package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.SqlErrors;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.db.dao.mapper.InstituteMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicInstitute extends StatementsLogicDefault<Institute> implements StatementsLogic<Institute> {

	private static final Logger log = LogManager.getLogger(StatementsLogicInstitute.class);

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
			log.error(getText(errorKey, institute.getId()), exception);
			throw new GBankingException(getText(errorKey), exception);
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
		List<Institute> inserts = new ArrayList<>();
		List<Institute> updates = new ArrayList<>();
		try {
			for (Institute institute : institutes) {
				if (institute.getId() > 0) {
					updates.add(institute);
				} else {
					inserts.add(institute);
				}
			}
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
			log.error(getText(SqlErrors.ERROR_DB), exception);
			throw new GBankingException(getText(SqlErrors.ERROR_DB), exception);
		}
	}

	private void persistInstituteDetails(Institute institute, StatementType statementType, InstituteMapper mapper)
			throws SQLException {
		persistInstituteDetail(institute, statementType, mapper::hasDkData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DK, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DK,
				(entity, statement) -> mapper.setParamsDK(entity, StatementType.UPDATE, statement),
				(entity, statement) -> mapper.setParamsDK(entity, StatementType.INSERT, statement));
		persistInstituteDetail(institute, statementType, mapper::hasDbbData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB,
				(entity, statement) -> mapper.setParamsDBB(entity, StatementType.UPDATE, statement),
				(entity, statement) -> mapper.setParamsDBB(entity, StatementType.INSERT, statement));
		persistInstituteDetail(institute, statementType, mapper::hasEpcData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_EPC, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_EPC,
				(entity, statement) -> mapper.setParamsEPC(entity, StatementType.UPDATE, statement),
				(entity, statement) -> mapper.setParamsEPC(entity, StatementType.INSERT, statement));
		persistInstituteDetail(institute, statementType, mapper::hasDbbReachableData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB_REACHABLE,
				(entity, statement) -> mapper.setParamsDbbReachable(entity, StatementType.UPDATE, statement),
				(entity, statement) -> mapper.setParamsDbbReachable(entity, StatementType.INSERT, statement));
		persistInstituteDetail(institute, statementType, mapper::hasAdditionalData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_ADDITIONAL,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_ADDITIONAL,
				(entity, statement) -> mapper.setParamsAdditional(entity, StatementType.UPDATE, statement),
				(entity, statement) -> mapper.setParamsAdditional(entity, StatementType.INSERT, statement));
	}

	private void persistInstituteDetail(Institute institute, StatementType statementType,
			Predicate<Institute> hasDetails, String updateSql, String insertSql, String deleteSql,
			SqlBatchBinder<Institute> updateBinder, SqlBatchBinder<Institute> insertBinder) throws SQLException {
		if (!hasDetails.test(institute)) {
			if (statementType == StatementType.UPDATE) {
				deleteInstituteDetails(deleteSql, institute);
			}
			return;
		}
		if (statementType == StatementType.INSERT) {
			executeCachedUpdateExactlyOnce(insertSql, statement -> insertBinder.bind(institute, statement));
		} else {
			updateThenInsertInstituteDetail(institute, updateSql, insertSql, updateBinder, insertBinder);
		}
	}

	private void updateThenInsertInstituteDetail(Institute institute, String updateSql, String insertSql,
			SqlBatchBinder<Institute> updateBinder, SqlBatchBinder<Institute> insertBinder) throws SQLException {
		int updateCount = executeCachedUpdate(updateSql, statement -> updateBinder.bind(institute, statement));
		if (updateCount == 0) {
			executeCachedUpdateExactlyOnce(insertSql, statement -> insertBinder.bind(institute, statement));
		} else if (updateCount != 1) {
			throw new SQLException("Database detail update did not affect at most one row");
		}
	}

	private void deleteInstituteDetails(String sql, Institute institute) throws SQLException {
		executeCachedUpdate(sql, statement -> statement.setInt(1, institute.getId()));
	}

	private void persistInstituteDetails(List<Institute> inserts, List<Institute> updates, InstituteMapper mapper)
			throws SQLException {
		insertInstituteDetails(inserts, mapper::hasDkData, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, mapper::hasDbbData, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, mapper::hasEpcData, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, mapper::hasDbbReachableData,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.INSERT, statement));
		insertInstituteDetails(inserts, mapper::hasAdditionalData,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL,
				(institute, statement) -> mapper.setParamsAdditional(institute, StatementType.INSERT, statement));

		persistDetailBatch(updates, mapper::hasDkData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DK, DaoSqlStatements.SQL_INSERT_INSTITUTE_DK,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DK,
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDK(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, mapper::hasDbbData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB, DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB,
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDBB(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, mapper::hasEpcData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_EPC, DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_EPC,
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsEPC(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, mapper::hasDbbReachableData,
				DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE,
				DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB_REACHABLE,
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.UPDATE, statement),
				(institute, statement) -> mapper.setParamsDbbReachable(institute, StatementType.INSERT, statement));
		persistDetailBatch(updates, mapper::hasAdditionalData,
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
		List<Institute> deletes = matching(updates, hasDetails.negate());
		if (!deletes.isEmpty()) {
			executeCachedBatch(deleteSql, deletes,
					(institute, statement) -> statement.setInt(1, institute.getId()));
		}
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

	private static List<Institute> matching(Iterable<Institute> institutes, Predicate<Institute> predicate) {
		List<Institute> matches = new ArrayList<>();
		for (Institute institute : institutes) {
			if (predicate.test(institute)) {
				matches.add(institute);
			}
		}
		return matches;
	}

}
