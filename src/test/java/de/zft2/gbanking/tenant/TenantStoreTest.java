package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.messages.Messages;

class TenantStoreTest {

	@TempDir
	Path tempDir;

	private Locale previousLocale;

	@BeforeEach
	void setGermanLocale() {
		previousLocale = Messages.getLocale();
		Messages.setLocale(Locale.GERMAN);
	}

	@AfterEach
	void restoreLocale() {
		Messages.setLocale(previousLocale);
	}

	@Test
	void shouldCreateAuthenticateAndUpdateTenant() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));

		TenantProfile tenant = tenantStore.createTenant("georg", "secret".toCharArray());

		assertEquals("georg", tenant.username());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
		try (TenantSession authenticatedSession = tenantStore.authenticateSession(tenant.id(), "secret".toCharArray()).orElseThrow()) {
			assertEquals(tenant.id(), authenticatedSession.profile().id());
		}
		assertTrue(tenant.encryptionIterations() > 0);
		assertFalse(tenant.encryptionSalt().isBlank());
		assertFalse(tenant.wrappedKeyNonce().isBlank());
		assertFalse(tenant.wrappedDataKey().isBlank());
		TenantPaths tenantPaths = tenantStore.getTenantPaths(tenant.id());
		assertEquals(tempDir.resolve("data").resolve("tenant").resolve(tenant.id()), tenantPaths.tenantDirectory());
		assertTrue(Files.isDirectory(tenantPaths.databaseDirectory()));
		assertTrue(Files.isDirectory(tenantPaths.backupDirectory()));
		assertTrue(Files.isDirectory(tenantPaths.accountStatementsDirectory()));
		assertTrue(Files.isRegularFile(tempDir.resolve("data").resolve("tenants.properties")));

		TenantProfile updatedTenant = tenantStore.updateTenant(tenant.id(), "georg2", "secret".toCharArray(), "newsecret".toCharArray());

		assertEquals("georg2", updatedTenant.username());
		assertNotEquals(tenant.wrappedDataKey(), updatedTenant.wrappedDataKey());
		assertTrue(tenantStore.authenticate(updatedTenant.id(), "newsecret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectDuplicateUsernameIgnoringCase() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		tenantStore.createTenant("Georg", "secret".toCharArray());

		char[] secret2 = "secret2".toCharArray();
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant("georg", secret2));

		assertEquals("Der Benutzername ist bereits vergeben.", exception.getMessage());
	}

	@Test
	void shouldReserveDemoUsernameForDemoTenant() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		char[] password = "secret".toCharArray();

		IllegalArgumentException createException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant(" Demo ", password));

		assertEquals("Der Benutzername demo ist für den Demo-Mandanten reserviert. Bitte einen anderen Benutzernamen verwenden.",
				createException.getMessage());

		TenantProfile tenant = tenantStore.createTenant("alpha", password);
		String tenantId = tenant.id();
		char[] emptyPassword = new char[0];
		IllegalArgumentException updateException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.updateTenant(tenantId, "DEMO", password, emptyPassword));

		assertEquals(createException.getMessage(), updateException.getMessage());
	}

	@Test
	void shouldDeleteTenantMetadataWithoutTouchingOthers() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant01 = tenantStore.createTenant("alpha", "secret".toCharArray());
		TenantProfile tenant02 = tenantStore.createTenant("beta", "secret".toCharArray());

		Files.createDirectories(tenantStore.getTenantDirectory(tenant01.id()));
		Files.createDirectories(tenantStore.getTenantDirectory(tenant02.id()));

		tenantStore.deleteTenant(tenant01.id(), "secret".toCharArray());

		assertEquals(1, tenantStore.getTenants().size());
		assertEquals("beta", tenantStore.getTenants().get(0).username());
		assertTrue(Files.exists(tenantStore.getTenantDirectory(tenant01.id())));
		assertTrue(Files.exists(tenantStore.getTenantDirectory(tenant02.id())));
	}
}
