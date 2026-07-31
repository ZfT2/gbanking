package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperatingSystemTest {

	private String originalOsName;

	@BeforeEach
	void rememberOriginalOsName() {
		originalOsName = System.getProperty("os.name");
	}

	@AfterEach
	void restoreOriginalOsName() {
		if (originalOsName == null) {
			System.clearProperty("os.name");
		} else {
			System.setProperty("os.name", originalOsName);
		}
	}

	@Test
	void currentShouldResolveWindowsMacAndFallbackLinux() {
		System.setProperty("os.name", "Windows 11");
		assertSame(OperatingSystem.WINDOWS, OperatingSystem.current());

		System.setProperty("os.name", "Mac OS X");
		assertSame(OperatingSystem.MAC, OperatingSystem.current());

		System.setProperty("os.name", "FreeBSD");
		assertSame(OperatingSystem.LINUX, OperatingSystem.current());
	}

	@Test
	void metadataShouldMatchReleaseArtifactsAndLaunchers() {
		assertEquals("-windows.zip", OperatingSystem.WINDOWS.assetSuffix());
		assertEquals("gbanking.bat", OperatingSystem.WINDOWS.launcherName());
		assertTrue(OperatingSystem.WINDOWS.isWindows());

		assertEquals("-linux.zip", OperatingSystem.LINUX.assetSuffix());
		assertEquals("gbanking.sh", OperatingSystem.LINUX.launcherName());
		assertFalse(OperatingSystem.LINUX.isWindows());

		assertEquals("-mac.zip", OperatingSystem.MAC.assetSuffix());
		assertEquals("gbanking.command", OperatingSystem.MAC.launcherName());
		assertFalse(OperatingSystem.MAC.isWindows());
	}
}
