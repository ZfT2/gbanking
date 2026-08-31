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
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.file.BaseFileTest;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportMT940BeanTest extends BaseFileTest {

	private DBController dbController;

	@BeforeAll
	void setupDatabase() throws Exception {
		HBCIUtils.initThread(new Properties(), new HBCICallbackConsole());
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
	void exportFileFromDatatbase_shouldWriteParseableMt940Statement() throws Exception {
		BankAccount account = createAccountWithBooking();
		Path exportFile = getExportFilePath("exports", "bookings.sta");

		boolean result = new FileExportMT940Bean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		assertTrue(result);
		assertTrue(Files.isRegularFile(exportFile));

		String mt940 = Files.readString(exportFile, StandardCharsets.ISO_8859_1);
		assertTrue(mt940.contains(":20:GBANKING" + account.getId()));
		assertTrue(mt940.contains(":25:" + account.getIban()));
		assertTrue(mt940.contains(":61:2604110410C123,45NTRF"));
		assertTrue(mt940.contains("?20MT940 Export Zweck"));
		assertTrue(mt940.contains("?32MT940 Empfaenger"));

		GVRKUms parsed = new GVRKUms();
		parsed.appendMT940Data("\r\n" + mt940);
		assertEquals(1, parsed.getFlatData().size());
	}

	private BankAccount createAccountWithBooking() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Giro MT940");
		account.setIban("DE11111111111111111111");
		account.setNumber("11111111");
		account = dbController.insertOrUpdate(account);

		Recipient recipient = new Recipient("MT940 Empfaenger", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517", "Testbank",
				Source.MANUELL);
		recipient = dbController.insertOrUpdate(recipient);

		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(LocalDate.of(2026, Month.APRIL, 10));
		booking.setDateValue(LocalDate.of(2026, Month.APRIL, 11));
		booking.setPurpose("MT940 Export Zweck");
		booking.setAmount(new BigDecimal("123.45"));
		booking.setRecipientId(recipient.getId());
		booking.setSource(Source.IMPORT);
		booking.setBookingType(BookingType.DEPOSIT);
		dbController.insertOrUpdate(booking);

		return account;
	}
}
