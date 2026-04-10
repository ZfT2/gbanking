package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DbMigrationRunnerTest {

	@Test
	void shouldCompareVersionsNumerically() {
		assertTrue(DbMigrationRunner.compareVersions("0.1.10", "0.1.2") > 0);
		assertTrue(DbMigrationRunner.compareVersions("1.0.0", "0.9.9") > 0);
		assertTrue(DbMigrationRunner.compareVersions("0.1.0-SNAPSHOT", "0.1.0") == 0);
		assertTrue(DbMigrationRunner.compareVersions("0.1", "0.1.0") == 0);
		assertTrue(DbMigrationRunner.compareVersions(null, "0.0.1") < 0);
	}

	@Test
	void shouldTreatInvalidVersionPartsAsZero() {
		assertEquals(0, DbMigrationRunner.compareVersions("1.alpha.0", "1.0.0"));
		assertTrue(DbMigrationRunner.compareVersions("2.beta", "1.9.9") > 0);
	}
}
