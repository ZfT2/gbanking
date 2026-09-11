package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.fp3xmlextract.convert.Converter;
import de.zft2.fp3xmlextract.data.Fp3XmlBankAccount;
import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
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
		InstituteLookupCache.clear();
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		InstituteLookupCache.clear();
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
		insertLookupBank("DK Bank", 1);
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
		assertEquals("TestBank", booking.getRecipient().getBank(), "An existing imported bank name must not be replaced");
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
	void importFileToDatabase_shouldKeepExistingOnlineBookingInsteadOfImportDuplicate() throws Exception {
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("50015001"));
		BankAccount onlineAccount = TestDataFactory.createSampleAccount(bankAccess.getId());
		onlineAccount.setAccountName("Testkonto");
		onlineAccount.setIban("DE56600160020008290050");
		onlineAccount.setNumber("8290050");
		onlineAccount = dbController.insertOrUpdate(onlineAccount);

		Booking onlineBooking = new Booking();
		onlineBooking.setAccountId(onlineAccount.getId());
		onlineBooking.setDateBooking(java.time.LocalDate.of(2025, 1, 1));
		onlineBooking.setPurpose("Testbuchung");
		onlineBooking.setAmount(new BigDecimal("123.45"));
		onlineBooking.setBookingType(BookingType.DEPOSIT);
		onlineBooking.setSource(Source.ONLINE);
		onlineBooking = dbController.insertOrUpdate(onlineBooking);

		String xml = Files.readString(testResource("dummy_import.xml")).replace("Testbuchung</ZWECK>", "Testbuchung  V00010</ZWECK>");
		Path importFile = tempDir.resolve("online-duplicate.xml");
		Files.writeString(importFile, xml);
		new FileImportBean(null).importFileToDatatbase(importFile.toString());

		BankAccount persistedAccount = dbController.getById(BankAccount.class, onlineAccount.getId());
		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, onlineAccount.getId());
		assertEquals(bankAccess.getId(), persistedAccount.getBankAccessId());
		assertEquals(1, bookings.size());
		assertEquals(onlineBooking.getId(), bookings.get(0).getId());
		assertEquals(Source.ONLINE, bookings.get(0).getSource());
		assertEquals("Testbuchung", bookings.get(0).getPurpose());
	}

	@Test
	void importFileToDatabase_shouldCompleteMissingBankNameFromPreferredViewSource() throws Exception {
		insertLookupBank("DBB Bank", 2);
		insertLookupBank("EPC Bank", 3);
		insertLookupBank("DK Bank", 1);
		assertImportedBankName("DK Bank", false);
	}

	@Test
	void importFileToDatabase_shouldCompleteMissingBankNameFromBicOnlySource() throws Exception {
		insertLookupBank("EPC Bank", 3);
		assertImportedBankName("EPC Bank", true);
	}

	@Test
	void importFileToDatabase_shouldPersistSelectedBankNameCorrection() throws Exception {
		insertValidationData();

		Booking booking = importDummyXml(findings -> {
			assertEquals("Testkonto", findings.get(0).accountName());
			return Map.of(findings.get(0).bookingId(), "Expected Bank");
		});

		assertEquals("Expected Bank", booking.getRecipient().getBank());
	}

	@Test
	void importFileToDatabase_shouldKeepImportedBankNameWhenCorrectionIsSkipped() throws Exception {
		insertValidationData();

		Booking booking = importDummyXml(findings -> Map.of());

		assertEquals("TestBank", booking.getRecipient().getBank());
	}

	@Test
	void importFileToDatabase_shouldRollbackIfBankNameValidationIsInterrupted() {
		insertValidationData();
		FileImportBean importBean = new FileImportBean(null, null, false, findings -> {
			throw new IllegalStateException("Simulated validation interruption");
		});

		assertThrows(RuntimeException.class, () -> importBean.importFileToDatatbase(testResource("dummy_import.xml").toString()));

		assertOnlyValidationDataRemains();
	}

	@Test
	void fileImportTask_shouldCancelAndRollbackIfBankNameValidationIsCancelled() throws Exception {
		insertValidationData();
		FileImportTask task = new FileImportTask(testResource("dummy_import.xml").toString(), ExportType.BOOKINGS_XML,
				null, null, findings -> {
					throw new CancellationException("Simulated user cancellation");
				});

		JavaFxTestSupport.callFx(task::call);

		assertTrue(task.isCancelled());
		assertOnlyValidationDataRemains();
	}

	private void assertOnlyValidationDataRemains() {
		assertEquals(1, dbController.getAll(BankAccount.class).size());
		assertEquals(1, dbController.getAll(Booking.class).size());
		assertEquals(1, dbController.getAll(Recipient.class).size());
	}

	private Booking importDummyXml(ImportedBankNameCorrectionHandler correctionHandler) throws Exception {
		new FileImportBean(null, null, false, correctionHandler).importFileToDatatbase(testResource("dummy_import.xml").toString());
		BankAccount account = dbController.getAll(BankAccount.class).stream()
				.filter(candidate -> "Testkonto".equals(candidate.getAccountName())).findFirst().orElseThrow();
		return dbController.getAllByParentFull(Booking.class, account.getId()).get(0);
	}

	private void insertValidationData() {
		insertDkLookupBank("12345678", "GENODEF1XXX", "Expected Bank", 1);
		insertDkLookupBank("87654321", "TESTDEFFXXX", "TestBank", 2);
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Validation evidence");
		account = dbController.insertOrUpdate(account);
		Recipient recipient = dbController.insertOrUpdate(new Recipient("Known recipient", "DE00123456789012345678", "GENODEF1XXX",
				"1234567890", "12345678", "Expected Bank", Source.IMPORT));
		Booking booking = TestDataFactory.createSampleBookingWithRecipient(account.getId(), recipient.getId());
		booking.setSource(Source.IMPORT);
		dbController.insertOrUpdate(booking);
	}

	private void assertImportedBankName(String bankName, boolean removeBlz) throws Exception {
		String xml = Files.readString(testResource("dummy_import.xml")).replace("<BANKNAME>TestBank</BANKNAME>", "<BANKNAME/>");
		if (removeBlz) {
			xml = xml.replace("<BLZ>12345678</BLZ>", "<BLZ/>");
		}
		Path importFile = tempDir.resolve("missing-bank-name.xml");
		Files.writeString(importFile, xml);
		new FileImportBean(null).importFileToDatatbase(importFile.toString());

		BankAccount account = dbController.getAll(BankAccount.class).get(0);
		Booking booking = dbController.getAllByParentFull(Booking.class, account.getId()).get(0);
		assertEquals(bankName, booking.getRecipient().getBank());
	}

	private void insertLookupBank(String bankName, int sourcePriority) {
		Institute bank = new Institute();
		bank.setBlz(sourcePriority == 3 ? null : "12345678");
		bank.setBic("GENODEF1XXX");
		bank.setBankName(bankName);
		bank.setStateType(InstituteStatus.ACTIVE);
		bank.setImportFile(dbController.insertOrUpdate(new ImportHistory("lookup.csv")).getId());
		switch (sourcePriority) {
		case 1 -> bank.setImportNumber(42);
		case 2 -> bank.setDatasetNumber("000001");
		case 3 -> bank.setCountry("Germany");
		default -> throw new IllegalArgumentException("Unknown source priority: " + sourcePriority);
		}
		dbController.insertOrUpdate(bank);
	}

	private void insertDkLookupBank(String blz, String bic, String bankName, int importNumber) {
		Institute bank = new Institute();
		bank.setBlz(blz);
		bank.setBic(bic);
		bank.setBankName(bankName);
		bank.setStateType(InstituteStatus.ACTIVE);
		bank.setImportFile(dbController.insertOrUpdate(new ImportHistory("validation.csv")).getId());
		bank.setImportNumber(importNumber);
		dbController.insertOrUpdate(bank);
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
