package de.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HbciStatusMessageExtractorTest {

	@Test
	void extractMessages_shouldReturnReadableMessagesFromRawHbciResponse() {
		Object[] statusPayload = new Object[] {
				"HNHBK:1:3+000000000206+300+4D6032511025638+1+4D6032511025638:1'HIRMG:2:2+9050::Die Nachricht enthält Fehler.+9800::Dialog abgebrochen+9942::*Anmeldedaten sind ungültig.+9340::Auftrag abgelehnt.'HNHBS:3:1+1'" };

		String result = HbciStatusMessageExtractor.extractMessages(statusPayload);

		assertEquals(
				"9050: Die Nachricht enthält Fehler." + System.lineSeparator() + "9800: Dialog abgebrochen" + System.lineSeparator()
						+ "9942: *Anmeldedaten sind ungültig." + System.lineSeparator() + "9340: Auftrag abgelehnt.",
				result);
	}

	@Test
	void sanitizeForDetails_shouldReturnJoinedRawPayload() {
		Object[] statusPayload = new Object[] { "[SynchRes]", "HIRMG:2:2+9800::Dialog abgebrochen'" };

		String result = HbciStatusMessageExtractor.sanitizeForDetails(statusPayload);

		assertTrue(result.contains("[SynchRes]"));
		assertTrue(result.contains("9800::Dialog abgebrochen"));
	}
}
