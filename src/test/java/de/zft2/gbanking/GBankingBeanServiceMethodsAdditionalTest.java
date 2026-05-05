package de.zft2.gbanking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GBankingBeanServiceMethodsAdditionalTest {

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

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void addNewBankAccess_shouldPopulateAccountsReuseExistingAccessAndClearPin() {
		GBankingBean bean = spy(new GBankingBean());
		BankAccess existingAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("10020030"));
		BankAccess bankAccess = TestData.createSampleBankAccess(null);
		char[] pin = "12345".toCharArray();
		bankAccess.setPin(pin);

		HBCIPassport passport = mock(HBCIPassport.class);
		Properties upd = new Properties();
		Properties bpd = new Properties();
		Konto konto = createKonto("DE44500105175407324931", "10020030", "987654321");

		when(passport.getAccounts()).thenReturn(new Konto[] { konto });
		when(passport.getBLZ()).thenReturn("10020030");
		when(passport.getUPD()).thenReturn(upd);
		when(passport.getBPD()).thenReturn(bpd);
		when(passport.getInstName()).thenReturn("Mock Bank");
		doReturn(passport).when(bean).initBankConnection(eq(bankAccess), any(GBankingHBCICallback.class));

		HBCIHandler handle = mock(HBCIHandler.class);
		HBCIExecStatus status = mock(HBCIExecStatus.class);
		when(status.isOK()).thenReturn(true);
		when(handle.execute()).thenReturn(status);
		doReturn(handle).when(bean).createHBCIHandler(eq(GBankingBean.getVersion().getId()), same(passport));

		try (MockedConstruction<GBankingHBCICallback> callbacks = mockConstruction(GBankingHBCICallback.class)) {
			boolean result = bean.addNewBankAccess(bankAccess);

			assertTrue(result);
			assertEquals(existingAccess.getId(), bankAccess.getId());
			assertEquals("10020030", bankAccess.getBlz());
			assertSame(upd, bankAccess.getUpd());
			assertSame(bpd, bankAccess.getBpd());
			assertEquals(1, bankAccess.getAccounts().size());
			assertEquals(konto.iban, bankAccess.getAccounts().get(0).getIban());
			assertArrayEquals(new char[] { '\0', '\0', '\0', '\0', '\0' }, pin);
			verify(callbacks.constructed().get(0)).startStatusDialog();
			verify(callbacks.constructed().get(0)).finishStatusDialog();
			verify(passport).close();
		}
	}

	@Test
	void deleteBankAccessFromDB_shouldKeepAccountsAsManualAndRemoveBankAccess() {
		GBankingBean bean = new GBankingBean();
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("20030040"));
		BankAccount account = TestData.createSampleAccount(bankAccess.getId());
		account.setSource(Source.ONLINE);
		account = dbController.insertOrUpdate(account);
		bankAccess.setAccounts(List.of(account));

		boolean result = bean.deleteBankAccessFromDB(bankAccess);

		assertTrue(result);
		assertNull(dbController.getBankAccessByBlz("20030040"));
		assertEquals(Source.MANUELL, dbController.getByIdFull(BankAccount.class, account.getId()).getSource());
	}

	@Test
	void saveBankAccessAccountsToDB_shouldPersistAccountsAsActiveOnlineAccountsWithBusinessCases() {
		GBankingBean bean = new GBankingBean();
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("30040050"));
		BankAccount account = TestData.createSampleAccount(null);
		account.setOfflineAccount(true);
		account.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA"), createBusinessCase("HKIPZ")));
		bankAccess.setAccounts(List.of(account));

		boolean result = bean.saveBankAccessAccountsToDB(bankAccess);

		BankAccount storedAccount = dbController.getAll(BankAccount.class).stream()
				.filter(accountFromDb -> bankAccess.getId() == accountFromDb.getBankAccessId()).findFirst().orElseThrow();
		assertTrue(result);
		assertEquals(bankAccess.getId(), storedAccount.getBankAccessId());
		assertFalse(storedAccount.isOfflineAccount());
		assertEquals(AccountState.ACTIVE, storedAccount.getAccountState());
		assertEquals(2, dbController.getAll(BusinessCase.class).size());
	}

	@Test
	void saveRecipientToDB_shouldInsertNewRecipientAndUpdateExistingNoteOnlyOnce() {
		GBankingBean bean = new GBankingBean();
		Recipient newRecipient = new Recipient("Recipient One", "DE11111111111111111111", "TESTDEFFXXX", null, null, "Testbank", Source.MANUELL);

		Recipient savedRecipient = bean.saveRecipientToDB(newRecipient);

		assertTrue(savedRecipient.getId() > 0);
		assertEquals(1, dbController.getAll(Recipient.class).size());

		Recipient recipientWithUpdatedNote = new Recipient("Recipient One", "DE11111111111111111111", "TESTDEFFXXX", null, null, "Testbank",
				Source.MANUELL);
		recipientWithUpdatedNote.setNote("Updated note");

		Recipient updatedRecipient = bean.saveRecipientToDB(recipientWithUpdatedNote);

		assertEquals(savedRecipient.getId(), updatedRecipient.getId());
		assertEquals("Updated note", dbController.getByIdFull(Recipient.class, savedRecipient.getId()).getNote());
		assertEquals(1, dbController.getAll(Recipient.class).size());
	}

	@Test
	void deleteBookingsInBlock_shouldDeleteOnlyOnlineFamilyFromReferenceDate() {
		GBankingBean bean = new GBankingBean();
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		insertBooking(account.getId(), Source.ONLINE, LocalDate.of(2026, 1, 1));
		Booking reference = insertBooking(account.getId(), Source.ONLINE, LocalDate.of(2026, 1, 10));
		insertBooking(account.getId(), Source.ONLINE_NEW, LocalDate.of(2026, 1, 20));
		insertBooking(account.getId(), Source.IMPORT, LocalDate.of(2026, 1, 20));
		insertBooking(account.getId(), Source.MANUELL, LocalDate.of(2026, 1, 20));

		int deletedCount = bean.deleteBookingsInBlock(reference, true);

		List<Booking> remainingBookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(2, deletedCount);
		assertEquals(3, remainingBookings.size());
		assertTrue(remainingBookings.stream().anyMatch(booking -> booking.getSource() == Source.ONLINE));
		assertTrue(remainingBookings.stream().anyMatch(booking -> booking.getSource() == Source.IMPORT));
		assertTrue(remainingBookings.stream().anyMatch(booking -> booking.getSource() == Source.MANUELL));
	}

	@Test
	void deleteBookingsInBlock_shouldDeleteImportFamilyUntilReferenceValueDate() {
		GBankingBean bean = new GBankingBean();
		BankAccount account = dbController.insertOrUpdate(TestData.createSampleAccount(null));
		insertBookingWithValueDateOnly(account.getId(), Source.IMPORT, LocalDate.of(2026, 2, 1));
		Booking reference = insertBookingWithValueDateOnly(account.getId(), Source.IMPORT_NEW, LocalDate.of(2026, 2, 10));
		insertBookingWithValueDateOnly(account.getId(), Source.IMPORT_INITIAL, LocalDate.of(2026, 2, 20));

		int deletedCount = bean.deleteBookingsInBlock(reference, false);

		List<Booking> remainingBookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(2, deletedCount);
		assertEquals(1, remainingBookings.size());
		assertEquals(LocalDate.of(2026, 2, 20), remainingBookings.get(0).getDateValue());
	}

	@Test
	void deleteBookingsInBlock_shouldIgnoreInvalidReferences() {
		GBankingBean bean = new GBankingBean();
		Booking manualBooking = new Booking();
		manualBooking.setAccountId(1);
		manualBooking.setDateBooking(LocalDate.now());
		manualBooking.setSource(Source.MANUELL);

		assertEquals(0, bean.deleteBookingsInBlock(null, true));
		assertEquals(0, bean.deleteBookingsInBlock(manualBooking, true));
	}

	@Test
	void initBankAccess_shouldLoadAccessAndAttachPinOrReturnNullWithoutAccessId() {
		GBankingBean bean = new GBankingBean();
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("40050060"));
		BankAccount account = TestData.createSampleAccount(bankAccess.getId());
		char[] pin = "9876".toCharArray();

		BankAccess initializedAccess = bean.initBankAccess(account, pin);

		assertEquals(bankAccess.getId(), initializedAccess.getId());
		assertSame(pin, initializedAccess.getPin());

		account.setBankAccessId(0);
		assertNull(bean.initBankAccess(account, pin));
	}

	@Test
	void initBankConnection_shouldConfigurePinTanPassportFromBankInfoAndProductKey() {
		GBankingBean bean = new GBankingBean();
		BankAccess bankAccess = TestData.createSampleBankAccess("50060070");
		GBankingHBCICallback callback = mock(GBankingHBCICallback.class);
		HBCIPassport passport = mock(HBCIPassport.class);
		BankInfo bankInfo = mock(BankInfo.class);
		when(bankInfo.getPinTanAddress()).thenReturn("https://fints.example.test");
		Setting productKey = new Setting();
		productKey.setAttribute("productKey");
		productKey.setValue("TestProduct");
		productKey.setDataType(DataType.STRING);
		dbController.insertOrUpdate(productKey);

		try (MockedStatic<HBCIUtils> hbciUtils = mockStatic(HBCIUtils.class);
				MockedStatic<AbstractHBCIPassport> passportFactory = mockStatic(AbstractHBCIPassport.class)) {
			hbciUtils.when(() -> HBCIUtils.getBankInfo("50060070")).thenReturn(bankInfo);
			passportFactory.when(() -> AbstractHBCIPassport.getInstance("PinTanDB", "50060070")).thenReturn(passport);

			HBCIPassport initializedPassport = bean.initBankConnection(bankAccess, callback);

			assertSame(passport, initializedPassport);
			hbciUtils.verify(() -> HBCIUtils.init(any(Properties.class), same(callback)));
			hbciUtils.verify(() -> HBCIUtils.setParam("client.passport.PinTan.init", "1"));
			hbciUtils.verify(() -> HBCIUtils.setParam("client.product.name", "TestProduct"));
			verify(passport).setCountry("DE");
			verify(passport).setHost("https://fints.example.test");
			verify(passport).setPort(443);
			verify(passport).setFilterType(HbciEncodingFilterType.BASE64.toString());
		}
	}

	@Test
	void initBankConnection_shouldFailWhenBankInfoHasNoFinTsAddress() {
		GBankingBean bean = new GBankingBean();
		BankAccess bankAccess = TestData.createSampleBankAccess("60070080");
		HBCIPassport passport = mock(HBCIPassport.class);

		try (MockedStatic<HBCIUtils> hbciUtils = mockStatic(HBCIUtils.class);
				MockedStatic<AbstractHBCIPassport> passportFactory = mockStatic(AbstractHBCIPassport.class)) {
			BankInfo bankInfo = mock(BankInfo.class);
			when(bankInfo.getPinTanAddress()).thenReturn(null);
			hbciUtils.when(() -> HBCIUtils.getBankInfo("60070080")).thenReturn(bankInfo);
			passportFactory.when(() -> AbstractHBCIPassport.getInstance("PinTanDB", "60070080")).thenReturn(passport);

			assertThrows(GBankingException.class, () -> bean.initBankConnection(bankAccess, mock(GBankingHBCICallback.class)));
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
		konto.allowedGVs = List.of("UebSEPA", "KUmsAllCamt");
		return konto;
	}

	private static BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}

	private Booking insertBooking(int accountId, Source source, LocalDate date) {
		Booking booking = TestData.createSampleBooking(accountId);
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setSource(source);
		booking.setAmount(new BigDecimal("10.00"));
		booking.setBookingType(BookingType.DEPOSIT);
		return dbController.insertOrUpdate(booking);
	}

	private Booking insertBookingWithValueDateOnly(int accountId, Source source, LocalDate valueDate) {
		Booking booking = insertBooking(accountId, source, valueDate);
		booking.setDateBooking(null);
		booking.setDateValue(valueDate);
		return dbController.insertOrUpdate(booking);
	}
}
