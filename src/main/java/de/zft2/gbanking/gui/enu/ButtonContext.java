package de.zft2.gbanking.gui.enu;

public enum ButtonContext {
	
	BUTTON_NEW("Neu...", "Neuer Bankzugang", "Neuen Bankzugang anlegen"),
	BUTTON_EDIT("bearbeiten", "Bankzugang bearbeiten", "Bestehenden Bankzugang bearbeiten"), 
	BUTTON_DELETE("löschen", "Bankzugang löschen", "Bestehenden Bankzugang löschen");

	public static ButtonContext forString(String strValue) {
		for (ButtonContext x : values()) {
			if (x.label.equals(strValue))
				return x;
		}
		return null;
	}

	private final String label;
	private final String headline;
	private final String description;

	private ButtonContext(String label, String headline, String description) {
		this.label = label;
		this.headline = headline;
		this.description = description;
	}

	@Override
	public final String toString() {
		return label;
	}

	public String getLabel() {
		return label;
	}

	public String getHeadline() {
		return headline;
	}

	public String getDescription() {
		return description;
	}

}
