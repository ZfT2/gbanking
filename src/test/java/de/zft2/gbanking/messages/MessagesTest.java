package de.zft2.gbanking.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class MessagesTest {

	@Test
	void shouldUseEnglishBundleWhenEnglishLocaleIsSelected() {
		Messages.setLocale(Locale.ENGLISH);

		assertEquals("Select tenant", Messages.getInstance().getMessage("UI_DIALOG_TENANT_SELECTION_TITLE"));
	}

	@Test
	void shouldUseGermanBundleWhenGermanLocaleIsSelected() {
		Messages.setLocale(Locale.GERMAN);

		assertEquals("Mandant auswählen", Messages.getInstance().getMessage("UI_DIALOG_TENANT_SELECTION_TITLE"));
	}
}
