package de.zft2.gbanking.db.dao.logic;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

	private static Logger log = LogManager.getLogger(StatementsLogicInstitute.class);

	@Override
	public Institute insertOrUpdateSingle(Institute institute) {
		StatementType statementType = getStatementTypeForInsertOrUpdate(institute);
		String sql = StatementsConfig.getSqlStatement(Institute.class, statementType);
		AbstractDaoMapper<Institute, Void> mapperBase = StatementsConfig.getMapperForDaoType(Institute.class);
		InstituteMapper mapper = (InstituteMapper) mapperBase;
		boolean insert = statementType == StatementType.INSERT;

		try (PreparedStatement ps = connection.prepareStatement(sql, insert ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
			mapper.setParamsFull(institute, ps);
			ps.executeUpdate();
			if (insert) {
				setGeneratedDbId(institute, ps);
			}

			upsertInstituteDetails(institute, statementType, mapper);
		} catch (SQLException e) {
			String errorKey = statementType == StatementType.UPDATE ? SqlErrors.ERROR_DB_UPDATE : SqlErrors.ERROR_DB_INSERT;
			log.error(getText(errorKey, institute.getId()), e);
			throw new GBankingException(getText(errorKey), e);
		}

		return institute;
	}

	private void upsertInstituteDetails(Institute institute, StatementType statementType, InstituteMapper mapper) throws SQLException {
		if (mapper.hasDkData(institute)) {
			try (PreparedStatement ps = connection.prepareStatement(
					statementType == StatementType.INSERT ? DaoSqlStatements.SQL_INSERT_INSTITUTE_DK : DaoSqlStatements.SQL_UPDATE_INSTIUTE_DK)) {
				mapper.setParamsDK(institute, statementType, ps);
				ps.executeUpdate();
			}
		} else {
			deleteInstituteDetails(DaoSqlStatements.SQL_DELETE_INSTIUTE_DK, institute);
		}

		if (mapper.hasDbbData(institute)) {
			try (PreparedStatement ps = connection.prepareStatement(
					statementType == StatementType.INSERT ? DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB : DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB)) {
				mapper.setParamsDBB(institute, statementType, ps);
				ps.executeUpdate();
			}
		} else {
			deleteInstituteDetails(DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB, institute);
		}

		if (mapper.hasEpcData(institute)) {
			try (PreparedStatement ps = connection.prepareStatement(
					statementType == StatementType.INSERT ? DaoSqlStatements.SQL_INSERT_INSTITUTE_EPC : DaoSqlStatements.SQL_UPDATE_INSTIUTE_EPC)) {
				mapper.setParamsEPC(institute, statementType, ps);
				ps.executeUpdate();
			}
		} else {
			deleteInstituteDetails(DaoSqlStatements.SQL_DELETE_INSTIUTE_EPC, institute);
		}

		if (mapper.hasDbbReachableData(institute)) {
			try (PreparedStatement ps = connection.prepareStatement(statementType == StatementType.INSERT
					? DaoSqlStatements.SQL_INSERT_INSTITUTE_DBB_REACHABLE
					: DaoSqlStatements.SQL_UPDATE_INSTIUTE_DBB_REACHABLE)) {
				mapper.setParamsDbbReachable(institute, statementType, ps);
				ps.executeUpdate();
			}
		} else {
			deleteInstituteDetails(DaoSqlStatements.SQL_DELETE_INSTIUTE_DBB_REACHABLE, institute);
		}

		if (mapper.hasAdditionalData(institute)) {
			try (PreparedStatement ps = connection.prepareStatement(statementType == StatementType.INSERT
					? DaoSqlStatements.SQL_INSERT_INSTITUTE_ADDITIONAL
					: DaoSqlStatements.SQL_UPDATE_INSTIUTE_ADDITIONAL)) {
				mapper.setParamsAdditional(institute, statementType, ps);
				ps.executeUpdate();
			}
		} else {
			deleteInstituteDetails(DaoSqlStatements.SQL_DELETE_INSTIUTE_ADDITIONAL, institute);
		}
	}

	private void deleteInstituteDetails(String sql, Institute institute) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, institute.getId());
			ps.executeUpdate();
		}
	}

	private void setGeneratedDbId(Institute institute, PreparedStatement ps) throws SQLException {
		try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
			if (generatedKeys.next()) {
				institute.setId(generatedKeys.getInt(1));
				return;
			}
		}
		throw new SQLException(getText(SqlErrors.ERROR_DB_NO_ID, institute.getClass().getName()));
	}

}
