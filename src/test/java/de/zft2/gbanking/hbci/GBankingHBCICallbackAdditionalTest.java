package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;

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

			assertThrows(HBCI_Exception.class,
					() -> callback.callback(null, GBankingHBCICallback.NEED_PT_DECOUPLED, "Bitte in der App freigeben", 0, new StringBuffer()));
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

	private static BankAccess createBankAccess() {
		BankAccess bankAccess = new BankAccess();
		bankAccess.setPin("12345".toCharArray());
		bankAccess.setBlz("10020030");
		bankAccess.setUserId("user-1");
		return bankAccess;
	}

	private static void assertCallbackValue(GBankingHBCICallback callback, int reason, String expectedValue) {
		StringBuffer retData = new StringBuffer("old");

		callback.callback(null, reason, "msg", 0, retData);

		assertEquals(expectedValue, retData.toString());
	}
}
