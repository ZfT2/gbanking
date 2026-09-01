package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HbciLogMessageSanitizerTest {

	private static final String IBAN = "DE44500105175407324931";

	@Test
	void shouldSummarizeRawFinTsMessagesWithoutPayload() {
		String xml = "<?xml version=\"1.0\"?><Document><Nm>Max Mustermann</Nm><IBAN>" + IBAN + "</IBAN><Amt>123.45</Amt></Document>";
		String message = "received message after decryption: HNHBK:1:3+1000+300'HIRMS:2:2:3+3040::Weitere Daten'HICAZ:3:1:3+@"
				+ xml.length() + "@" + xml + "'HNHBS:4:1+1'";

		String sanitized = HbciLogMessageSanitizer.sanitize(message, true);

		assertTrue(sanitized.startsWith("received message after decryption: [vertrauliche FinTS-Nachricht maskiert;"));
		assertTrue(sanitized.contains("Segmente=HNHBK,HIRMS,HICAZ,HNHBS"));
		assertTrue(sanitized.contains("Rückmeldungscodes=3040"));
		assertFalse(sanitized.contains(IBAN));
		assertFalse(sanitized.contains("Max Mustermann"));
		assertFalse(sanitized.contains("123.45"));
	}

	@Test
	void shouldSummarizeStandaloneXmlPayloads() {
		String message = "generated XML:\n<?xml version=\"1.0\"?><Document><IBAN>" + IBAN + "</IBAN></Document>";

		String sanitized = HbciLogMessageSanitizer.sanitize(message, true);

		assertTrue(sanitized.startsWith("generated XML:\n[vertrauliche XML-Nutzdaten maskiert; Zeichen="));
		assertFalse(sanitized.contains(IBAN));
	}

	@Test
	void shouldMaskIdentifiersInOrdinaryMessages() {
		String message = "adding new vop-auth message to queue [vop-id: abcdef123456] for " + IBAN;

		String sanitized = HbciLogMessageSanitizer.sanitize(message, true);

		assertTrue(sanitized.contains("vop-id: **********56"));
		assertTrue(sanitized.contains("******************4931"));
		assertFalse(sanitized.contains("abcdef123456"));
		assertFalse(sanitized.contains(IBAN));
	}

	@Test
	void shouldMaskIndividualParameterValues() {
		String purpose = "Vertraulicher Verwendungszweck = Kundenreferenz";

		String sanitized = HbciLogMessageSanitizer.sanitize("setting SEPA param usage = " + purpose, true);

		assertTrue(sanitized.startsWith("setting SEPA param usage = [vertraulicher Wert maskiert; Zeichen="));
		assertFalse(sanitized.contains(purpose));
	}

	@Test
	void shouldPreserveTechnicalJobNames() {
		String message = "adding task KUmsNewCamt1";

		assertEquals(message, HbciLogMessageSanitizer.sanitize(message, true));
	}

	@Test
	void shouldLeaveMessageUntouchedWhenMaskingIsDisabled() {
		String message = "generated XML:\n<?xml version=\"1.0\"?><Document><IBAN>" + IBAN + "</IBAN></Document>";

		assertEquals(message, HbciLogMessageSanitizer.sanitize(message, false));
	}
}
