package de.zft2.gbanking.db.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DaoSqlStatements;
import de.zft2.gbanking.db.dao.enu.InstituteValidityDateType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class InstituteValidityRepository {

	public void markSeen(Collection<Observation> observations) {
		if (observations.isEmpty()) {
			return;
		}
		try (PreparedStatement statement = DBController.getConnection()
				.prepareStatement(DaoSqlStatements.SQL_UPSERT_INSTITUTE_VALIDITY_SEEN)) {
			for (Observation observation : observations) {
				statement.setInt(1, observation.instituteId());
				statement.setInt(2, observation.validFromType().getDbStateId());
				setEnumId(3, observation.validToType(), statement);
				statement.setInt(4, observation.importHistoryId());
				statement.setInt(5, observation.importHistoryId());
				statement.setTimestamp(6, TypeConverter.toSqlTimestampNow());
				statement.addBatch();
			}
			statement.executeBatch();
		} catch (SQLException exception) {
			throw new GBankingException("Could not update institute validity observations", exception);
		}
	}

	public void close(Collection<Integer> instituteIds) {
		if (instituteIds.isEmpty()) {
			return;
		}
		try (PreparedStatement statement = DBController.getConnection()
				.prepareStatement(DaoSqlStatements.SQL_CLOSE_INSTITUTE_VALIDITY)) {
			for (Integer instituteId : instituteIds) {
				statement.setInt(1, InstituteValidityDateType.SOURCE_DATE.getDbStateId());
				statement.setInt(2, InstituteValidityDateType.FIRST_MISSING.getDbStateId());
				statement.setTimestamp(3, TypeConverter.toSqlTimestampNow());
				statement.setInt(4, instituteId);
				statement.addBatch();
			}
			statement.executeBatch();
		} catch (SQLException exception) {
			throw new GBankingException("Could not close institute validity observations", exception);
		}
	}

	private static void setEnumId(int index, InstituteValidityDateType value, PreparedStatement statement)
			throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.INTEGER);
		} else {
			statement.setInt(index, value.getDbStateId());
		}
	}

	public record Observation(int instituteId, InstituteValidityDateType validFromType,
			InstituteValidityDateType validToType, int importHistoryId) {
	}
}
