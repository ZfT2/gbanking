package de.zft2.gbanking.gui.enu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class GuiEnumTest {

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
}
