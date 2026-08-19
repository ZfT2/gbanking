package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.messages.Messages;

class TenantStoreAdditionalTest {

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
	void shouldPersistTenantsAcrossReload() {
		Path dataDirectory = tempDir.resolve("data");
		TenantStore writerStore = new TenantStore(dataDirectory);
		TenantProfile createdTenant = writerStore.createTenant("alpha", "secret".toCharArray());

		TenantStore readerStore = new TenantStore(dataDirectory);
		Optional<TenantProfile> reloadedTenant = readerStore.findById(createdTenant.id());

		assertTrue(reloadedTenant.isPresent());
		assertEquals("alpha", reloadedTenant.get().username());
		assertTrue(readerStore.authenticate(createdTenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectUpdateWhenOldPasswordDoesNotMatch() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());

		final String tenantId = tenant.id();
		final char[] wrong = "wrong".toCharArray();
		final char[] newsecret = "newsecret".toCharArray();
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.updateTenant(tenantId, "alpha2", wrong, newsecret));

		assertEquals("Das alte Passwort ist nicht korrekt.", exception.getMessage());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldUpdateUsernameWithoutChangingPasswordWhenNewPasswordIsMissing() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());

		TenantProfile updatedTenant = tenantStore.updateTenant(tenant.id(), "alpha2", "secret".toCharArray(), new char[0]);

		assertEquals("alpha2", updatedTenant.username());
		assertEquals(tenant.wrappedDataKey(), updatedTenant.wrappedDataKey());
		assertTrue(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldRejectDeleteWhenPasswordDoesNotMatch() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());
		String tenantId = tenant.id();
		char[] wrongPassword = "wrong".toCharArray();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.deleteTenant(tenantId, wrongPassword));

		assertEquals("Das Passwort ist nicht korrekt.", exception.getMessage());
		assertTrue(tenantStore.findById(tenant.id()).isPresent());
	}

	@Test
	void shouldRejectBlankUsernameAndMissingPassword() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));

		char[] secret = "secret".toCharArray();
		IllegalArgumentException usernameException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant(" ", secret));
		IllegalArgumentException passwordException = assertThrows(IllegalArgumentException.class,
				() -> tenantStore.createTenant("alpha", new char[0]));

		assertEquals("Bitte einen Benutzernamen eingeben.", usernameException.getMessage());
		assertEquals("Bitte ein Passwort eingeben.", passwordException.getMessage());
	}

	@Test
	void findByIdAndAuthenticateShouldReturnEmptyForBlankOrUnknownIds() {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		tenantStore.createTenant("alpha", "secret".toCharArray());

		assertTrue(tenantStore.findById(null).isEmpty());
		assertTrue(tenantStore.findById(" ").isEmpty());
		assertTrue(tenantStore.findById("unknown").isEmpty());
		assertTrue(tenantStore.authenticate("unknown", "secret".toCharArray()).isEmpty());
		assertFalse(tenantStore.authenticate(" ", "secret".toCharArray()).isPresent());
	}

	@Test
	void shouldRewrapExistingEncryptedTenantFilesWhenPasswordChanges() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());
		java.nio.file.Path backupFile;
		java.nio.file.Path statementFile;
		try (TenantSession session = tenantStore.authenticateSession(tenant.id(), "secret".toCharArray()).orElseThrow()) {
			java.nio.file.Files.writeString(session.paths().databaseFile(), "database-content");
			statementFile = session.paths().accountStatementsDirectory().resolve("statement.pdf.enc");
			new TenantEncryptionManager().writeEncryptedContent(statementFile, session,
					output -> output.write("statement-content".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			new TenantBackupManager().backupTenantDatabase(session);
			backupFile = session.paths().backupDirectory().resolve("gbanking.db.backup_on_open.gbbackup");
			new TenantDatabaseLifecycleManager().closeAndEncryptDatabase(session, null);
		}

		TenantProfile updatedTenant = tenantStore.updateTenant(tenant.id(), "alpha", "secret".toCharArray(), "newsecret".toCharArray());

		assertFalse(tenantStore.authenticate(tenant.id(), "secret".toCharArray()).isPresent());
		try (TenantSession updatedSession = tenantStore.authenticateSession(updatedTenant.id(), "newsecret".toCharArray()).orElseThrow()) {
			new TenantDatabaseLifecycleManager().prepareDatabaseForOpen(updatedSession, null);
			assertEquals("database-content", java.nio.file.Files.readString(updatedSession.paths().databaseFile()));
		}
		java.nio.file.Path restoredBackup = tempDir.resolve("restored.zip");
		new TenantEncryptionManager().decryptContainer(backupFile, restoredBackup, "newsecret".toCharArray());
		assertTrue(java.nio.file.Files.size(restoredBackup) > 0L);
		java.nio.file.Path restoredStatement = tempDir.resolve("restored-statement.pdf");
		new TenantEncryptionManager().decryptContainer(statementFile, restoredStatement, "newsecret".toCharArray());
		assertEquals("statement-content", java.nio.file.Files.readString(restoredStatement));
		TenantEncryptionManager encryptionService = new TenantEncryptionManager();
		java.nio.file.Path wrongBackup = tempDir.resolve("wrong.zip");
		java.nio.file.Path wrongStatement = tempDir.resolve("wrong.pdf");
		char[] oldPassword = "secret".toCharArray();
		assertThrows(IllegalStateException.class,
				() -> encryptionService.decryptContainer(backupFile, wrongBackup, oldPassword));
		assertThrows(IllegalStateException.class,
				() -> encryptionService.decryptContainer(statementFile, wrongStatement, oldPassword));
	}
}
