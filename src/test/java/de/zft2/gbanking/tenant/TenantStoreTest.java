package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldCreateAuthenticateAndUpdateTenant() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));

		TenantProfile tenant = tenantStore.createTenant("georg", "secret".toCharArray());

		assertEquals("georg", tenant.username());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());

		TenantProfile updatedTenant = tenantStore.updateTenant(tenant.id(), "georg2", "secret".toCharArray(), "newsecret".toCharArray());

		assertEquals("georg2", updatedTenant.username());
		assertNotEquals(tenant.passwordHash(), updatedTenant.passwordHash());
		assertTrue(tenantStore.authenticate(updatedTenant.id(), "newsecret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectDuplicateUsernameIgnoringCase() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));
		tenantStore.createTenant("Georg", "secret".toCharArray());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant("georg", "secret2".toCharArray()));

		assertEquals("Der Benutzername ist bereits vergeben.", exception.getMessage());
	}

	@Test
	void shouldDeleteTenantMetadataWithoutTouchingOthers() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));
		TenantProfile tenant01 = tenantStore.createTenant("alpha", "secret".toCharArray());
		TenantProfile tenant02 = tenantStore.createTenant("beta", "secret".toCharArray());

		Files.createDirectories(tenantStore.getTenantDirectory(tenant01.id()));
		Files.createDirectories(tenantStore.getTenantDirectory(tenant02.id()));

		tenantStore.deleteTenant(tenant01.id());

		assertEquals(1, tenantStore.getTenants().size());
		assertEquals("beta", tenantStore.getTenants().get(0).username());
		assertTrue(Files.exists(tenantStore.getTenantDirectory(tenant01.id())));
		assertTrue(Files.exists(tenantStore.getTenantDirectory(tenant02.id())));
	}
}
