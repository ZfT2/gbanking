package de.zft2.gbanking.gui;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceMode;
import de.zft2.gbanking.gui.BackgroundActionCoordinator.QuiesceResult;
import de.zft2.gbanking.gui.dialog.tenant.TenantLoginDialog;
import javafx.application.Platform;
import javafx.stage.Stage;

class GBankingGuiTenantSwitchTest {

	@Test
	void cancellingTenantSelectionShouldExitWithoutRestoringPreviousTenant() throws Exception {
		GBankingGui gui = mock(GBankingGui.class, CALLS_REAL_METHODS);
		TenantLoginDialog loginDialog = mock(TenantLoginDialog.class);
		Stage mainWindow = mock(Stage.class);
		BackgroundActionCoordinator coordinator = mock(BackgroundActionCoordinator.class);
		setField(gui, "tenantLoginDialog", loginDialog);
		setField(gui, "primaryStage", mainWindow);
		when(loginDialog.closeTenantDatabase()).thenReturn(true);
		when(loginDialog.loginTenant()).thenReturn(false);
		// Even an available previous session must never bypass the cancelled login.
		when(loginDialog.reopenActiveTenant()).thenReturn(true);
		when(coordinator.quiesce(QuiesceMode.WAIT, Duration.ZERO))
				.thenReturn(CompletableFuture.completedFuture(new QuiesceResult(true, List.of())));

		try (var platform = mockStatic(Platform.class);
				var coordinators = mockStatic(BackgroundActionCoordinator.class);
				var layout = mockStatic(GuiLayoutState.class)) {
			platform.when(() -> Platform.isFxApplicationThread()).thenReturn(true);
			coordinators.when(() -> BackgroundActionCoordinator.getInstance()).thenReturn(coordinator);

			gui.switchTenant();

			verify(loginDialog).loginTenant();
			verify(loginDialog, never()).reopenActiveTenant();
			verify(loginDialog).releaseTenantLock();
			verify(mainWindow).hide();
			verify(mainWindow, never()).show();
			verify(coordinator).stopAcceptingActions();
			platform.verify(() -> Platform.exit());
		}
	}

	private void setField(GBankingGui gui, String name, Object value) throws Exception {
		Field field = GBankingGui.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(gui, value);
	}
}
