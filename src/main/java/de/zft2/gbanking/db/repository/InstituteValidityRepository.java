package de.zft2.gbanking.db.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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
				statement.setString(2, observation.validFromType().name());
				statement.setString(3, observation.validToType() != null ? observation.validToType().name() : null);
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
				statement.setString(1, InstituteValidityDateType.FIRST_MISSING.name());
				statement.setTimestamp(2, TypeConverter.toSqlTimestampNow());
				statement.setInt(3, instituteId);
				statement.addBatch();
			}
			statement.executeBatch();
		} catch (SQLException exception) {
			throw new GBankingException("Could not close institute validity observations", exception);
		}
	}

	public record Observation(int instituteId, InstituteValidityDateType validFromType,
			InstituteValidityDateType validToType, int importHistoryId) {
	}
}
