package de.zft2.gbanking.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.gui.GBankingGui;
import de.zft2.gbanking.gui.dialog.tenant.TenantLoginDialog;
import de.zft2.gbanking.gui.progress.TenantDatabaseLifecycleProgressBarPanel;
import javafx.stage.Stage;

class TenantLoginDialogTest {

	@TempDir
	Path tempDir;

	private TenantStore tenantStore;
	private TenantSession session;
	private TenantLoginDialog loginDialog;

	@BeforeEach
	void createActiveSession() throws Exception {
		tenantStore = new TenantStore(tempDir);
		TenantProfile tenant = tenantStore.createTenant("alpha", "secret".toCharArray());
		session = tenantStore.authenticateSession(tenant.id(), "secret".toCharArray()).orElseThrow();
		loginDialog = new TenantLoginDialog(mock(GBankingGui.class));
		setField("activeSession", session);
		setField("activeTenantStore", tenantStore);
		TenantFileEncryptionContext.activate(session);
	}

	@AfterEach
	void releaseSession() {
		loginDialog.releaseTenantLock();
	}

	@Test
	void successfulCloseShouldClearKeyAndRequireFreshAuthentication() throws Exception {
		try (var progress = mockLifecycleProgress(false)) {
			assertTrue(loginDialog.closeTenantDatabase());

			assertThrows(IllegalStateException.class, () -> session.dataKey().toSecretKey());
			assertThrows(IllegalStateException.class, () -> TenantFileEncryptionContext.encrypt(new byte[0], tempDir.resolve("statement.enc")));
			assertFalse(loginDialog.reopenActiveTenant());
			assertTrue(loginDialog.getActiveBackupDirectory().isEmpty());
			assertTrue(tenantStore.authenticateSession(session.profile().id(), new char[0]).isEmpty());
			assertTrue(tenantStore.authenticateSession(session.profile().id(), "wrong".toCharArray()).isEmpty());
			try (TenantSession freshSession = tenantStore.authenticateSession(session.profile().id(), "secret".toCharArray()).orElseThrow()) {
				assertNotNull(freshSession.dataKey().toSecretKey());
			}
			assertEquals(1, progress.constructed().size());
		}
	}

	@Test
	void deletedTenantShouldNotBeRecreatedUsingPreviousSession() {
		try (var progress = mockLifecycleProgress(false)) {
			assertTrue(loginDialog.closeTenantDatabase());
			tenantStore.deleteTenantAndData(session.profile().id(), "secret".toCharArray());

			assertFalse(loginDialog.reopenActiveTenant());
			assertFalse(Files.exists(session.paths().tenantDirectory()));
			assertTrue(tenantStore.authenticateSession(session.profile().id(), "secret".toCharArray()).isEmpty());
			assertEquals(1, progress.constructed().size());
		}
	}

	@Test
	void failedCloseShouldKeepSessionAvailableForRecovery() {
		try (var progress = mockLifecycleProgress(true)) {
			assertFalse(loginDialog.closeTenantDatabase());

			assertNotNull(session.dataKey().toSecretKey());
			assertEquals(session.paths().backupDirectory(), loginDialog.getActiveBackupDirectory().orElseThrow());
			assertEquals(1, progress.constructed().size());
		}
	}

	private MockedConstruction<TenantDatabaseLifecycleProgressBarPanel> mockLifecycleProgress(boolean failed) {
		Stage progressWindow = mock(Stage.class);
		return mockConstruction(TenantDatabaseLifecycleProgressBarPanel.class, (panel, context) -> {
			when(panel.createNewFileImportProgressBarWindow()).thenReturn(progressWindow);
			when(panel.hasFailed()).thenReturn(failed);
		});
	}

	private void setField(String name, Object value) throws Exception {
		Field field = TenantLoginDialog.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(loginDialog, value);
	}
}
