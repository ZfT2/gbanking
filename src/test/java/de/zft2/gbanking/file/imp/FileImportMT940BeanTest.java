package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportMT940BeanTest {

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
	void importFileToDatabase_shouldImportMt940BookingAndSkipDuplicateOnSecondImport() throws Exception {
		BankAccount account = createAccount("MT940 Konto", "DE11111111111111111111", "11111111");
		Path mt940File = writeMt940("""
				:20:GBANKING
				:25:DE11111111111111111111
				:28C:1/1
				:60F:C260410EUR0,00
				:61:2604100410C123,45NTRFNONREF//INSTREF1
				:86:166?00Gutschrift?20MT940 Zweck?30TESTDEFFXXX?31DE99999999999999999999?32MT940 Empfaenger
				:62F:C260410EUR123,45
				-
				""");

		FileImportMT940Bean firstImport = new FileImportMT940Bean(null);
		firstImport.importFileToDatabase(mt940File.toString());

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		Booking importedBooking = bookings.get(0);
		assertEquals(new BigDecimal("123.45"), importedBooking.getAmount());
		assertEquals(Source.IMPORT, importedBooking.getSource());
		assertEquals(BookingType.DEPOSIT, importedBooking.getBookingType());
		assertEquals("MT940 Zweck\n", importedBooking.getPurpose());
		BookingAdditionalDetails details = importedBooking.getAdditionalDetails();
		assertNotNull(details);
		assertEquals("INSTREF1", details.getInstref());
		assertNotNull(importedBooking.getRecipient());
		assertEquals("MT940 Empfaenger", importedBooking.getRecipient().getName());

		FileImportMT940Bean secondImport = new FileImportMT940Bean(null);
		secondImport.importFileToDatabase(mt940File.toString());

		assertEquals(1, dbController.getAllByParentFull(Booking.class, account.getId()).size());
		FileImportBean.ImportAccountStatistics statistics = secondImport.getImportStatistics().get(0);
		assertEquals(1, statistics.getExistingBookings());
		assertEquals(0, statistics.getAddedBookings());
		assertEquals(1, statistics.getSkippedBookings());
	}

	private BankAccount createAccount(String name, String iban, String number) {
		BankAccount account = TestData.createSampleAccount(null);
		account.setAccountName(name);
		account.setIban(iban);
		account.setNumber(number);
		return dbController.insertOrUpdate(account);
	}

	private Path writeMt940(String content) throws Exception {
		Path mt940File = tempDir.resolve("import-" + System.nanoTime() + ".sta");
		Files.writeString(mt940File, content.stripIndent().replace("\n", "\r\n"), StandardCharsets.ISO_8859_1);
		return mt940File;
	}
}
