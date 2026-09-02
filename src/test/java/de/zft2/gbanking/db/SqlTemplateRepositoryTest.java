package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SqlTemplateRepositoryTest {

	@Test
	void getDml_shouldResolvePlaceholdersFromSharedTemplates() {
		String sql = SqlTemplateRepository.getDml("SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS");

		assertTrue(sql.contains("accountState"));
		assertTrue(sql.contains("updatedAt"));
		assertFalse(sql.contains("${"));
	}

	@Test
	void getDdl_shouldReturnConcreteCreateStatement() {
		String sql = SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_INSTITUTE");

		assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS institute_db.institute"));
		assertTrue(sql.contains("stateType"));
	}

	@Test
	void getTemplate_shouldFailForUnknownKey() {
		assertThrows(IllegalArgumentException.class, () -> SqlTemplateRepository.getDml("UNKNOWN_KEY"));
		assertThrows(IllegalArgumentException.class, () -> SqlTemplateRepository.getDdl("UNKNOWN_KEY"));
	}

	@Test
	void baselineStatements_shouldContainExecutableSetupStatementsOnly() {
		List<String> baselineStatements = SqlTemplateRepository.getBaselineStatements();

		assertFalse(baselineStatements.isEmpty());
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("CREATE TABLE IF NOT EXISTS institute_db.institute")));
		assertFalse(baselineStatements.stream().anyMatch(statement -> statement.contains("SQL_MIGRATION_")));
	}

	@Test
	void baselineStatements_shouldContainIntegrityConstraintsForFreshSchemas() {
		List<String> baselineStatements = SqlTemplateRepository.getBaselineStatements();

		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("validate_moneytransfer_insert")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("set_null_booking_cross_reference_delete")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("UNIQUE (account_id, businessCase_id)")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("validate_bankaccount_insert")));
	}

	@Test
	void bookingQueries_shouldUseConcreteIdentifiersAndBookingTypeParameters() {
		String bookingById = SqlTemplateRepository.getDml("SQL_SELECT_BOOKING_FULL_BY_ID");
		String crossBookings = SqlTemplateRepository.getDml("SQL_FIND_CROSS_BOOKINGS_FULL");

		assertTrue(bookingById.contains("WHERE b.id = ?"));
		assertFalse(bookingById.contains("${"));
		assertTrue(crossBookings.contains("(ba.iban = ? OR ba.number = ?)"));
		assertTrue(crossBookings.contains("b.bookingType NOT IN (?, ?)"));
		assertFalse(crossBookings.contains("b.bookingType NOT IN (5, 6)"));
		assertFalse(crossBookings.contains("LIKE ?"));
		assertFalse(crossBookings.contains("'REBOOKING_IN'"));
	}

	@Test
	void relationAndMessageQueries_shouldUseStableOrdering() {
		assertTrue(SqlTemplateRepository.getDml("SQL_SELECT_ALL_BANKACCOUNTS")
				.endsWith("ORDER BY ba.bankAccess_id, ba.id"));
		assertTrue(SqlTemplateRepository.getDml("SQL_SELECT_ALL_BANKACCOUNTS_BY_BANKACCESS")
				.endsWith("ORDER BY ba.id"));
		assertTrue(SqlTemplateRepository.getDml("SQL_SELECT_BANKACCOUNTS_BY_BANKACCESS_IDS")
				.endsWith("ORDER BY ba.bankAccess_id, ba.id"));
		assertTrue(SqlTemplateRepository.getDml("SQL_SELECT_ALL_BANK_MESSAGES_BY_BANKACCESS")
				.endsWith("ORDER BY versionDate DESC, retrievedAt DESC, code, description, id DESC"));
		assertTrue(SqlTemplateRepository.getDml("SQL_SELECT_ALL_BANK_MESSAGES")
				.endsWith("ORDER BY bankName, versionDate DESC, retrievedAt DESC, code, description, id DESC"));
	}

	@Test
	void instituteDetailWrites_shouldTargetAttachedDatabaseExplicitly() {
		assertTrue(SqlTemplateRepository.getDml("SQL_INSERT_INSTITUTE_DK")
				.startsWith("INSERT INTO institute_db.instituteDk"));
		assertTrue(SqlTemplateRepository.getDml("SQL_INSERT_INSTITUTE_DBB")
				.startsWith("INSERT INTO institute_db.instituteDbb"));
		assertTrue(SqlTemplateRepository.getDml("SQL_INSERT_INSTITUTE_EPC")
				.startsWith("INSERT INTO institute_db.instituteEpc"));
		assertTrue(SqlTemplateRepository.getDml("SQL_DELETE_INSTITUTE_DK")
				.startsWith("DELETE FROM institute_db.instituteDk"));
		assertTrue(SqlTemplateRepository.getDml("SQL_DELETE_INSTITUTE_DBB")
				.startsWith("DELETE FROM institute_db.instituteDbb"));
		assertTrue(SqlTemplateRepository.getDml("SQL_DELETE_INSTITUTE_EPC")
				.startsWith("DELETE FROM institute_db.instituteEpc"));
	}

	@Test
	void parameterDataWrites_shouldUseFixedSingleRowTemplates() {
		String parameterDataInsert = SqlTemplateRepository.getDml("SQL_INSERT_PARAMETERDATA");
		String relationInsert = SqlTemplateRepository.getDml("SQL_UPSERT_BANKACCESS_PARAMETERDATA");
		String relationDelete = SqlTemplateRepository.getDml("SQL_DELETE_BANKACCESS_PARAMETERDATA_BY_KEY");

		assertFalse(parameterDataInsert.contains("%s"));
		assertFalse(relationInsert.contains("%s"));
		assertFalse(relationDelete.contains("%s"));
		assertEquals(3L, parameterDataInsert.chars().filter(character -> character == '?').count());
		assertEquals(5L, relationInsert.chars().filter(character -> character == '?').count());
		assertEquals(3L, relationDelete.chars().filter(character -> character == '?').count());
	}

	@Test
	void getDdl_shouldParseFormattedTriggerBodiesWithInnerSemicolons() {
		String sql = SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_TRIGGER_MONEYTRANSFER_VALIDATE_INSERT");

		assertTrue(sql.contains("CREATE TRIGGER IF NOT EXISTS validate_moneytransfer_insert"));
		assertTrue(sql.contains("SELECT RAISE(FAIL, \"invalid moneytransfer data\");"));
		assertTrue(sql.contains("END"));
	}

	@Test
	void versionScripts_shouldBeAvailableInVersionOrder() {
		List<SqlTemplateRepository.VersionScript> versionScripts = SqlTemplateRepository.getVersionScripts();

		assertFalse(versionScripts.isEmpty());
		SqlTemplateRepository.VersionScript baselineScript = SqlTemplateRepository.getBaselineVersionScript();
		assertNotNull(baselineScript);
		assertEqualsByVersion("0.1.0", baselineScript.getVersion());
		assertEqualsByVersion(baselineScript.getVersion(), versionScripts.get(0).getVersion());
		assertTrue(baselineScript.getSettingKey().startsWith("db.migration."));
		assertTrue(baselineScript.getResource().startsWith("sql/ddl/"));
		assertTrue(versionScripts.stream().anyMatch(script -> "0.4.1".equals(script.getVersion())));

		for (int i = 1; i < versionScripts.size(); i++) {
			String previous = versionScripts.get(i - 1).getVersion();
			String current = versionScripts.get(i).getVersion();
			assertTrue(DbMigrationRunner.compareVersions(previous, current) <= 0);
		}
	}

	private static void assertEqualsByVersion(String expected, String actual) {
		assertEquals(0, DbMigrationRunner.compareVersions(expected, actual),
				() -> "Expected version " + expected + " but was " + actual);
	}
}
