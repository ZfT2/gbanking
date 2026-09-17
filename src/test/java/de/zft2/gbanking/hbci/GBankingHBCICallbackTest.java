package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;

class GBankingHBCICallbackTest {

	@Test
	void statusShouldUpdateCurrentActionForRegisteredJob() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(HbciCallbackMessageDialog.class)) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			GBankingHBCICallback callback = new GBankingHBCICallback(new BankAccess());
			HBCIJob<?> job = mock(HBCIJob.class);
			callback.registerJobDescription(job, "Hole Kontoauszug 1 von 3 für Konto Girokonto...");

			callback.status(null, GBankingHBCICallback.STATUS_SEND_TASK, new Object[] { job });

			verify(dialogConstruction.constructed().get(0)).updateCurrentAction("Hole Kontoauszug 1 von 3 für Konto Girokonto...");
		}
	}

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
	void needPtSecMechShouldReuseSelectionForSameBankAccessWithinRetrievalScope() {
		try (MockedStatic<DialogWindowSupport> dialogSupportMock = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogConstruction = Mockito.mockConstruction(
						HbciCallbackMessageDialog.class, (mock, context) -> when(mock.requestSelection(Mockito.anyString(),
								Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
								.thenReturn("907"));
				GBankingHBCICallback.SecurityMechanismSelectionScope ignored =
						GBankingHBCICallback.openSecurityMechanismSelectionScope()) {
			dialogSupportMock.when(DialogWindowSupport::findBestOwnerWindow).thenReturn(Optional.empty());
			BankAccess firstAccess = bankAccess(42);
			BankAccess secondAccess = bankAccess(42);
			GBankingHBCICallback firstCallback = new GBankingHBCICallback(firstAccess);
			GBankingHBCICallback secondCallback = new GBankingHBCICallback(secondAccess);
			StringBuffer firstResult = new StringBuffer("906:iTAN-Card|907:flateXSecure");
			StringBuffer secondResult = new StringBuffer("906:iTAN-Card|907:flateXSecure");

			firstCallback.callback(null, GBankingHBCICallback.NEED_PT_SECMECH, "Sicherheitsverfahren", 0, firstResult);
			secondCallback.callback(null, GBankingHBCICallback.NEED_PT_SECMECH, "Sicherheitsverfahren", 0, secondResult);

			assertEquals("907", firstResult.toString());
			assertEquals("907", secondResult.toString());
			assertSame(TanProcedure.APP_TAN, firstAccess.getFints().getTanProcedure());
			verify(dialogConstruction.constructed().get(0)).requestSelection(Mockito.anyString(), Mockito.anyString(),
					Mockito.anyList(), Mockito.anyString(), Mockito.anyString());
			verify(dialogConstruction.constructed().get(1), never()).requestSelection(Mockito.anyString(), Mockito.anyString(),
					Mockito.anyList(), Mockito.anyString(), Mockito.anyString());
		}
	}

	private BankAccess bankAccess(int id) {
		BankAccess access = new BankAccess();
		access.setId(id);
		return access;
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
			assertEquals(List.of("Confirmation of Payee mismatch"), callback.drainInstitutionMessages());
			assertEquals(List.of(), callback.drainInstitutionMessages());

			StringBuffer sb = new StringBuffer();
			assertThrows(HBCI_Exception.class,
					() -> callback.callback(null, GBankingHBCICallback.NEED_PT_TAN, "Bitte TAN eingeben", 0, sb));

			HbciCallbackMessageDialog dialog = dialogConstruction.constructed().get(0);
			verify(dialog).requestConfirmation(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
			verify(dialog, never()).requestSecretInput(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
		}
	}
}
