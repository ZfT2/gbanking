package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AppPathsTest {

	@Test
	void shouldResolveRelativePathsAgainstApplicationBaseDirectory() {
		Path baseDirectory = AppPaths.getApplicationBaseDirectory();

		assertEquals(baseDirectory.resolve("db").normalize(), AppPaths.resolveInApplicationDirectory("db"));
		assertEquals(baseDirectory.resolve(Path.of("properties", "gui.properties")).normalize(),
				AppPaths.resolveInApplicationDirectory("properties", "gui.properties"));
	}

	@Test
	void shouldKeepAbsolutePathsUnchanged() {
		Path absolutePath = Path.of(System.getProperty("java.io.tmpdir")).resolve("gbanking-path-test").toAbsolutePath().normalize();

		assertEquals(absolutePath, AppPaths.resolveInApplicationDirectory(absolutePath));
		assertEquals(absolutePath, AppPaths.resolveInApplicationDirectory(absolutePath.toString()));
	}

	@Test
	void shouldResolveApplicationBaseDirectoryToProjectOrDistributionRoot() {
		assertTrue(AppPaths.resolveInApplicationDirectory("LICENSE").toFile().exists());
	}
}
