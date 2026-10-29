package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV.HBCIJobImpl;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.exceptions.InvalidUserDataException;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.messages.Messages;

class HbciStatusDescriptionProviderTest {

	private static final String ERROR_WITHOUT_DETAILS = "Fehler beim FinTS/HBCI-Vorgang";
	private static final String BALANCE_OF_SELECTED_ACCOUNT = "Rufe Saldo ab für Konto (ausgewählt)...";

	private final HbciStatusDescriptionProvider provider = new HbciStatusDescriptionProvider();
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

	@ParameterizedTest
	@MethodSource("exceptions")
	void describeExceptionShouldUseMostSpecificMessage(Throwable throwable, String expected) {
		assertEquals(expected, provider.describeException(throwable));
	}

	static Stream<Arguments> exceptions() {
		Throwable selfCaused = mock(Throwable.class);
		when(selfCaused.getMessage()).thenReturn(" loop ");
		when(selfCaused.getCause()).thenReturn(selfCaused);
		return Stream.of(
				Arguments.of(new HBCI_Exception("Fehler beim Hinzufügen des Auftrages KontoauszugPdf2 zum aktuellen Dialog",
						new InvalidUserDataException("Property my.bic wurde nicht gesetzt")),
						"Fehler: Property my.bic wurde nicht gesetzt"),
				Arguments.of(new IllegalStateException(" outer ", new IllegalArgumentException(" ")), "Fehler: outer"),
				Arguments.of(selfCaused, "Fehler: loop"),
				Arguments.of(new IllegalStateException(), ERROR_WITHOUT_DETAILS),
				Arguments.of(null, ERROR_WITHOUT_DETAILS));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			                                                                        |
			"  "                                                                    |
			" Verbindung abgebrochen "                                              | Fehler: Verbindung abgebrochen
			HIRMG:2:2+9050::Die Nachricht enthält Fehler.+9800::Dialog abgebrochen' | Fehler: 9800: Dialog abgebrochen
			""")
	void describeFailureShouldUseLastBankMessage(String failureMessage, String expected) {
		assertEquals(expected, provider.describeFailure(failureMessage));
	}

	@ParameterizedTest
	@MethodSource("statusTexts")
	void describeStatusShouldTranslateDialogSteps(int statusTag, String expected) {
		assertEquals(expected, provider.describeStatus(statusTag, new Object[0]));
	}

	static Stream<Arguments> statusTexts() {
		return Stream.of(
				Arguments.of(HBCICallback.STATUS_INST_BPD_INIT, "Hole Bankparameterdaten..."),
				Arguments.of(HBCICallback.STATUS_INST_GET_KEYS, "Hole Bankschlüssel..."),
				Arguments.of(HBCICallback.STATUS_SEND_KEYS, "Übermittle Kundenschlüssel..."),
				Arguments.of(HBCICallback.STATUS_INIT_SYSID, "Synchronisiere Kundensystem..."),
				Arguments.of(HBCICallback.STATUS_INIT_UPD, "Hole Benutzer- und Kontodaten..."),
				Arguments.of(HBCICallback.STATUS_LOCK_KEYS, "Sperre Kundenschlüssel..."),
				Arguments.of(HBCICallback.STATUS_INIT_SIGID, "Synchronisiere Signatur-ID..."),
				Arguments.of(HBCICallback.STATUS_DIALOG_INIT, "Eröffne Bankdialog..."),
				Arguments.of(HBCICallback.STATUS_DIALOG_END, "Beende Bankdialog..."),
				Arguments.of(HBCICallback.STATUS_SEND_TASK_DONE, null));
	}

	@Test
	void describeStatusShouldFallBackToGenericJobWithoutJobPayload() {
		for (Object[] payload : new Object[][] { null, {}, { "Auftrag", 1 } }) {
			assertEquals("Führe Bankauftrag aus...", provider.describeStatus(HBCICallback.STATUS_SEND_TASK, payload));
		}
	}

	@Test
	void describeStatusShouldUseRegisteredJobDescription() {
		HBCIJob<?> job = mock(HBCIJob.class);
		provider.registerJobDescription(job, "  Hole Kontoauszug 2 von 5 für Konto Girokonto...  ");

		assertEquals("Hole Kontoauszug 2 von 5 für Konto Girokonto...",
				provider.describeStatus(HBCICallback.STATUS_SEND_TASK, new Object[] { "Auftrag", job }));
	}

	@Test
	void registerJobDescriptionShouldIgnoreMissingValues() {
		HBCIJob<?> job = job("SaldoReq", null);
		provider.registerJobDescription(null, "Unbenutzt");
		provider.registerJobDescription(job, null);
		provider.registerJobDescription(job, " ");

		assertEquals(BALANCE_OF_SELECTED_ACCOUNT, describe(job));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			SaldoReq7             | Rufe Saldo ab für Konto Girokonto...
			KUmsAllCamt1          | Rufe Umsätze ab für Konto Girokonto...
			KUmsAll               | Rufe Umsätze ab für Konto Girokonto...
			KUmsZeitCamt          | Rufe Umsätze ab für Konto Girokonto...
			KUmsZeit              | Rufe Umsätze ab für Konto Girokonto...
			Vormerkposten         | Rufe vorgemerkte Umsätze ab für Konto Girokonto...
			Kontoauszug           | Hole Kontoauszug für Konto Girokonto...
			KontoauszugPdf        | Hole Kontoauszug für Konto Girokonto...
			KontoauszugUebersicht | Hole Übersicht der vorliegenden Kontoauszüge...
			Receipt               | Bestätige den Empfang von Kontoauszügen...
			InfoList              | Hole Übersicht der Banknachrichten...
			InfoDetails           | Hole Banknachrichten 1 von 1...
			DauerSEPAList         | Rufe Daueraufträge ab für Konto Girokonto...
			TermUebSEPAList       | Rufe Terminüberweisungen ab für Konto Girokonto...
			UebSEPA               | Sende SEPA-Überweisung für Konto Girokonto...
			InstUebSEPA           | Sende Echtzeit-Überweisung für Konto Girokonto...
			UebEil                | Sende Eil-Überweisung für Konto Girokonto...
			TermUebSEPA           | Sende Terminüberweisung für Konto Girokonto...
			DauerSEPANew          | Sende Dauerauftrag für Konto Girokonto...
			TermUebSEPAEdit       | Ändere Terminüberweisung für Konto Girokonto...
			TermUebSEPADel        | Lösche Terminüberweisung für Konto Girokonto...
			DauerSEPAEdit         | Ändere Dauerauftrag für Konto Girokonto...
			DauerSEPADel          | Lösche Dauerauftrag für Konto Girokonto...
			UebForeign            | Sende Auslandsüberweisung für Konto Girokonto...
			TAN2Step6             | Autorisiere Bankauftrag...
			VoP1                  | Prüfe Zahlungsempfänger...
			Unknown3              | Führe Bankauftrag 'Unknown3' aus...
			123                   | Führe Bankauftrag '123' aus...
			                      | Führe Bankauftrag 'null' aus...
			""")
	void describeStatusShouldTranslateJobNames(String jobName, String expected) {
		assertEquals(expected, describe(job(jobName, konto("Girokonto", null, null))));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			Girokonto | DE02120300000000202051 | 1234 | Girokonto
			" "       | DE02120300000000202051 | 1234 | DE02120300000000202051
			          |                        | 1234 | 1234
			          |                        |      | (ausgewählt)
			""")
	void shouldDescribeAccountsByFirstAvailableValue(String name, String iban, String number, String expected) {
		BankAccount bankAccount = new BankAccount();
		bankAccount.setAccountName(name);
		bankAccount.setIban(iban);
		bankAccount.setNumber(number);

		assertEquals(expected, provider.getAccountDescription(bankAccount));
		assertEquals("Rufe Saldo ab für Konto " + expected + "...", describe(job("SaldoReq", konto(name, iban, number))));
	}

	@Test
	void shouldUseSelectedAccountPlaceholderWithoutAccount() {
		HBCIJob<?> plainJob = mock(HBCIJob.class);
		when(plainJob.getName()).thenReturn("SaldoReq2");

		assertEquals("(ausgewählt)", provider.getAccountDescription(null));
		assertEquals(BALANCE_OF_SELECTED_ACCOUNT, describe(plainJob));
		assertEquals(BALANCE_OF_SELECTED_ACCOUNT, describe(job("SaldoReq", null)));
	}

	private String describe(HBCIJob<?> job) {
		return provider.describeStatus(HBCICallback.STATUS_SEND_TASK, new Object[] { job });
	}

	private static HBCIJobImpl<?> job(String name, Konto orderAccount) {
		HBCIJobImpl<?> job = mock(HBCIJobImpl.class);
		when(job.getName()).thenReturn(name);
		when(job.getOrderAccount()).thenReturn(orderAccount);
		return job;
	}

	private static Konto konto(String name, String iban, String number) {
		Konto konto = new Konto();
		konto.name = name;
		konto.iban = iban;
		konto.number = number;
		return konto;
	}
}