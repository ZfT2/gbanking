package de.zft2.gbanking.gui.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.messages.Messages;

class GuiEnumTest {
	private Locale previousLocale;

	@BeforeEach
	void useGermanLocale() {
		previousLocale = Messages.getLocale();
		Messages.setLocale(Locale.GERMAN);
	}

	@AfterEach
	void restoreLocale() {
		Messages.setLocale(previousLocale);
	}

	@Test
	void buttonContext_shouldResolveByLabelAndExposeDialogTexts() {
		ButtonContext context = ButtonContext.BUTTON_EDIT;

		assertSame(context, ButtonContext.forString(context.getLabel()));
		assertEquals(context.getLabel(), context.toString());
		assertEquals("Bankzugang bearbeiten", context.getHeadline());
		assertEquals("Bestehenden Bankzugang bearbeiten", context.getDescription());
		assertNull(ButtonContext.forString("unknown"));
	}

	@Test
	void fileType_shouldResolveViaGBankingEnumDefaultMethod() {
		assertSame(FileType.XML, FileType.CSV.forString(FileType.XML.getDescription()));
		assertEquals(".xml", FileType.XML.getSuffix());
		assertEquals(FileType.XML.getDescription(), FileType.XML.toString());
		assertNull(FileType.CSV.forString("unknown"));
	}

	@Test
	void pageContext_shouldResolveByDescription() {
		assertSame(PageContext.ALL_ACCOUNTS, PageContext.forString(PageContext.ALL_ACCOUNTS.toString()));
		assertNull(PageContext.forString("unknown"));
	}

	@Test
	void guiEnums_shouldUseEnglishTexts() {
		Messages.setLocale(Locale.ENGLISH);

		assertEquals("Edit bank access", ButtonContext.BUTTON_EDIT.getHeadline());
		assertEquals("XML files", FileType.XML.getDescription());
		assertEquals("All accounts", PageContext.ALL_ACCOUNTS.toString());
	}
}
