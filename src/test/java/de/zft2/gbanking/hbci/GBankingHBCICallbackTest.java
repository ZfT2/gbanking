package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.hbci.ChipTanUsbSupport;
import de.zft2.gbanking.hbci.GBankingHBCICallback;

import org.kapott.hbci.exceptions.HBCI_Exception;

class GBankingHBCICallbackTest {

	@Test
	void needPtTanShouldTrimManualTanInput() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn(" 123456 "))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(false);

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer();

			callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, retData);

			assertEquals("123456", retData.toString());
		}
	}

	@Test
	void needPtSecMechShouldReturnSingleOptionWithoutShowingSelectionDialog() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class)) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(false);

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			StringBuffer retData = new StringBuffer("900:pushTAN");

			callback.callback(null, GBankingHBCICallback.NEED_PT_SECMECH, "Sicherheitsverfahren", 0, retData);

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			assertEquals("900", retData.toString());
			verify(dialog, never()).requestSelection(Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString());
		}
	}

	@Test
	void pendingRecipientCheckShouldRequireConfirmationBeforeTanEntry() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedStatic<ChipTanUsbSupport> chipTanMock = mockStatic(ChipTanUsbSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class,
						(mock, context) -> when(mock.requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn(false))) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			chipTanMock.when(ChipTanUsbSupport::isEnabled).thenReturn(false);

			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			callback.callback(null, GBankingHBCICallback.HAVE_INST_MSG, "Confirmation of Payee mismatch", 0, new StringBuffer());

			assertThrows(HBCI_Exception.class,
					() -> callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, new StringBuffer()));

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			verify(dialog).requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
			verify(dialog, never()).requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
		}
	}
}
