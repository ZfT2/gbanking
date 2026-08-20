package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRVoP;
import org.kapott.hbci.GV_Result.GVRVoP.VoPStatus;
import org.kapott.hbci.comm.Comm;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassportInternal;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.RecipientCheckDecision;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.RecipientCheckRequest;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.TanChallenge;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.TanChallengeType;

class GBankingHBCICallbackAdditionalTest {

	@Test
	void callback_shouldFillStandardBankAccessValues() {
		BankAccess bankAccess = createBankAccess();

		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> ignored = Mockito.mockConstruction(HbciCallbackMessageDialog.class)) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(bankAccess);

			assertCallbackValue(callback, GBankingHBCICallback.NEED_PT_PIN, "12345");
			assertCallbackValue(callback, GBankingHBCICallback.NEED_PASSPHRASE_LOAD, "12345");
			assertCallbackValue(callback, GBankingHBCICallback.NEED_BLZ, "10020030");
			assertCallbackValue(callback, GBankingHBCICallback.NEED_USERID, "user-1");
			assertCallbackValue(callback, GBankingHBCICallback.NEED_CUSTOMERID, "user-1");
		}
	}

	@Test
	void needPtTan_shouldUseChipTanReaderWhenPayloadIsSupported() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class)) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(true);
			chipTanMock.when(() -> ChipTanUsbSupport.isChipTanPayload("0FA1BC")).thenReturn(true);
			chipTanMock.when(ChipTanUsbSupport::getConfiguredReaderName).thenReturn("");
			chipTanMock.when(() -> ChipTanUsbSupport.requestTan("0FA1BC")).thenReturn(" 987654 ");

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("0FA1BC");

			callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			assertEquals("987654", retData.toString());
			verify(dialog, never()).requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
			verify(dialog).appendMessages(Mockito.contains("chipTAN"));
		}
	}

	@Test
	void needPtTan_shouldFallbackToManualTanWhenChipTanReaderFails() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn(" 111222 "))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(true);
			chipTanMock.when(() -> ChipTanUsbSupport.isChipTanPayload("0FA1BC")).thenReturn(true);
			chipTanMock.when(ChipTanUsbSupport::getConfiguredReaderName).thenReturn("Reader 01");
			chipTanMock.when(() -> ChipTanUsbSupport.requestTan("0FA1BC")).thenThrow(new IllegalStateException("reader offline"));

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("0FA1BC");

			callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			assertEquals("111222", retData.toString());
			verify(dialog).requestSecretInput(Mockito.anyString(), Mockito.eq("0FA1BC"), Mockito.anyString(), Mockito.anyString());
			verify(dialog).appendMessages(Mockito.contains("Reader 01"));
		}
	}

	@Test
	void needPtTan_shouldPassFlickerChallengeForOpticalChipTan() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.any(TanChallenge.class),
								Mockito.anyString(), Mockito.anyString())).thenReturn(" 333444 "))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(false);
			chipTanMock.when(() -> ChipTanUsbSupport.isChipTanPayload("0FA1BC")).thenReturn(true);

			BankAccess bankAccess = new BankAccess();
			bankAccess.getFints().setTanProcedure(TanProcedure.CHIP_TAN_OPTICAL);
			GBankingHBCICallback callback = new GBankingHBCICallback(bankAccess);
			StringBuffer retData = new StringBuffer("0FA1BC");

			callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			ArgumentCaptor<TanChallenge> challengeCaptor = ArgumentCaptor.forClass(TanChallenge.class);
			assertEquals("333444", retData.toString());
			verify(dialog).requestSecretInput(Mockito.anyString(), Mockito.eq("0FA1BC"), challengeCaptor.capture(), Mockito.anyString(),
					Mockito.anyString());
			TanChallenge challenge = challengeCaptor.getValue();
			assertEquals(TanChallengeType.FLICKER, challenge.type());
			assertEquals("0FA1BC", challenge.flickerCode());
		}
	}

	@Test
	void needPtPhotoTan_shouldPassImageChallenge() throws Exception {
		byte[] imageBytes = createMatrixImageBytes();
		String payload = createMatrixPayload(imageBytes);

		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.any(TanChallenge.class),
								Mockito.anyString(), Mockito.anyString())).thenReturn(" 555666 "))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(false);

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer(payload);

			callback.callback(null, GBankingHBCICallback.NEED_PT_PHOTOTAN, "Bitte PhotoTAN scannen", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			ArgumentCaptor<TanChallenge> challengeCaptor = ArgumentCaptor.forClass(TanChallenge.class);
			assertEquals("555666", retData.toString());
			verify(dialog).requestSecretInput(Mockito.anyString(), Mockito.eq(payload), challengeCaptor.capture(), Mockito.anyString(),
					Mockito.anyString());
			TanChallenge challenge = challengeCaptor.getValue();
			assertEquals(TanChallengeType.IMAGE, challenge.type());
			assertArrayEquals(imageBytes, challenge.imageBytes());
		}
	}

	@Test
	void needPtTanMedia_shouldKeepOriginalValueWhenUserCancelsSelection() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestSelection(Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.anyString(),
								Mockito.anyString())).thenReturn(null))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("900:pushTAN|910:smsTAN");

			callback.callback(null, GBankingHBCICallback.NEED_PT_TANMEDIA, "TAN-Medium", 0, retData);

			assertEquals("900:pushTAN|910:smsTAN", retData.toString());
			verify(dialogConstruction.constructed().get(0)).requestSelection(Mockito.anyString(), Mockito.anyString(), Mockito.anyList(),
					Mockito.anyString(), Mockito.anyString());
		}
	}

	@Test
	void needPtDecoupled_shouldThrowWhenUserCancelsAuthorization() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> ignored = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn(false))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());

			StringBuffer sb = new StringBuffer();
			assertThrows(HBCI_Exception.class,
					() -> callback.callback(null, GBankingHBCICallback.NEED_PT_DECOUPLED, "Bitte in der App freigeben", 0, sb));
		}
	}

	@Test
	void haveVoPResult_shouldWriteUserDecisionToReturnData() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> ignored = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn(true))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("false");

			callback.callback(null, GBankingHBCICallback.HAVE_VOP_RESULT, "Empfaengerpruefung erfolgreich", 0, retData);

			assertEquals("true", retData.toString());
		}
	}

	@Test
	void haveVoPResult_closeMatchShouldRequestExplicitDecisionAndStoreCorrectedName() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestRecipientCheckDecision(Mockito.any(RecipientCheckRequest.class), Mockito.anyString(),
								Mockito.anyString())).thenReturn(new RecipientCheckDecision(true, "Muster GmbH")))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			callback.setCurrentMoneyTransfer(createMoneyTransfer());
			StringBuffer retData = new StringBuffer("false");

			callback.callback(createPassportWithVoPResult(VoPStatus.CLOSE_MATCH), GBankingHBCICallback.HAVE_VOP_RESULT, "VOP", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			ArgumentCaptor<RecipientCheckRequest> requestCaptor = ArgumentCaptor.forClass(RecipientCheckRequest.class);
			verify(dialog).requestRecipientCheckDecision(requestCaptor.capture(), Mockito.anyString(), Mockito.anyString());
			assertEquals("true", retData.toString());
			assertEquals("Muster GmbH", callback.getConfirmedRecipientName());
			RecipientCheckRequest request = requestCaptor.getValue();
			assertEquals("Muster GmBH", request.initialRecipientName());
			assertEquals("Muster GmBH", request.recipientName());
			assertEquals("DE02120300000000202051", request.iban());
			assertEquals("GENODEF1MST", request.bic());
			assertEquals("Musterbank", request.bank());
			assertEquals("Rechnung 4711", request.purpose());
			assertFalse(request.freeRecipientNameInput());
			assertEquals("Muster GmBH", request.recipientNameOptions().get(0).value());
			assertEquals("Muster GmbH", request.recipientNameOptions().get(1).value());
		}
	}

	@Test
	void haveVoPResult_noMatchShouldRequestFreeRecipientNameInput() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestRecipientCheckDecision(Mockito.any(RecipientCheckRequest.class), Mockito.anyString(),
								Mockito.anyString())).thenReturn(new RecipientCheckDecision(true, "Korrigierte GmbH")))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("false");

			callback.callback(createPassportWithVoPResult(VoPStatus.NO_MATCH), GBankingHBCICallback.HAVE_VOP_RESULT, "VOP", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			ArgumentCaptor<RecipientCheckRequest> requestCaptor = ArgumentCaptor.forClass(RecipientCheckRequest.class);
			verify(dialog).requestRecipientCheckDecision(requestCaptor.capture(), Mockito.anyString(), Mockito.anyString());
			RecipientCheckRequest request = requestCaptor.getValue();
			assertEquals("true", retData.toString());
			assertEquals("Korrigierte GmbH", callback.getConfirmedRecipientName());
			assertEquals("Muster GmBH", request.initialRecipientName());
			assertTrue(request.freeRecipientNameInput());
			assertEquals(0, request.recipientNameOptions().size());
		}
	}

	@Test
	void haveVoPResult_matchShouldContinueWithoutExplicitConfirmation() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class)) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("false");

			callback.callback(createPassportWithVoPResult(VoPStatus.MATCH), GBankingHBCICallback.HAVE_VOP_RESULT, "VOP", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			assertEquals("true", retData.toString());
			verify(dialog, never()).requestRecipientCheckDecision(Mockito.any(), Mockito.anyString(), Mockito.anyString());
			verify(dialog, never()).requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
			verify(dialog, never()).appendMessages(Mockito.contains("Match"));
		}
	}

	private static byte[] createMatrixImageBytes() throws Exception {
		int mimeTypeLength = "image/png".getBytes(Comm.ENCODING).length;
		byte[] imageBytes = new byte[100 - mimeTypeLength - 4];
		for (int i = 0; i < imageBytes.length; i++) {
			imageBytes[i] = (byte) (i + 1);
		}
		return imageBytes;
	}

	private static String createMatrixPayload(byte[] imageBytes) throws Exception {
		byte[] mimeType = "image/png".getBytes(Comm.ENCODING);
		byte[] payload = new byte[4 + mimeType.length + imageBytes.length];
		payload[0] = 0;
		payload[1] = (byte) mimeType.length;
		System.arraycopy(mimeType, 0, payload, 2, mimeType.length);
		System.arraycopy(imageBytes, 0, payload, 4 + mimeType.length, imageBytes.length);
		return new String(payload, Comm.ENCODING);
	}

	private static BankAccess createBankAccess() {
		BankAccess bankAccess = new BankAccess();
		bankAccess.setPin("12345".toCharArray());
		bankAccess.getFints().setBlz("10020030");
		bankAccess.getFints().setUserId("user-1");
		return bankAccess;
	}

	private static MoneyTransfer createMoneyTransfer() {
		Recipient recipient = new Recipient();
		recipient.setName("Muster GmBH");
		recipient.setIban("DE02120300000000202051");
		recipient.setBic("GENODEF1MST");
		recipient.setBank("Musterbank");

		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setOrderType(OrderType.SCHEDULED_TRANSFER);
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose("Rechnung 4711");
		return moneyTransfer;
	}

	private static HBCIPassportInternal createPassportWithVoPResult(VoPStatus status) {
		HBCIPassportInternal passport = mock(HBCIPassportInternal.class);
		when(passport.getPersistentData(AbstractHBCIPassport.KEY_VOP_RESULT)).thenReturn(createVoPResult(status));
		return passport;
	}

	private static GVRVoP.VoPResult createVoPResult(VoPStatus status) {
		GVRVoP.VoPResult result = new GVRVoP.VoPResult();
		GVRVoP.VoPResultItem item = new GVRVoP.VoPResultItem();
		item.setStatus(status);
		item.setOriginal("Muster GmBH");
		item.setName("Muster GmbH");
		item.setIban("DE02120300000000202051");
		item.setAmount(new BigDecimal("10.00"));
		item.setUsage("Rechnung");
		result.getItems().add(item);
		return result;
	}

	private static void assertCallbackValue(GBankingHBCICallback callback, int reason, String expectedValue) {
		StringBuffer retData = new StringBuffer("old");

		callback.callback(null, reason, "msg", 0, retData);

		assertEquals(expectedValue, retData.toString());
	}
}
