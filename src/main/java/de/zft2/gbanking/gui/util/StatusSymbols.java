package de.zft2.gbanking.gui.util;

import javafx.scene.control.Label;

/**
 * Central definition of the status symbols used across the GUI.
 *
 * <p>The symbols are Unicode characters (no image resources), therefore they are
 * defined here once and reused by every panel that shows a positive or negative
 * state, for example the active flag of a bank access.</p>
 */
public final class StatusSymbols {

	/** Check mark (✔) used to indicate a positive state. */
	public static final String CHECK = "✓";

	/** Ballot X (✗) used to indicate a negative state. */
	public static final String CROSS = "✗";

	/** Style class shared by all status symbol labels. */
	public static final String STYLE_CLASS = "status-symbol";

	/** Style class for the positive (check mark) variant. */
	public static final String STYLE_CLASS_VALID = "status-symbol-valid";

	/** Style class for the negative (cross) variant. */
	public static final String STYLE_CLASS_INVALID = "status-symbol-invalid";

	/** Style class for the validation status symbols. */
	public static final String VALIDATION_STYLE_CLASS = "iban-validation-status";

	/** Style class for the positive (check mark) validation variant. */
	public static final String VALIDATION_STYLE_CLASS_VALID = "iban-valid";

	/** Style class for the negative (cross) validation variant. */
	public static final String VALIDATION_STYLE_CLASS_INVALID = "iban-invalid";

	private StatusSymbols() {
	}

	/**
	 * Returns the symbol text for the given state.
	 *
	 * @param valid {@code true} for the check mark, {@code false} for the cross
	 * @return the symbol as text
	 */
	public static String text(boolean valid) {
		return valid ? CHECK : CROSS;
	}

	/**
	 * Creates a label showing the check mark or the cross for the given state.
	 *
	 * @param valid {@code true} for the check mark, {@code false} for the cross
	 * @return the styled label
	 */
	public static Label createLabel(boolean valid) {
		Label label = new Label();
		applyTo(label, valid);
		return label;
	}

	/**
	 * Shows the check mark or the cross for the given state in the given label.
	 *
	 * @param label the label to update
	 * @param valid {@code true} for the check mark, {@code false} for the cross
	 */
	public static void applyTo(Label label, boolean valid) {
		label.setText(text(valid));
		label.getStyleClass().setAll(STYLE_CLASS, valid ? STYLE_CLASS_VALID : STYLE_CLASS_INVALID);
	}
}
