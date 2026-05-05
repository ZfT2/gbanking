package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class HbciStatusMessageExtractorAdditionalTest {

	@Test
	void extractMessageLines_shouldFlattenNestedPayloadsAndNormalizeHbciEscaping() {
		Object[] payload = new Object[] { new Object[] { "HIRMG:1:2+0010::Dialog akzeptiert'", null },
				"HIRMS:2:2+0020::?:?Auftrag gebucht'" };

		List<String> lines = HbciStatusMessageExtractor.extractMessageLines(payload);

		assertEquals(List.of("0010: Dialog akzeptiert", "0020: Auftrag gebucht"), lines);
	}

	@Test
	void extractMessages_shouldReturnEmptyStringForBlankOrPlainPayload() {
		assertEquals("", HbciStatusMessageExtractor.extractMessages((String) null));
		assertEquals("", HbciStatusMessageExtractor.extractMessages("nur ein Freitext ohne HBCI-Code"));
		assertEquals("", HbciStatusMessageExtractor.extractMessages(new Object[0]));
	}

	@Test
	void sanitizeForDetails_shouldFlattenNestedPayloadsAndKeepRawTexts() {
		Object[] payload = new Object[] { "outer", new Object[] { "inner 1", null, "inner 2" } };

		String details = HbciStatusMessageExtractor.sanitizeForDetails(payload);

		assertTrue(details.contains("outer"));
		assertTrue(details.contains("inner 1"));
		assertTrue(details.contains("inner 2"));
		assertEquals("outer" + System.lineSeparator() + System.lineSeparator() + "inner 1" + System.lineSeparator() + System.lineSeparator() + "inner 2",
				details);
	}
}
