package de.zft2.gbanking.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.gui.KeyboardShortcutDispatcher.Action;
import de.zft2.gbanking.gui.panel.overview.OverviewBasePanel;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import javafx.scene.layout.Pane;

class KeyboardShortcutsTest {

	private static final List<String> ACTION_MENU_KEYS = List.of("UI_MENU_VIEW_REFRESH");
	private static final List<String> TOP_LEVEL_MENU_KEYS = List.of("UI_MENU_FILE", "UI_MENU_EDIT", "UI_MENU_VIEW", "UI_MENU_EXECUTE",
			"UI_MENU_ANALYSIS", "UI_MENU_SETTINGS", "UI_MENU_ABOUT");

	@Test
	void centralShortcutDefinitions_shouldUseExpectedKeysAndModifiers() {
		assertShortcut(KeyboardShortcuts.FIND, KeyCode.F);
		assertShortcut(KeyboardShortcuts.SAVE, KeyCode.S);
		assertShortcut(KeyboardShortcuts.EXIT, KeyCode.Q);
		assertShortcut(KeyboardShortcuts.SETTINGS, KeyCode.COMMA);
		assertKey(KeyboardShortcuts.CANCEL, KeyCode.ESCAPE);
		assertKey(KeyboardShortcuts.REFRESH, KeyCode.F5);
		assertKey(KeyboardShortcuts.HELP, KeyCode.F1);
	}

	@Test
	void shortcutMenuLabels_shouldExistAndTopLevelMenusShouldHaveMnemonics() {
		for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
			ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
			for (String key : ACTION_MENU_KEYS) {
				assertFalse(bundle.getString(key).isBlank(), () -> "Missing menu label for " + key + " in " + locale);
			}
			for (String key : TOP_LEVEL_MENU_KEYS) {
				assertTrue(bundle.getString(key).contains("_"), () -> "Missing menu mnemonic for " + key + " in " + locale);
			}
		}
	}

	@Test
	void dispatcher_shouldUseLastVisibleRegisteredTarget() {
		TestOverview panel = new TestOverview();
		Pane first = new Pane();
		Pane second = new Pane();
		AtomicInteger handledBy = new AtomicInteger();
		KeyboardShortcutDispatcher.register(first, Action.SAVE, () -> handledBy.compareAndSet(0, 1));
		KeyboardShortcutDispatcher.register(second, Action.SAVE, () -> handledBy.compareAndSet(0, 2));
		panel.getChildren().setAll(first, second);

		assertTrue(KeyboardShortcutDispatcher.dispatch(panel, Action.SAVE));
		assertEquals(2, handledBy.get());

		second.setVisible(false);
		handledBy.set(0);
		assertTrue(KeyboardShortcutDispatcher.dispatch(panel, Action.SAVE));
		assertEquals(1, handledBy.get());
	}

	@Test
	void dispatcher_shouldPreventRefreshWhileVisibleTargetBlocksIt() {
		TestOverview panel = new TestOverview();
		Pane editor = new Pane();
		AtomicBoolean editing = new AtomicBoolean(true);
		KeyboardShortcutDispatcher.blockRefreshWhile(editor, editing::get);
		panel.getChildren().add(editor);

		assertFalse(KeyboardShortcutDispatcher.dispatch(panel, Action.REFRESH));
		assertEquals(0, panel.refreshCount);
		editing.set(false);
		assertTrue(KeyboardShortcutDispatcher.dispatch(panel, Action.REFRESH));
		assertEquals(1, panel.refreshCount);
	}

	private void assertShortcut(javafx.scene.input.KeyCombination combination, KeyCode keyCode) {
		KeyCodeCombination keyCombination = (KeyCodeCombination) combination;
		assertEquals(keyCode, keyCombination.getCode());
		assertEquals(ModifierValue.DOWN, keyCombination.getShortcut());
	}

	private void assertKey(javafx.scene.input.KeyCombination combination, KeyCode keyCode) {
		KeyCodeCombination keyCombination = (KeyCodeCombination) combination;
		assertEquals(keyCode, keyCombination.getCode());
		assertEquals(ModifierValue.UP, keyCombination.getShortcut());
	}

	private static class TestOverview extends OverviewBasePanel {
		private int refreshCount;

		@Override
		public void createOverallPanel(boolean show) {
			// This test double needs no UI; only inherited shortcut handling is exercised.
		}

		@Override
		public void refreshOnShow() {
			refreshCount++;
		}
	}
}
