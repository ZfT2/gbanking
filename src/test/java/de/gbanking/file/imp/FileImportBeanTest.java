package de.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.gbanking.db.DBController;
import de.gbanking.db.DBControllerTestUtil;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.BookingType;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportBeanTest {

	private FileImportBean fileImportBean;
	private File exportFile;

	private DBController dbController;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {

		// Create fresh DBControllerForTest instance
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

	@BeforeEach
	void setUp() throws Exception {
		fileImportBean = new FileImportBean(null);

		// Load test XML from resources
		URL resource = getClass().getClassLoader().getResource("dummy_import.xml");
		assertNotNull(resource, "Test XML file dummy_import.xml must be in src/test/resources");

		// Create temporary export file
		exportFile = File.createTempFile("export_test_", ".xml");
		exportFile.deleteOnExit();
	}

	@Test
	void testImportRecipients_Success() throws Exception {
		de.zft2.fp3xmlextract.data.BankAccount xmlBankAccount01 = new de.zft2.fp3xmlextract.data.BankAccount();
		xmlBankAccount01.setBezeichnung("Girokonto 01");
		xmlBankAccount01.setNamePP("Girokonto 01");
		xmlBankAccount01.setIban("DE00000000000000000001");
		xmlBankAccount01.setNumber("00000001");
		xmlBankAccount01.setBic("BANKDE00001");
		xmlBankAccount01.setType("Girokonto");

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0101 = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung 01",
				BigDecimal.valueOf(200.00), null, null, "Girokonto 01");
		xmlBookingAccount0101.setCrossReceiverName("Empfänger 01");
		xmlBookingAccount0101.setCrossAccountIBAN("DE00000000000000000001");
		xmlBookingAccount0101.setCrossAccountBIC("BANKDE00001");
		xmlBookingAccount0101.setCrossAccountNumber("00000001");
		xmlBookingAccount0101.setCrossBlz("100100001");
		xmlBookingAccount0101.setCrossBankName("Testbank 01");
		xmlBookingAccount0101.setCrossAccountNamePP(null);

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0102 = new de.zft2.fp3xmlextract.data.Booking("16.10.2025", "16.10.2025", "Testbuchung 02",
				BigDecimal.valueOf(100.00), null, null, "Girokonto 01");
		xmlBookingAccount0102.setCrossReceiverName("Empfänger 02");
		xmlBookingAccount0102.setCrossAccountIBAN("DE00000000000000000002");
		xmlBookingAccount0102.setCrossAccountBIC("BANKDE00002");
		xmlBookingAccount0102.setCrossAccountNumber("00000002");
		xmlBookingAccount0102.setCrossBlz("100100002");
		xmlBookingAccount0102.setCrossBankName("Testbank 02");
		xmlBookingAccount0102.setCrossAccountNamePP(null);

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0103 = new de.zft2.fp3xmlextract.data.Booking("19.10.2025", "19.10.2025", "Testbuchung 03",
				BigDecimal.valueOf(-50.00), null, null, "Girokonto 01");
		xmlBookingAccount0103.setCrossReceiverName("Empfänger 02");
		xmlBookingAccount0103.setCrossAccountIBAN("DE00000000000000000002");
		xmlBookingAccount0103.setCrossAccountBIC("BANKDE00002");
		xmlBookingAccount0103.setCrossAccountNumber("00000002");
		xmlBookingAccount0103.setCrossBlz("100100002");
		xmlBookingAccount0103.setCrossBankName("Testbank 02");
		xmlBookingAccount0103.setCrossAccountNamePP(null);

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0104 = new de.zft2.fp3xmlextract.data.Booking("20.10.2025", "20.10.2025", "Testbuchung 04",
				BigDecimal.valueOf(150.00), null, null, "Girokonto 01");
		xmlBookingAccount0104.setCrossReceiverName("Empfänger 04");
		xmlBookingAccount0104.setCrossAccountIBAN("DE00000000000000000004");
		xmlBookingAccount0104.setCrossAccountBIC("BANKDE00004");
		xmlBookingAccount0104.setCrossAccountNumber("00000004");
		xmlBookingAccount0104.setCrossBlz("100100004");
		xmlBookingAccount0104.setCrossBankName("Testbank 04");
		xmlBookingAccount0104.setCrossAccountNamePP(null);

		xmlBankAccount01.setBookings(Arrays.asList(xmlBookingAccount0101, xmlBookingAccount0102, xmlBookingAccount0103, xmlBookingAccount0104));

		de.zft2.fp3xmlextract.data.BankAccount xmlBankAccount02 = new de.zft2.fp3xmlextract.data.BankAccount();
		xmlBankAccount02.setBezeichnung("Girokonto 02");
		xmlBankAccount01.setNamePP("Girokonto 02");
		xmlBankAccount02.setIban("DE00000000000000000002");
		xmlBankAccount02.setNumber("00000002");
		xmlBankAccount02.setBic("BANKDE00002");
		xmlBankAccount02.setType("Girokonto");

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0201 = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung 05",
				BigDecimal.valueOf(400.00), null, null, "Girokonto 02");
		xmlBookingAccount0201.setCrossReceiverName("Empfänger 03");
		xmlBookingAccount0201.setCrossAccountIBAN("DE00000000000000000003");
		xmlBookingAccount0201.setCrossAccountBIC("BANKDE00003");
		xmlBookingAccount0201.setCrossAccountNumber("00000003");
		xmlBookingAccount0201.setCrossBlz("100100003");
		xmlBookingAccount0201.setCrossBankName("Testbank 03");
		xmlBookingAccount0201.setCrossAccountNamePP(null);

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0202 = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung 06",
				BigDecimal.valueOf(-600.00), null, null, "Girokonto 02");
		xmlBookingAccount0202.setCrossReceiverName("Empfänger 04");
		xmlBookingAccount0202.setCrossAccountIBAN("DE00000000000000000004");
		xmlBookingAccount0202.setCrossAccountBIC("BANKDE00004");
		xmlBookingAccount0202.setCrossAccountNumber("00000004");
		xmlBookingAccount0202.setCrossBlz("100100004");
		xmlBookingAccount0202.setCrossBankName("Testbank 04");
		xmlBookingAccount0202.setCrossAccountNamePP(null);

		xmlBankAccount02.setBookings(Arrays.asList(xmlBookingAccount0201, xmlBookingAccount0202));

		fileImportBean.writeAccountsToDB(Arrays.asList(xmlBankAccount01, xmlBankAccount02));
		fileImportBean.writeBookingsToDB(Arrays.asList(xmlBankAccount01, xmlBankAccount02));

		List<BankAccount> dbAccountList = dbController.getAllFull(BankAccount.class);
		assertEquals(2, dbAccountList.size());

		BankAccount bankAccount01 = dbAccountList.get(0);
		BankAccount bankAccount02 = dbAccountList.get(1);

		assertEquals(4, bankAccount01.getBookings().size());

		Booking booking0102 = bankAccount01.getBookings().stream().filter(booking -> "Testbuchung 02".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals("DE00000000000000000002", booking0102.getRecipient().getIban());
		// assertEquals(BookingType.DEPOSIT, booking0102.getBookingType());

		assertEquals(2, bankAccount02.getBookings().size());

		Booking booking0202 = bankAccount02.getBookings().stream().filter(booking -> "Testbuchung 06".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals("DE00000000000000000004", booking0202.getRecipient().getIban());
		// assertEquals(BookingType.REMOVAL, booking0202.getBookingType());

		List<Recipient> recipientsList = dbController.getAll(Recipient.class);
		assertEquals(4, recipientsList.size());

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0203 = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung 07",
				BigDecimal.valueOf(-600.00), null, null, "Girokonto 02");
		xmlBookingAccount0203.setCrossReceiverName("Empfänger 04");
		xmlBookingAccount0203.setCrossAccountIBAN("DE00000000000000000004");
		xmlBookingAccount0203.setCrossAccountBIC("BANKDE00004");
		xmlBookingAccount0203.setCrossAccountNumber("00000004");
		xmlBookingAccount0203.setCrossBlz("100100004");
		xmlBookingAccount0203.setCrossBankName("Testbank 04");
		xmlBookingAccount0203.setCrossAccountNamePP(null);

		de.zft2.fp3xmlextract.data.Booking xmlBookingAccount0204 = new de.zft2.fp3xmlextract.data.Booking("14.10.2025", "14.10.2025", "Testbuchung 08",
				BigDecimal.valueOf(-600.00), null, null, "Girokonto 02");
		xmlBookingAccount0204.setCrossReceiverName("Empfänger 05");
		xmlBookingAccount0204.setCrossAccountIBAN("DE00000000000000000005");
		xmlBookingAccount0204.setCrossAccountBIC("BANKDE00005");
		xmlBookingAccount0204.setCrossAccountNumber("00000005");
		xmlBookingAccount0204.setCrossBlz("100100005");
		xmlBookingAccount0204.setCrossBankName("Testbank 05");
		xmlBookingAccount0204.setCrossAccountNamePP(null);

		xmlBankAccount02.setBookings(Arrays.asList(xmlBookingAccount0203, xmlBookingAccount0204));

		fileImportBean.writeBookingsToDB(Arrays.asList(xmlBankAccount02));

		dbAccountList = dbController.getAllFull(BankAccount.class);
		bankAccount02 = dbAccountList.get(1);

		assertEquals(4, bankAccount02.getBookings().size());

		recipientsList = dbController.getAll(Recipient.class);
		assertEquals(5, recipientsList.size());

		Booking booking0203 = bankAccount02.getBookings().stream().filter(booking -> "Testbuchung 05".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals("DE00000000000000000003", booking0203.getRecipient().getIban());

		Booking booking0204 = bankAccount02.getBookings().stream().filter(booking -> "Testbuchung 06".equals(booking.getPurpose())).findAny().orElse(null);
		assertEquals("DE00000000000000000004", booking0204.getRecipient().getIban());
	}

	@Test
	void testImportDuplicateBookings_AreSkippedAndSummarized() {
		de.zft2.fp3xmlextract.data.BankAccount xmlBankAccount = createXmlBankAccount("Girokonto Doubletten", "DE00000000000000000999", "999", "BANKDE00999");
		de.zft2.fp3xmlextract.data.Booking initialBooking = createXmlBooking("14.10.2025", "14.10.2025", "Bereits da", BigDecimal.valueOf(42.50),
				"Girokonto Doubletten");
		xmlBankAccount.setBookings(List.of(initialBooking));

		fileImportBean.writeAccountsToDB(List.of(xmlBankAccount));
		fileImportBean.writeBookingsToDB(List.of(xmlBankAccount));

		FileImportBean secondImportBean = new FileImportBean(null);
		de.zft2.fp3xmlextract.data.BankAccount secondImportAccount = createXmlBankAccount("Girokonto Doubletten", "DE00000000000000000999", "999",
				"BANKDE00999");
		de.zft2.fp3xmlextract.data.Booking duplicateBooking = createXmlBooking("14.10.2025", "14.10.2025", "Bereits da", BigDecimal.valueOf(42.50),
				"Girokonto Doubletten");
		de.zft2.fp3xmlextract.data.Booking newBooking = createXmlBooking("15.10.2025", "15.10.2025", "Neu importiert", BigDecimal.valueOf(84.50),
				"Girokonto Doubletten");
		secondImportAccount.setBookings(List.of(duplicateBooking, newBooking));

		secondImportBean.writeAccountsToDB(List.of(secondImportAccount));
		secondImportBean.writeBookingsToDB(List.of(secondImportAccount));

		List<Booking> dbBookingList = dbController.getAllFull(Booking.class);
		assertEquals(2, dbBookingList.size());

		String summary = secondImportBean.getImportSummaryText();
		assertNotNull(summary);
		assertEquals(1, countOccurrences(summary, "Girokonto Doubletten"));
		assertTrue(summary.contains("bereits vorhandene Buchungen 1"));
		assertTrue(summary.contains("neu hinzugefügt 1"));
		assertTrue(summary.contains("übersprungen 1"));
		assertTrue(summary.contains("Gesamtzahl 2"));
	}

	@Test
	void testImportMinimalFile_Success() throws URISyntaxException {

		fileImportBean.importFile(verifyFileName("MinimalAccount.xml"));

		List<BankAccount> dbAccountList = dbController.getAllFull(BankAccount.class);
		assertEquals(0, dbAccountList.size());

		List<Booking> dbBookingList = dbController.getAllFull(Booking.class);
		assertEquals(0, dbBookingList.size());

	}

	@Test
	void testImportBigFile_Success() throws URISyntaxException {

		fileImportBean.importFile(verifyFileName("ExampleAccounts.xml"));

		List<BankAccount> dbAccountList = dbController.getAllFull(BankAccount.class);
		assertEquals(26, dbAccountList.size());

		List<Booking> dbBookingList = dbController.getAllFull(Booking.class);
		assertEquals(788, dbBookingList.size());

		assertEquals(107, dbBookingList.stream().filter(booking -> BookingType.REBOOKING_IN == booking.getBookingType()).count());
		assertEquals(133, dbBookingList.stream().filter(booking -> BookingType.REBOOKING_OUT == booking.getBookingType()).count());
		assertEquals(138, dbBookingList.stream().filter(booking -> BookingType.DEPOSIT == booking.getBookingType()).count());
		assertEquals(234, dbBookingList.stream().filter(booking -> BookingType.REMOVAL == booking.getBookingType()).count());
		assertEquals(165, dbBookingList.stream().filter(booking -> BookingType.INTEREST == booking.getBookingType()).count());
		assertEquals(3, dbBookingList.stream().filter(booking -> BookingType.INTEREST_CHARGE == booking.getBookingType()).count());
		assertEquals(8, dbBookingList.stream().filter(booking -> booking.getBookingType() == null).count());
	}

	@Disabled
	void testImportDefinedBookingsFile_Success() throws URISyntaxException {

		fileImportBean.importFile(verifyFileName("DefinedAccountsBookings.xml"));

		List<BankAccount> dbAccountList = dbController.getAllFull(BankAccount.class);
		assertEquals(26, dbAccountList.size());

		List<Booking> dbBookingList = dbController.getAllFull(Booking.class);
		assertEquals(788, dbBookingList.size());

	}

	private String verifyFileName(String inputfileName) throws URISyntaxException {
		URL resource = getClass().getClassLoader().getResource(inputfileName);
		assertNotNull(resource, "Test XML file " + inputfileName + " must be in src/test/resources");

		return Paths.get(resource.toURI()).toString();

	}

	private de.zft2.fp3xmlextract.data.BankAccount createXmlBankAccount(String accountName, String iban, String number, String bic) {
		de.zft2.fp3xmlextract.data.BankAccount bankAccount = new de.zft2.fp3xmlextract.data.BankAccount();
		bankAccount.setBezeichnung(accountName);
		bankAccount.setNamePP(accountName);
		bankAccount.setIban(iban);
		bankAccount.setNumber(number);
		bankAccount.setBic(bic);
		bankAccount.setType("Girokonto");
		return bankAccount;
	}

	private de.zft2.fp3xmlextract.data.Booking createXmlBooking(String dateBooking, String dateValue, String purpose, BigDecimal amount, String accountName) {
		return new de.zft2.fp3xmlextract.data.Booking(dateBooking, dateValue, purpose, amount, null, null, accountName);
	}

	private int countOccurrences(String text, String search) {
		return text.split(java.util.regex.Pattern.quote(search), -1).length - 1;
	}

}
