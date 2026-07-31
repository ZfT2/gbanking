package de.zft2.gbanking.enu;

import java.util.Locale;

import de.zft2.gbanking.messages.Messages;

public interface LocalizedEnumValue {

	String name();

	default String getMessageKey() {
		return "ENUM_" + getClass().getSimpleName().toUpperCase(Locale.ROOT) + "_" + name();
	}

	default String getDisplayName() {
		return getDisplayName(Messages.getLocale());
	}

	default String getGermanName() {
		return getDisplayName(Locale.GERMAN);
	}

	default String getEnglishName() {
		return getDisplayName(Locale.ENGLISH);
	}

	default boolean matches(String value) {
		return value != null && (name().equalsIgnoreCase(value) || getGermanName().equalsIgnoreCase(value) || getEnglishName().equalsIgnoreCase(value));
	}

	private String getDisplayName(Locale locale) {
		return Messages.getInstance().getMessage(getMessageKey(), locale);
	}

	static <E extends Enum<E> & LocalizedEnumValue> E forString(Class<E> enumType, String value) {
		for (E enumValue : enumType.getEnumConstants()) {
			if (enumValue.matches(value)) {
				return enumValue;
			}
		}
		return null;
	}
}
