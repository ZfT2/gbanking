package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DbRuntimeContextTest {

	@AfterEach
	void resetContext() {
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	@Test
	void setCurrentDbDirectory_shouldNormalizePath() {
		DbRuntimeContext.setCurrentDbDirectory("db/tenant/../tenant-a");

		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.getCurrentDbDirectory());
	}

	@Test
	void resolveDbDirectory_shouldReturnCurrentDirectoryForBlankInput() {
		DbRuntimeContext.setCurrentDbDirectory("db\\tenant-a");

		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory(null));
		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory(" "));
		assertEquals(Paths.get("db", "tenant-a").toString(), DbRuntimeContext.resolveDbDirectory("."));
	}

	@Test
	void resolveDbDirectory_shouldUpdateCurrentDirectoryForExplicitInput() {
		String resolved = DbRuntimeContext.resolveDbDirectory("db/tenant-b/../tenant-c");

		assertEquals(Paths.get("db", "tenant-c").toString(), resolved);
		assertEquals(Paths.get("db", "tenant-c").toString(), DbRuntimeContext.getCurrentDbDirectory());
	}
}
