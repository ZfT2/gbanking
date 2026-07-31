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
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("uk_bankaccount_businesscase")));
		assertTrue(baselineStatements.stream().anyMatch(statement -> statement.contains("validate_bankaccount_insert")));
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
