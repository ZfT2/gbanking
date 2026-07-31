package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantLockTest {

	@TempDir
	private Path tempDir;

	@Test
	void shouldPreventSecondLockForSameTenantDirectory() throws Exception {
		Path tenantDirectory = tempDir.resolve("tenant-a");

		try (TenantLock lock = TenantLock.tryAcquire(tenantDirectory).orElseThrow()) {
			Optional<TenantLock> secondLock = TenantLock.tryAcquire(tenantDirectory);

			assertTrue(secondLock.isEmpty());
			assertTrue(Files.exists(lock.getLockFile()));
		}
	}

	@Test
	void shouldAllowLockAgainAfterRelease() throws Exception {
		Path tenantDirectory = tempDir.resolve("tenant-a");
		Path lockFile;
		try (TenantLock firstLock = TenantLock.tryAcquire(tenantDirectory).orElseThrow()) {
			lockFile = firstLock.getLockFile();
		}

		try (TenantLock secondLock = TenantLock.tryAcquire(tenantDirectory).orElseThrow()) {
			assertEquals(lockFile, secondLock.getLockFile());
		}
	}
}
