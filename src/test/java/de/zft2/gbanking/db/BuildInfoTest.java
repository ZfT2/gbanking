package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BuildInfoTest {

	@Test
	void getJavaVersion_shouldReturnSystemJavaVersion() {
		assertEquals(System.getProperty("java.version"), BuildInfo.getJavaVersion());
	}

	@Test
	void getJavaFxVersion_shouldReturnSystemJavaFxVersion() {
		String previous = System.getProperty("javafx.version");
		try {
			System.setProperty("javafx.version", "test-javafx-version");

			assertEquals("test-javafx-version", BuildInfo.getJavaFxVersion());
		} finally {
			if (previous == null) {
				System.clearProperty("javafx.version");
			} else {
				System.setProperty("javafx.version", previous);
			}
		}
	}

	@Test
	void getProgramVersion_shouldNeverExposeUnresolvedBuildPlaceholders() {
		String version = BuildInfo.getProgramVersion();

		assertNotNull(version);
		assertFalse(version.isBlank());
		assertFalse(version.contains("${project.version}"));
		assertFalse(version.contains("@project.version@"));
	}
}
