package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.gui.progress.InstituteFileImportProgressBarPanel;
import de.zft2.gbanking.service.institute.InstituteImportService;
import de.zft2.gbanking.service.institute.InstituteImportService.ImportDefinition;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

class GBankingServiceStartupTest {

	@Test
	void shouldShowInstituteImportsSequentiallyWithSameOwner() {
		Window owner = mock(Window.class);
		List<ImportDefinition> importDefinitions = new InstituteImportService().getDefaultImports();
		List<Stage> progressWindows = new ArrayList<>();
		List<Object> constructorOwners = new ArrayList<>();

		try (MockedConstruction<InstituteFileImportProgressBarPanel> panels = mockConstruction(
				InstituteFileImportProgressBarPanel.class, (panel, context) -> {
					Stage progressWindow = createProgressWindow(panel);
					progressWindows.add(progressWindow);
					constructorOwners.add(context.arguments().get(1));
				})) {
			GBankingService.showInstituteImportsSequentially(owner, importDefinitions);

			assertEquals(importDefinitions.size(), panels.constructed().size());
			for (int index = 0; index < importDefinitions.size(); index++) {
				InstituteFileImportProgressBarPanel panel = panels.constructed().get(index);
				ImportDefinition importDefinition = importDefinitions.get(index);
				assertSame(owner, constructorOwners.get(index));
				verify(progressWindows.get(index)).showAndWait();
				verify(panel).startTask(importDefinition.fileName(), null, null);
			}
		}
	}

	private static Stage createProgressWindow(InstituteFileImportProgressBarPanel panel) {
		Stage progressWindow = mock(Stage.class);
		AtomicReference<EventHandler<WindowEvent>> shownHandler = new AtomicReference<>();
		doAnswer(invocation -> {
			shownHandler.set(invocation.getArgument(0));
			return null;
		}).when(progressWindow).setOnShown(any());
		doAnswer(invocation -> {
			assertNotNull(shownHandler.get());
			shownHandler.get().handle(mock(WindowEvent.class));
			return null;
		}).when(progressWindow).showAndWait();
		when(panel.createNewFileImportProgressBarWindow()).thenReturn(progressWindow);
		return progressWindow;
	}
}
