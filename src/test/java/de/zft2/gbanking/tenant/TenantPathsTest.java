package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantPathsTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldResolveAllTenantDirectories() {
		Path tenantDirectory = tempDir.resolve("data").resolve("tenant").resolve(UUID.randomUUID().toString());
		TenantPaths tenantPaths = new TenantPaths(tenantDirectory);

		assertEquals(tenantDirectory.resolve("db"), tenantPaths.databaseDirectory());
		assertEquals(tenantDirectory.resolve("db").resolve("gbanking.db"), tenantPaths.databaseFile());
		assertEquals(tenantDirectory.resolve("backup"), tenantPaths.backupDirectory());
		assertEquals(tenantDirectory.resolve("accountStatements"), tenantPaths.accountStatementsDirectory());
		assertEquals(tenantPaths, TenantPaths.fromDatabaseDirectory(tenantPaths.databaseDirectory()));
	}

	@Test
	void shouldRecognizeDataDirectoryOnlyForNewTenantLayout() {
		Path dataDirectory = tempDir.resolve("data");
		Path databaseDirectory = dataDirectory.resolve("tenant").resolve(UUID.randomUUID().toString()).resolve("db");

		assertEquals(dataDirectory, TenantPaths.findDataDirectory(databaseDirectory).orElseThrow());
		assertTrue(TenantPaths.findDataDirectory(dataDirectory.resolve(UUID.randomUUID().toString())).isEmpty());
	}

	@Test
	void shouldKeepEncryptedDataSharedWhenDatabaseUsesLocalWorkDirectory() {
		Path dataDirectory = tempDir.resolve("shared-data");
		Path tenantDirectory = dataDirectory.resolve("tenant").resolve(UUID.randomUUID().toString());
		Path workDatabaseDirectory = tempDir.resolve("application").resolve("work").resolve("tenant")
				.resolve(tenantDirectory.getFileName()).resolve("db");
		TenantPaths tenantPaths = new TenantPaths(tenantDirectory, workDatabaseDirectory);

		assertEquals(workDatabaseDirectory.resolve("gbanking.db"), tenantPaths.databaseFile());
		assertEquals(tenantDirectory.resolve("db").resolve("gbanking.db.enc"), tenantPaths.encryptedDatabaseFile());
		assertEquals(tenantDirectory.resolve("db").resolve("gbanking.db.enc.tmp"), tenantPaths.databaseEncryptionTempFile());
		assertEquals(tenantDirectory.resolve("backup"), tenantPaths.backupDirectory());
		assertEquals(tenantDirectory.resolve("accountStatements"), tenantPaths.accountStatementsDirectory());
		assertEquals(dataDirectory, tenantPaths.dataDirectory());
		assertTrue(tenantPaths.usesSeparateDatabaseDirectory());
	}

	@Test
	void shouldKeepStandaloneDatabaseDirectoriesUsable() {
		Path standaloneDatabaseDirectory = tempDir.resolve("standalone");

		assertEquals(standaloneDatabaseDirectory.resolve("accountStatements"),
				TenantPaths.resolveAccountStatementsDirectory(standaloneDatabaseDirectory));
	}
}
