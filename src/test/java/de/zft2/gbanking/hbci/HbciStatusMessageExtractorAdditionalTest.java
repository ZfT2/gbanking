package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.status.HBCIExecStatus;

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

	@Test
	void containsWrongPinFeedback_shouldDetectFinTsCodeAndCredentialTexts() {
		String rawStatus = "HIRMG:2:2+9050::Die Nachricht enth\u00e4lt Fehler.+9942::*Anmeldedaten sind ung\u00fcltig.'";

		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback(rawStatus));
		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback("Dialog:\n9942:*Anmeldedaten sind ung\u00fcltig."));
		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback("Die PIN ist falsch."));
		assertFalse(HbciStatusMessageExtractor.containsWrongPinFeedback("HIRMG:2:2+9800::Dialog abgebrochen.'"));
	}

	@Test
	void containsWrongPinFeedback_shouldInspectThrowableCauseChain() {
		Throwable transportFailure = new IllegalStateException("Fehler beim Empfangen der Daten vom HBCI-Server",
				new IOException("Server returned HTTP response code: 400 for URL: https://bank.example/fints"));

		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback(transportFailure));
		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback(new IllegalArgumentException("Die PIN ist falsch.")));
		assertFalse(HbciStatusMessageExtractor.containsWrongPinFeedback(new IOException("Connection reset")));
	}

	@Test
	void containsWrongPinFeedback_shouldInspectHbciExecutionStatus() {
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.getErrorString()).thenReturn("HIRMG:2:2+9942::*Anmeldedaten sind ung\u00fcltig.'");

		assertTrue(HbciStatusMessageExtractor.containsWrongPinFeedback(status));
		assertFalse(HbciStatusMessageExtractor.containsWrongPinFeedback((HBCIExecStatus) null));
	}
}
