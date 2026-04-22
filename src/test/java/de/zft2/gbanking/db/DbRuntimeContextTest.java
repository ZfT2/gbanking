package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DbRuntimeContext;

class DbRuntimeContextTest {

	@AfterEach
	void resetContext() {
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	@Test
	void setCurrentDbDirectory_shouldNormalizePath() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant/../tenant-a");

		assertEquals("db\\tenant-a", DbRuntimeContext.getCurrentDbDirectory());
	}

	@Test
	void resolveDbDirectory_shouldReturnCurrentDirectoryForBlankInput() {
		DbRuntimeContext.setCurrentDbDirectory("db\\tenant-a");

		assertEquals("db\\tenant-a", DbRuntimeContext.resolveDbDirectory(null));
		assertEquals("db\\tenant-a", DbRuntimeContext.resolveDbDirectory(" "));
		assertEquals("db\\tenant-a", DbRuntimeContext.resolveDbDirectory("."));
	}

	@Test
	void resolveDbDirectory_shouldUpdateCurrentDirectoryForExplicitInput() {
		String resolved = DbRuntimeContext.resolveDbDirectory("db/tenant-b/../tenant-c");

		assertEquals("db\\tenant-c", resolved);
		assertEquals("db\\tenant-c", DbRuntimeContext.getCurrentDbDirectory());
	}
}
