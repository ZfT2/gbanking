package de.zft2.gbanking.gui.enu;

import de.zft2.gbanking.enu.LocalizedEnumValue;
import de.zft2.gbanking.messages.Messages;

public enum ButtonContext implements LocalizedEnumValue {
	
	BUTTON_NEW,
	BUTTON_EDIT,
	BUTTON_DELETE;

	public static ButtonContext forString(String strValue) {
		return LocalizedEnumValue.forString(ButtonContext.class, strValue);
	}

	@Override
	public final String toString() {
		return getLabel();
	}

	public String getLabel() {
		return getDisplayName();
	}

	public String getHeadline() {
		return getText("_HEADLINE");
	}

	public String getDescription() {
		return getText("_DESCRIPTION");
	}

	private String getText(String keySuffix) {
		return Messages.getInstance().getMessage(getMessageKey() + keySuffix);
	}

}
