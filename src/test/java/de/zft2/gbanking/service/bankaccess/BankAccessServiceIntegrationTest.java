package de.zft2.gbanking.service.bankaccess;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.mockito.MockedConstruction;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;
import de.zft2.gbanking.testdata.TestDataFactory;
import de.zft2.gbanking.testdata.HbciParameterTestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankAccessServiceIntegrationTest {

	private DBController dbController;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterEach
	void resetServices() {
		ServiceRegistry.removeService(BankAccessService.class);
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void initBankAccess_shouldLoadPersistedAccessAndAttachPin() {
		BankAccessService service = new BankAccessService();
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		BankAccount account = TestDataFactory.createSampleAccount(bankAccess.getId());
		char[] pin = "1234".toCharArray();

		BankAccess initializedAccess = service.initBankAccess(account, pin);

		assertNotNull(initializedAccess);
		assertEquals(bankAccess.getId(), initializedAccess.getId());
		assertEquals("10020030", initializedAccess.getFints().getBlz());
		assertSame(pin, initializedAccess.getPin());
	}

	@Test
	void saveBankAccessAccountsToDB_shouldPersistAccountsAsActiveOnlineAccountsWithBusinessCases() {
		BankAccessService service = new BankAccessService();
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("20030040"));
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setOfflineAccount(true);
		account.setAccountState(null);
		account.setAllowedBusinessCases(List.of(TestDataFactory.createBusinessCase("HKCCS"), TestDataFactory.createBusinessCase("HKIPZ")));
		bankAccess.setAccounts(List.of(account));

		boolean result = service.saveBankAccessAccountsToDB(bankAccess);

		List<BankAccount> accounts = dbController.getAllByParent(BankAccount.class, bankAccess.getId());
		assertTrue(result);
		assertEquals(1, accounts.size());
		assertEquals(bankAccess.getId(), accounts.get(0).getBankAccessId());
		assertFalse(accounts.get(0).isOfflineAccount());
		assertEquals(AccountState.ACTIVE, accounts.get(0).getAccountState());
		assertEquals(2, dbController.getAll(BusinessCase.class).size());
	}

	@Test
	void deleteBankAccessFromDB_shouldKeepAccountsAsManualAndRemoveAccess() {
		BankAccessService service = new BankAccessService();
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("30040050"));
		BankAccount account = TestDataFactory.createSampleAccount(bankAccess.getId());
		account.setSource(Source.ONLINE);
		account = dbController.insertOrUpdate(account);
		bankAccess.setAccounts(List.of(account));

		boolean result = service.deleteBankAccessFromDB(bankAccess);

		BankAccount reloadedAccount = dbController.getByIdFull(BankAccount.class, account.getId());
		assertTrue(result);
		assertNull(dbController.getBankAccessByBlz("30040050"));
		assertEquals(Source.MANUELL, reloadedAccount.getSource());
	}

	@Test
	void addNewBankAccess_shouldPopulatePassportDataReuseExistingAccessAndClearPin() {
		BankAccessService bankAccessService = ServiceStubbingUtil.spyService(BankAccessService.class);
		BankAccess existingAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("40050060"));
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess(null);
		char[] pin = "12345".toCharArray();
		bankAccess.setPin(pin);
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		Properties bpd = HbciParameterTestDataFactory.buildCapabilityBpd("HKCCS");
		Properties upd = HbciParameterTestDataFactory.buildCapabilityUpd("HKCCS");
		Konto konto = createKonto("DE44400500601234567890", "40050060", "123456789");

		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(passport.getBLZ()).thenReturn("40050060");
		when(passport.getBPD()).thenReturn(bpd);
		when(passport.getUPD()).thenReturn(upd);
		when(passport.getInstName()).thenReturn("Mock Bank");
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		doReturn(passport).when(bankAccessService).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
		doReturn(handle).when(bankAccessService).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = bankAccessService.addNewBankAccess(bankAccess);

			assertTrue(result);
			assertEquals(existingAccess.getId(), bankAccess.getId());
			assertEquals("40050060", bankAccess.getFints().getBlz());
			assertEquals("Mock Bank", bankAccess.getBankName());
			assertSame(bpd, bankAccess.getFints().getBpd());
			assertSame(upd, bankAccess.getFints().getUpd());
			assertEquals(1, bankAccess.getAccounts().size());
			assertEquals(konto.iban, bankAccess.getAccounts().get(0).getIban());
			assertEquals(konto.number, bankAccess.getAccounts().get(0).getNumber());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0', '\0' }, pin);
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
		}
	}

	@Test
	void refreshBankAccessParameterData_shouldPersistPassportDataAccountsAndClearPin() {
		BankAccessService bankAccessService = ServiceStubbingUtil.spyService(BankAccessService.class);
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("50060070"));
		char[] pin = "56789".toCharArray();
		HBCIPassport passport = mock(HBCIPassport.class);
		HBCIHandler handle = mock(HBCIHandler.class);
		Properties bpd = HbciParameterTestDataFactory.buildCapabilityBpd("HKCDE");
		Properties upd = HbciParameterTestDataFactory.buildCapabilityUpd("HKCDE");
		Konto konto = createKonto("DE44500600701234567890", "50060070", "987654321");

		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(passport.getBLZ()).thenReturn("50060070");
		when(passport.getCountry()).thenReturn("DE");
		when(passport.getHost()).thenReturn("https://fints.example.test");
		when(passport.getPort()).thenReturn(443);
		when(passport.getUserId()).thenReturn("refresh-user");
		when(passport.getCustomerId()).thenReturn("refresh-customer");
		when(passport.getHBCIVersion()).thenReturn("300");
		when(passport.getBPDVersion()).thenReturn("2");
		when(passport.getUPDVersion()).thenReturn("3");
		when(passport.getFilterType()).thenReturn(HbciEncodingFilterType.BASE64.toString());
		when(passport.getBPD()).thenReturn(bpd);
		when(passport.getUPD()).thenReturn(upd);
		when(passport.getInstName()).thenReturn("Refresh Bank");
		doReturn(passport).when(bankAccessService).initBankConnection(any(BankAccess.class), any(GBankingHBCICallback.class));
		doReturn(handle).when(bankAccessService).createHBCIHandler(eq(BaseMessagesDb.getVersion().getId()), same(passport));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = bankAccessService.refreshBankAccessParameterData(bankAccess, pin);

			assertTrue(result);
			assertEquals("Refresh Bank", bankAccess.getBankName());
			assertEquals("https://fints.example.test", bankAccess.getFints().getHbciURL());
			assertEquals("2", bankAccess.getFints().getBpdVersion());
			assertEquals("3", bankAccess.getFints().getUpdVersion());
			assertSame(bpd, bankAccess.getFints().getBpd());
			assertSame(upd, bankAccess.getFints().getUpd());
			assertEquals(1, dbController.getAllByParent(BankAccount.class, bankAccess.getId()).size());
			assertEquals(1, dbController.getAllByParent(Bpd.class, bankAccess.getId()).size());
			assertEquals(2, dbController.getAllByParent(Upd.class, bankAccess.getId()).size());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0', '\0' }, pin);
			verify(handle).refreshXPD(HBCIHandler.REFRESH_BPD + HBCIHandler.REFRESH_UPD);
			verify(passport).saveChanges();
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
		}
	}

	private static Konto createKonto(String iban, String blz, String number) {
		Konto konto = new Konto();
		konto.country = "DE";
		konto.blz = blz;
		konto.number = number;
		konto.iban = iban;
		konto.bic = "BANKDEFFXXX";
		konto.name = "John Doe";
		konto.type = "Girokonto";
		konto.acctype = "0";
		konto.curr = "EUR";
		konto.allowedGVs = List.of("HKCCS", "HKIPZ");
		return konto;
	}
}
