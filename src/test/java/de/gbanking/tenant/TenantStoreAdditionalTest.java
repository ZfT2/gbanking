package de.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantStoreAdditionalTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldPersistTenantsAcrossReload() {
		Path dbDirectory = tempDir.resolve("db");
		TenantStore writerStore = new TenantStore(dbDirectory);
		TenantProfile createdTenant = writerStore.createTenant("alpha", "secret".toCharArray());

		TenantStore readerStore = new TenantStore(dbDirectory);
		Optional<TenantProfile> reloadedTenant = readerStore.findById(createdTenant.id());

		assertTrue(reloadedTenant.isPresent());
		assertEquals("alpha", reloadedTenant.get().username());
		assertTrue(readerStore.authenticate(createdTenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectUpdateWhenOldPasswordDoesNotMatch() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.updateTenant(tenant.id(), "alpha2", "wrong".toCharArray(), "newsecret".toCharArray()));

		assertEquals("Das alte Passwort ist nicht korrekt.", exception.getMessage());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldUpdateUsernameWithoutChangingPasswordWhenNewPasswordIsMissing() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());

		TenantProfile updatedTenant = tenantStore.updateTenant(tenant.id(), "alpha2", "secret".toCharArray(), new char[0]);

		assertEquals("alpha2", updatedTenant.username());
		assertEquals(tenant.passwordHash(), updatedTenant.passwordHash());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectBlankUsernameAndMissingPassword() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));

		IllegalArgumentException usernameException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant(" ", "secret".toCharArray()));
		IllegalArgumentException passwordException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant("alpha", new char[0]));

		assertEquals("Bitte einen Benutzernamen eingeben.", usernameException.getMessage());
		assertEquals("Bitte ein Passwort eingeben.", passwordException.getMessage());
	}

	@Test
	void findByIdAndAuthenticateShouldReturnEmptyForBlankOrUnknownIds() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("db"));
		tenantStore.createTenant("alpha", "secret".toCharArray());

		assertTrue(tenantStore.findById(null).isEmpty());
		assertTrue(tenantStore.findById(" ").isEmpty());
		assertTrue(tenantStore.findById("unknown").isEmpty());
		assertTrue(tenantStore.authenticate("unknown", "secret".toCharArray()).isEmpty());
		assertFalse(tenantStore.authenticate(" ", "secret".toCharArray()).isPresent());
	}
}
