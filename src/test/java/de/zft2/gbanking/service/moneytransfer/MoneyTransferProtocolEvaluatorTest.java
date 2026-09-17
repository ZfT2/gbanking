package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRVoP.VoPStatus;

import de.zft2.gbanking.db.dao.enu.MoneyTransferProtocolResultStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.VopResult;

class MoneyTransferProtocolEvaluatorTest {

	@Test
	void evaluateShouldCondenseSuccessfulTransferAndVopResult() {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(true,
				"HBCI job status: 0020:SEPA-Einzelüberweisung erfolgreich", true, VoPStatus.CLOSE_MATCH, true);

		assertEquals(MoneyTransferProtocolResultStatus.SUCCESS, result.resultStatus());
		assertTrue(result.pinOk());
		assertFalse(result.scaRequired());
		assertTrue(result.vopRequired());
		assertEquals(VopResult.CLOSE_MATCH, result.vopResult());
		assertTrue(result.recipientNameCorrected());
		assertNull(result.bankResponse());
	}

	@Test
	void evaluateShouldRecognizeTypicalBusinessErrors() {
		assertStatus("9255: Echtzeitüberweisungslimit überschritten", MoneyTransferProtocolResultStatus.ERROR_LIMIT_INSUFFICIENT);
		assertStatus("9230: Auftrag wegen mangelnder Deckung abgelehnt", MoneyTransferProtocolResultStatus.ERROR_INSUFFICIENT_FUNDS);
		assertStatus("9942: Anmeldedaten sind ungültig", MoneyTransferProtocolResultStatus.ERROR_INVALID_PIN);
		assertStatus("9390: Doppeleinreichung", MoneyTransferProtocolResultStatus.ERROR_DUPLICATE_ORDER);
	}

	@Test
	void evaluateShouldKeepOnlyUnrecognizedBankResponse() {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(false,
				"9050: Die Nachricht enthält Fehler." + System.lineSeparator() + "9999: Individueller Hinweis der Bank", false, null, false);

		assertEquals(MoneyTransferProtocolResultStatus.ERROR_TECHNICAL, result.resultStatus());
		assertFalse(result.pinOk());
		assertEquals("9999: Individueller Hinweis der Bank", result.bankResponse());
	}

	@Test
	void evaluateShouldMapScaAndRemoveGeneralInformation() {
		String response = String.join(System.lineSeparator(), "3076: Starke Kundenauthentifizierung nicht notwendig",
				"3060: Bitte beachten Sie die Hinweise", "3920: Zugelassene TAN-Verfahren: 920, 921",
				"3905: Es wurde keine Challenge erzeugt", "9999: Individueller Hinweis der Bank");

		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(true, response, false, null, false);

		assertFalse(result.scaRequired());
		assertEquals("9999: Individueller Hinweis der Bank", result.bankResponse());
	}

	@Test
	void evaluateShouldRecognizeEquivalentScaMessageWithoutReturnCode() {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(true,
				"Strong customer authentication not required", false, null, false);

		assertFalse(result.scaRequired());
		assertNull(result.bankResponse());
	}

	@Test
	void evaluateShouldMapRequiredScaToOppositeValue() {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(true,
				"3956: Starke Kundenauthentifizierung noch ausstehend", false, null, false);

		assertTrue(result.scaRequired());
		assertNull(result.bankResponse());
	}

	@Test
	void evaluateShouldUseLastScaFeedback() {
		String response = "3076: Keine starke Authentifizierung erforderlich" + System.lineSeparator()
				+ "9075: Dialog abgebrochen - starke Authentifizierung erforderlich";

		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(false, response, false, null, false);

		assertTrue(result.scaRequired());
	}

	@Test
	void evaluateLegacyShouldMapOldProtocolText() {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluateLegacy(MoneyTransferStatus.SENT,
				"0020: SEPA-Einzelüberweisung erfolgreich");

		assertEquals(MoneyTransferProtocolResultStatus.SUCCESS, result.resultStatus());
		assertTrue(result.pinOk());
		assertFalse(result.scaRequired());
		assertNull(result.bankResponse());
	}

	private void assertStatus(String response, MoneyTransferProtocolResultStatus expectedStatus) {
		MoneyTransferProtocolEvaluator.Evaluation result = MoneyTransferProtocolEvaluator.evaluate(false, response, false, null, false);
		assertEquals(expectedStatus, result.resultStatus());
	}
}
