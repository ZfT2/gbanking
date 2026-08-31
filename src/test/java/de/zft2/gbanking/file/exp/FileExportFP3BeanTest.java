package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.file.BaseFileTest;
import de.zft2.gbanking.file.imp.FileImportBean;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportFP3BeanTest extends BaseFileTest {

	private DBController dbController;

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
	void exportFileFromDatatbase_shouldWriteImportableFp3PreparedReport() throws Exception {
		BankAccount account = createAccountWithBookings();
		Path exportFile = getExportFilePath("exports", "bookings.fp3");

		boolean result = new FileExportFP3Bean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		assertTrue(result);
		assertTrue(Files.isRegularFile(exportFile));
		String fp3 = Files.readString(exportFile, StandardCharsets.UTF_8);
		assertTrue(fp3.contains("<preparedreport>"));
		assertTrue(fp3.contains("FP3 Empfänger&#10;FP3 Zweck"));
		assertTrue(fp3.contains("Giro FP3"));

		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		BankAccount importAccount = createAccount();
		new FileImportBean(null, importAccount, true).importFileToDatatbase(exportFile.toString());

		List<BankAccount> accounts = dbController.getAll(BankAccount.class);
		assertEquals(1, accounts.size());
		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, importAccount.getId());
		assertEquals(2, bookings.size());
		assertTrue(bookings.stream().anyMatch(booking -> booking.getPurpose() != null && booking.getPurpose().contains("FP3 Zweck")));
	}

	private BankAccount createAccountWithBookings() {
		BankAccount account = createAccount();

		Recipient recipient = new Recipient("FP3 Empfänger", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517", "Testbank",
				Source.MANUELL);
		recipient = dbController.insertOrUpdate(recipient);

		Booking credit = createBooking(account.getId(), new BigDecimal("123.45"), "FP3 Zweck", BookingType.DEPOSIT);
		credit.setRecipientId(recipient.getId());
		dbController.insertOrUpdate(credit);
		dbController.insertOrUpdate(createBooking(account.getId(), new BigDecimal("-23.45"), "FP3 Ausgabe", BookingType.REMOVAL));
		return account;
	}

	private BankAccount createAccount() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Giro FP3");
		account.setIban("DE11111111111111111111");
		account.setNumber("11111111");
		account.setBalance(new BigDecimal("100.00"));
		return dbController.insertOrUpdate(account);
	}

	private Booking createBooking(int accountId, BigDecimal amount, String purpose, BookingType bookingType) {
		Booking booking = new Booking();
		booking.setAccountId(accountId);
		booking.setDateBooking(LocalDate.of(2026, Month.APRIL, 10));
		booking.setDateValue(LocalDate.of(2026, Month.APRIL, 11));
		booking.setPurpose(purpose);
		booking.setAmount(amount);
		booking.setSource(Source.IMPORT);
		booking.setBookingType(bookingType);
		return booking;
	}
}
