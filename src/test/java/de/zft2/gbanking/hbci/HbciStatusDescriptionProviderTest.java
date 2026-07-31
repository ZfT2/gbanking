package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.exceptions.InvalidUserDataException;

import de.zft2.gbanking.messages.Messages;

class HbciStatusDescriptionProviderTest {

	private Locale previousLocale;

	@BeforeEach
	void useGermanMessages() {
		previousLocale = Messages.getLocale();
		Messages.setLocale(Locale.GERMAN);
	}

	@AfterEach
	void restoreLocale() {
		Messages.setLocale(previousLocale);
	}

	@Test
	void describeExceptionShouldUseMostSpecificCause() {
		HbciStatusDescriptionProvider provider = new HbciStatusDescriptionProvider();
		Exception exception = new HBCI_Exception("Fehler beim Hinzufügen des Auftrages KontoauszugPdf2 zum aktuellen Dialog",
				new InvalidUserDataException("Property my.bic wurde nicht gesetzt"));

		String description = provider.describeException(exception);

		assertEquals("Fehler: Property my.bic wurde nicht gesetzt", description);
	}

	@Test
	void describeStatusShouldUseRegisteredJobDescription() {
		HbciStatusDescriptionProvider provider = new HbciStatusDescriptionProvider();
		HBCIJob<?> job = mock(HBCIJob.class);
		provider.registerJobDescription(job, "Hole Kontoauszug 2 von 5 für Konto Girokonto...");

		String description = provider.describeStatus(HBCICallback.STATUS_SEND_TASK, new Object[] { job });

		assertEquals("Hole Kontoauszug 2 von 5 für Konto Girokonto...", description);
	}

	@Test
	void describeStatusShouldTranslateKnownUnregisteredJob() {
		HbciStatusDescriptionProvider provider = new HbciStatusDescriptionProvider();
		HBCIJob<?> job = mock(HBCIJob.class);
		when(job.getName()).thenReturn("SaldoReq2");

		String description = provider.describeStatus(HBCICallback.STATUS_SEND_TASK, new Object[] { job });

		assertEquals("Rufe Saldo ab für Konto (ausgewählt)...", description);
	}

	@Test
	void describeStatusShouldTranslateDialogInitialization() {
		HbciStatusDescriptionProvider provider = new HbciStatusDescriptionProvider();

		String description = provider.describeStatus(HBCICallback.STATUS_DIALOG_INIT, new Object[0]);

		assertEquals("Eröffne Bankdialog...", description);
	}
}
