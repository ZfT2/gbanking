package de.zft2.gbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.AccountTransactionService;
import de.zft2.gbanking.GBankingBean;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.logging.GBankingLoggingHandler;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTransactionServiceTest {

	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
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
	void saveHbciBookingsForAccountShouldReuseExistingRecipientAndKeepNullRecipients() {
		AccountTransactionService service = new AccountTransactionService(mock(GBankingBean.class), mock(GBankingLoggingHandler.class));

		BankAccount account = new BankAccount();
		account.setIban("DE12345678901234567890");
		account.setNumber("12345678");
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setSource(Source.IMPORT_INITIAL);
		account.setAccountState(AccountState.ACTIVE);
		account.setCurrency("EUR");
		account = DBController.getInstance(tempDir.toString()).insertOrUpdate(account);

		Recipient existingRecipient = new Recipient();
		existingRecipient.setName("Max Mustermann");
		existingRecipient.setIban("DE99999999999999999999");
		existingRecipient.setBic("TESTDEFFXXX");
		existingRecipient.setSource(Source.ONLINE);
		existingRecipient = DBController.getInstance(tempDir.toString()).insertOrUpdate(existingRecipient);

		UmsLine bookingWithRecipient = createUmsLine(createKonto("Max Mustermann", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517"),
				"Testbuchung 1", BigDecimal.valueOf(123.45));
		UmsLine bookingWithoutRecipient = createUmsLine(null, "Testbuchung 2", BigDecimal.valueOf(67.89));

		service.saveHbciBookingsForAccount(account, List.of(bookingWithRecipient, bookingWithoutRecipient));

		List<Recipient> recipients = DBController.getInstance(tempDir.toString()).getAll(Recipient.class);
		assertEquals(1, recipients.size());

		List<Booking> bookings = DBController.getInstance(tempDir.toString()).getAllByParentFull(Booking.class, account.getId());
		assertEquals(2, bookings.size());

		Booking booking01 = bookings.stream().filter(booking -> booking.getRecipientId() > 0).findFirst().orElseThrow();
		Booking booking02 = bookings.stream().filter(booking -> booking.getRecipientId() == 0).findFirst().orElseThrow();

		assertEquals(existingRecipient.getId(), booking01.getRecipientId());
		assertEquals(existingRecipient.getIban(), booking01.getRecipient().getIban());
		assertNull(booking02.getRecipient());
	}

	@Test
	void createAndAddHbciJobShouldApplySupportedParamTypesAndQueueJob() throws Exception {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		GBankingLoggingHandler logHandler = mock(GBankingLoggingHandler.class);
		AccountTransactionService service = new AccountTransactionService(hbciSupport, logHandler);
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);

		when(hbciSupport.newHbciJob(handle, "TestJob")).thenReturn(job);

		Konto konto = createKonto("Name", "DE123", "BICCODE", "4711", "50010517");
		Date startDate = new Date();

		HBCIJob<?> createdJob = (HBCIJob<?>) invokePrivate(service, "createAndAddHbciJob",
				new Class<?>[] { HBCIHandler.class, String.class, Map.class },
				handle, "TestJob", Map.of("text", "value", "date", startDate, "count", Integer.valueOf(3), "konto", konto));

		assertEquals(job, createdJob);
		verify(job).setParam("text", "value");
		verify(job).setParam("date", startDate);
		verify(job).setParam("count", Integer.valueOf(3));
		verify(job).setParam("konto", konto);
		verify(job).addToQueue();
	}

	@Test
	void hbciKontosMatchesShouldMatchByIbanOrAccountNumber() throws Exception {
		AccountTransactionService service = new AccountTransactionService(mock(GBankingBean.class), mock(GBankingLoggingHandler.class));
		BankAccount account = new BankAccount();
		account.setIban("DE12345678901234567890");
		account.setNumber("12345678");

		Konto ibanMatch = createKonto("Name", "DE12345678901234567890", "BIC", "99999999", "50010517");
		Konto numberMatch = createKonto("Name", "DE00000000000000000000", "BIC", "12345678", "50010517");
		Konto noMatch = createKonto("Name", "DE00000000000000000000", "BIC", "87654321", "50010517");

		assertTrue((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, ibanMatch));
		assertTrue((Boolean) invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, numberMatch));
		assertEquals(Boolean.FALSE, invokePrivate(service, "hbciKontosMatches", new Class<?>[] { BankAccount.class, Konto.class }, account, noMatch));
	}

	private static UmsLine createUmsLine(Konto other, String usageLine, BigDecimal amount) {
		UmsLine umsLine = new UmsLine();
		umsLine.bdate = new Date();
		umsLine.valuta = new Date();
		umsLine.usage = List.of(usageLine);
		Value value = new Value();
		value.setCurr("EUR");
		value.setValue(amount);
		umsLine.value = value;
		umsLine.other = other;
		return umsLine;
	}

	private static Konto createKonto(String name, String iban, String bic, String number, String blz) {
		Konto konto = new Konto();
		konto.name = name;
		konto.iban = iban;
		konto.bic = bic;
		konto.number = number;
		konto.blz = blz;
		return konto;
	}

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, args);
	}
}
