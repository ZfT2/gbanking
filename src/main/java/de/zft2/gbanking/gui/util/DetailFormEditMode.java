package de.zft2.gbanking.gui.util;

import java.util.Collection;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Control;

public final class DetailFormEditMode {

	private final List<Control> editableControls;
	private final List<Control> alwaysReadOnlyControls;
	private final List<Button> visibleInViewMode;
	private final List<Button> visibleWithSelection;
	private final List<Button> visibleInEditMode;

	public DetailFormEditMode(Collection<? extends Control> editableControls, Collection<? extends Control> alwaysReadOnlyControls,
			Collection<? extends Button> visibleInViewMode, Collection<? extends Button> visibleWithSelection,
			Collection<? extends Button> visibleInEditMode) {
		this.editableControls = List.copyOf(editableControls);
		this.alwaysReadOnlyControls = List.copyOf(alwaysReadOnlyControls);
		this.visibleInViewMode = List.copyOf(visibleInViewMode);
		this.visibleWithSelection = List.copyOf(visibleWithSelection);
		this.visibleInEditMode = List.copyOf(visibleInEditMode);
	}

	public void apply(boolean editMode, boolean hasSelection) {
		FormStyleUtils.setEditable(editMode, editableControls.toArray(Control[]::new));
		FormStyleUtils.setEditable(false, alwaysReadOnlyControls.toArray(Control[]::new));

		setButtonsVisible(visibleInViewMode, !editMode);
		setButtonsVisible(visibleWithSelection, !editMode && hasSelection);
		setButtonsVisible(visibleInEditMode, editMode);
	}

	private static void setButtonsVisible(Collection<Button> buttons, boolean visible) {
		for (Button button : buttons) {
			button.setVisible(visible);
			button.setManaged(visible);
		}
	}
}
