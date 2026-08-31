package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.fp3xmlextract.convert.Converter;
import de.zft2.fp3xmlextract.data.Fp3XmlBankAccount;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportXMLBeanTest {

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
	void fp3XmlExtract_shouldExposeAccountAndBookingCurrency() throws Exception {
		Fp3XmlBankAccount account = new Converter().convertXmlToCsvEntries(testResource("dummy_import.xml").toString())
				.iterator().next();

		assertEquals("EUR", account.getBaseCurrency());
		assertEquals("EUR", account.getBookings().get(0).getCurrency());
	}

	@Test
	void importFileToDatabase_shouldImportXmlAccountBookingRecipientAndCategory() throws Exception {
		new FileImportBean(null).importFileToDatatbase(testResource("dummy_import.xml").toString());

		List<BankAccount> accounts = dbController.getAll(BankAccount.class);
		assertEquals(1, accounts.size());
		BankAccount account = accounts.get(0);
		assertEquals("Testkonto", account.getAccountName());
		assertEquals(AccountType.CURRENT_ACCOUNT, account.getAccountType());
		assertEquals("DE56600160020008290050", account.getIban());
		assertEquals("JTBPDEFFXXX", account.getBic());
		assertEquals(new BigDecimal("0.46"), account.getBalance());

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		Booking booking = bookings.get(0);
		assertEquals("Testbuchung", booking.getPurpose());
		assertEquals(new BigDecimal("123.45"), booking.getAmount());
		assertEquals(Source.IMPORT_INITIAL, booking.getSource());
		assertEquals(BookingType.DEPOSIT, booking.getBookingType());
		assertNotNull(booking.getRecipient());
		assertEquals("Max Mustermann", booking.getRecipient().getName());
		assertEquals("DE12345678901234567890", booking.getRecipient().getIban());
		assertNotNull(booking.getCategory());
		assertEquals("Sonstiges", booking.getCategory().getFullName());
	}

	@Test
	void importFileToDatabase_shouldSkipDuplicateXmlBookingsOnSecondImport() throws Exception {
		Path xmlFile = testResource("dummy_import.xml");

		new FileImportBean(null).importFileToDatatbase(xmlFile.toString());
		FileImportBean secondImport = new FileImportBean(null);
		secondImport.importFileToDatatbase(xmlFile.toString());

		BankAccount account = dbController.getAll(BankAccount.class).get(0);
		assertEquals(1, dbController.getAllByParentFull(Booking.class, account.getId()).size());
		FileImportBean.ImportAccountStatistics statistics = secondImport.getImportStatistics().get(0);
		assertEquals(1, statistics.getExistingBookings());
		assertEquals(0, statistics.getAddedBookings());
		assertEquals(1, statistics.getSkippedBookings());
	}

	@Test
	void fileImportTask_shouldRouteFp3XmlFileThroughFp3XmlExtract() throws Exception {
		Path fp3File = testResource("fp100.xml");
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Vertragssparen - 2000510044");
		account.setIban("DE09120300002000510044");
		account.setNumber("2000510044");
		account = dbController.insertOrUpdate(account);

		FileImportTask task = new FileImportTask(fp3File.toString(), ExportType.BOOKINGS_FP3, account);
		JavaFxTestSupport.callFx(task::call);

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(18, bookings.size());
	}

	@Test
	void importFileToDatabase_shouldImportExternalCancellationXmlWithCancelAndRebookingTypes() throws Exception {
		Path xmlFile = fp3XmlExtractTestData("konto_umbuchung_same_day_AND_cancellation_test01.xml");
		assumeTrue(Files.exists(xmlFile), () -> "Missing fp3xmlextract test file: " + xmlFile);

		new FileImportBean(null).importFileToDatatbase(xmlFile.toString());

		BankAccount baseAccount = findAccount("Kontokorrent - 200174051");
		BankAccount targetAccount = findAccount("Kontokorrent - 4751027");
		List<Booking> baseBookings = dbController.getAllByParentFull(Booking.class, baseAccount.getId());
		List<Booking> targetBookings = dbController.getAllByParentFull(Booking.class, targetAccount.getId());
		assertEquals(3, baseBookings.size());
		assertEquals(1, targetBookings.size());

		Booking originalCancelledBooking = findBookingContaining(baseBookings, "V00001");
		assertEquals(new BigDecimal("-900.00"), originalCancelledBooking.getAmount());
		assertEquals(BookingType.CANCEL, originalCancelledBooking.getBookingType());
		assertNull(originalCancelledBooking.getCrossBookingId());

		Booking cancellationBooking = findBookingContaining(baseBookings, "Retoure SEPA Ueberweisung");
		assertEquals(new BigDecimal("900.00"), cancellationBooking.getAmount());
		assertEquals(BookingType.CANCEL, cancellationBooking.getBookingType());
		assertNull(cancellationBooking.getCrossBookingId());

		Booking retryBooking = findBookingContaining(baseBookings, "V00002");
		Booking targetBooking = targetBookings.get(0);
		assertEquals(BookingType.REBOOKING_OUT, retryBooking.getBookingType());
		assertEquals(BookingType.REBOOKING_IN, targetBooking.getBookingType());
		assertNotNull(retryBooking.getCrossBookingId());
		assertNotNull(targetBooking.getCrossBookingId());
		assertEquals(targetBooking.getId(), retryBooking.getCrossBookingId());
		assertEquals(retryBooking.getId(), targetBooking.getCrossBookingId());
	}

	private Path testResource(String fileName) throws Exception {
		URL resource = getClass().getClassLoader().getResource(fileName);
		assertNotNull(resource, "Test XML file " + fileName + " must be available.");
		return Path.of(resource.toURI());
	}

	private Path fp3XmlExtractTestData(String fileName) {
		List<Path> candidates = List.of(
				Path.of("..", "..", "fp3xmlextract", "src", "test", "resources", "testdata", fileName),
				Path.of("..", "..", "..", "..", "fp3xmlextract", "src", "test", "resources", "testdata", fileName));
		return candidates.stream()
				.map(path -> path.toAbsolutePath().normalize())
				.filter(Files::exists)
				.findFirst()
				.orElseGet(() -> candidates.get(0).toAbsolutePath().normalize());
	}

	private BankAccount findAccount(String accountName) {
		return dbController.getAll(BankAccount.class).stream()
				.filter(account -> accountName.equals(account.getAccountName()))
				.findFirst()
				.orElseThrow();
	}

	private Booking findBookingContaining(List<Booking> bookings, String purposePart) {
		return bookings.stream()
				.filter(booking -> booking.getPurpose() != null && booking.getPurpose().contains(purposePart))
				.findFirst()
				.orElseThrow();
	}
}
