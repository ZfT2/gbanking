package de.zft2.gbanking.gui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

final class KeyboardShortcuts {

	static final KeyCombination FIND = shortcut(KeyCode.F);
	static final KeyCombination SAVE = shortcut(KeyCode.S);
	static final KeyCombination CANCEL = new KeyCodeCombination(KeyCode.ESCAPE);
	static final KeyCombination REFRESH = new KeyCodeCombination(KeyCode.F5);
	static final KeyCombination EXIT = shortcut(KeyCode.Q);
	static final KeyCombination SETTINGS = shortcut(KeyCode.COMMA);
	static final KeyCombination HELP = new KeyCodeCombination(KeyCode.F1);

	private KeyboardShortcuts() {
	}

	private static KeyCombination shortcut(KeyCode keyCode) {
		return new KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN);
	}
}
