package de.zft2.gbanking.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.tenant.TenantSession;
import de.zft2.gbanking.tenant.TenantStore;

class DemoTenantManagerTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldCreateAndResetDemoTenant() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"), tempDir.resolve("work"));
		DemoTenantManager manager = new DemoTenantManager(tenantStore);

		String firstTenantId;
		Path firstTenantDirectory;
		Path firstWorkTenantDirectory;
		try (TenantSession firstSession = manager.createFreshDemoSession()) {
			firstTenantId = firstSession.profile().id();
			firstTenantDirectory = firstSession.paths().tenantDirectory();
			firstWorkTenantDirectory = firstSession.paths().databaseDirectory().getParent();
			Files.writeString(firstTenantDirectory.resolve("reset-marker.txt"), "old demo data");
			Files.writeString(firstSession.paths().databaseDirectory().resolve("reset-marker.txt"), "old working data");
			assertEquals(TenantStore.DEMO_USERNAME, firstSession.profile().username());
		}

		try (TenantSession secondSession = manager.createFreshDemoSession()) {
			assertNotEquals(firstTenantId, secondSession.profile().id());
			assertEquals(TenantStore.DEMO_USERNAME, secondSession.profile().username());
			assertTrue(tenantStore.authenticate(secondSession.profile().id(), "demo".toCharArray()).isPresent());
		}

		assertFalse(Files.exists(firstTenantDirectory));
		assertFalse(Files.exists(firstWorkTenantDirectory));
		assertEquals(1, tenantStore.getTenants().size());
	}
}
