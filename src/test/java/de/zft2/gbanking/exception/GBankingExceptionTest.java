package de.zft2.gbanking.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GBankingExceptionTest {

	@Test
	void messageConstructor_shouldAppendEmptyOriginalMessage() {
		GBankingException exception = new GBankingException("Fehler");

		assertEquals("Fehler: ", exception.getMessage());
		assertTrue(exception.toString().contains("GBankingException"));
	}

	@Test
	void formattedConstructor_shouldUseFormattedMessage() {
		GBankingException exception = new GBankingException("Fehler %s", "A");

		assertEquals("Fehler A: ", exception.getMessage());
	}

	@Test
	void originalExceptionConstructor_shouldExposeOriginalMessage() {
		GBankingException exception = new GBankingException("Fehler", new Exception("Ursache"));

		assertEquals("Fehler: Ursache", exception.getMessage());
	}

	@Test
	void suppressedStacktraceConstructor_shouldDisableWritableStacktrace() {
		GBankingException exception = new GBankingException("Kurz", true);

		assertEquals(0, exception.getStackTrace().length);
		assertEquals("Kurz: null", exception.toString());
	}

	@Test
	void exportException_shouldUseGBankingExceptionMessageHandling() {
		ExportException exception = new ExportException("Export fehlgeschlagen");

		assertEquals("Export fehlgeschlagen: ", exception.getMessage());
	}
}
