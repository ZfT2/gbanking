package de.zft2.gbanking.gui;

import java.util.function.BooleanSupplier;

import de.zft2.gbanking.gui.panel.overview.OverviewBasePanel;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;

public final class KeyboardShortcutDispatcher {

	public enum Action {
		FIND,
		SAVE,
		CANCEL,
		REFRESH
	}

	private static final Object REFRESH_BLOCKER = new Object();

	private KeyboardShortcutDispatcher() {
	}

	public static void registerFilter(Node node, TextInputControl filter, Node table) {
		register(node, Action.FIND, () -> {
			filter.requestFocus();
			filter.selectAll();
			return true;
		});
		register(node, Action.CANCEL, () -> {
			if (!filter.isFocused()) {
				return false;
			}
			filter.clear();
			table.requestFocus();
			return true;
		});
	}

	public static void registerForm(Node node, ButtonBase saveButton, ButtonBase cancelButton) {
		register(node, Action.SAVE, () -> fire(saveButton));
		register(node, Action.CANCEL, () -> fire(cancelButton));
	}

	public static void registerForm(Node node, ButtonBase saveButton, Runnable cancelAction) {
		register(node, Action.SAVE, () -> fire(saveButton));
		register(node, Action.CANCEL, () -> {
			cancelAction.run();
			return true;
		});
	}

	public static void register(Node node, Action action, BooleanSupplier handler) {
		node.getProperties().put(action, handler);
	}

	public static void blockRefreshWhile(Node node, BooleanSupplier blocker) {
		node.getProperties().put(REFRESH_BLOCKER, blocker);
	}

	public static boolean dispatch(OverviewBasePanel panel, Action action) {
		if (action == Action.REFRESH) {
			if (invokeVisible(panel, REFRESH_BLOCKER)) {
				return false;
			}
			panel.refreshOnShow();
			return true;
		}

		Node focusOwner = panel.getScene() != null ? panel.getScene().getFocusOwner() : null;
		if (belongsTo(focusOwner, panel)) {
			for (Node node = focusOwner; node != null; node = node.getParent()) {
				if (invoke(node, action)) {
					return true;
				}
				if (node == panel) {
					break;
				}
			}
		}
		return invokeVisible(panel, action);
	}

	private static boolean invokeVisible(Node node, Object key) {
		if (!node.isVisible() || node.isDisabled()) {
			return false;
		}
		if (invoke(node, key)) {
			return true;
		}
		if (node instanceof Parent parent) {
			for (int i = parent.getChildrenUnmodifiable().size() - 1; i >= 0; i--) {
				if (invokeVisible(parent.getChildrenUnmodifiable().get(i), key)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean invoke(Node node, Object key) {
		Object handler = node.hasProperties() ? node.getProperties().get(key) : null;
		return handler instanceof BooleanSupplier action && action.getAsBoolean();
	}

	private static boolean fire(ButtonBase button) {
		if (!button.isVisible() || button.isDisabled()) {
			return false;
		}
		button.fire();
		return true;
	}

	private static boolean belongsTo(Node node, Parent root) {
		for (Node current = node; current != null; current = current.getParent()) {
			if (current == root) {
				return true;
			}
		}
		return false;
	}
}
