package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportCSVBeanTest {

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
	void importFileToDatabase_shouldImportBookingAndSkipDuplicateOnSecondImport() throws Exception {
		BankAccount account = createAccount("CSV Konto", "DE11111111111111111111", "11111111");
		Path csvFile = writeCsv("""
				KONTO_NAME;KONTO_IBAN;KONTO_NR;KONTO_BANK;KONTO_BIC;BUCHUNGSDATUM;WERTSTELLUNG;BETRAG;WAEHRUNG;VERWENDUNGSZWECK;EMPFAENGER_NAME;EMPFAENGER_IBAN;EMPFAENGER_BIC;EMPFAENGER_KONTONR;EMPFAENGER_BLZ;EMPFAENGER_BANK;BUCHUNGSTYP;KATEGORIE;SEPA_CUSTOMER_REF;SEPA_CREDITOR_ID;SEPA_END_TO_END;SEPA_MANDATE;SEPA_PERSON_ID;SEPA_PURPOSE;SEPA_TYPE
				CSV Konto;DE11111111111111111111;11111111;Testbank;TESTDEFF;10.04.26;11.04.26;123,45;EUR;CSV Zweck;CSV Empfaenger;DE99999999999999999999;TESTDEFFXXX;99887766;50010517;Empfaengerbank;DEPOSIT;Sonstiges;KREF;;E2E;;;;
				""");

		FileImportCSVBean firstImport = new FileImportCSVBean(null);
		firstImport.importFileToDatabase(csvFile.toString());

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		Booking importedBooking = bookings.get(0);
		assertEquals(new BigDecimal("123.45"), importedBooking.getAmount());
		assertEquals(Source.IMPORT, importedBooking.getSource());
		assertEquals(BookingType.DEPOSIT, importedBooking.getBookingType());
		assertEquals("Sonstiges", importedBooking.getCategory().getFullName());
		assertNotNull(importedBooking.getRecipient());
		assertEquals("CSV Empfaenger", importedBooking.getRecipient().getName());

		FileImportCSVBean secondImport = new FileImportCSVBean(null);
		secondImport.importFileToDatabase(csvFile.toString());

		assertEquals(1, dbController.getAllByParentFull(Booking.class, account.getId()).size());
		FileImportBean.ImportAccountStatistics statistics = secondImport.getImportStatistics().get(0);
		assertEquals(1, statistics.getExistingBookings());
		assertEquals(0, statistics.getAddedBookings());
		assertEquals(1, statistics.getSkippedBookings());
	}

	@Test
	void importFileToDatabase_shouldCreateMissingAccountFromCsvAccountData() throws Exception {
		Path csvFile = writeCsv("""
				KONTO_NAME;KONTO_IBAN;KONTO_NR;BUCHUNGSDATUM;BETRAG;VERWENDUNGSZWECK
				Neues CSV Konto;DE22222222222222222222;22222222;10.04.2026;-12.50;Neue Buchung
				""");

		new FileImportCSVBean(null).importFileToDatabase(csvFile.toString());

		List<BankAccount> accounts = dbController.getAll(BankAccount.class);
		assertEquals(1, accounts.size());
		assertEquals("Neues CSV Konto", accounts.get(0).getAccountName());
		assertEquals(1, dbController.getAllByParentFull(Booking.class, accounts.get(0).getId()).size());
	}

	@Test
	void importFileToDatabase_shouldNotSetOwnAccountAsCrossAccountFromRecipient() throws Exception {
		BankAccount account = createAccount("CSV Eigenkonto", "DE55555555555555555555", "55555555");
		Path csvFile = writeCsv("""
				KONTO_NAME;KONTO_IBAN;KONTO_NR;BUCHUNGSDATUM;BETRAG;VERWENDUNGSZWECK;EMPFAENGER_NAME;EMPFAENGER_IBAN;EMPFAENGER_KONTONR
				CSV Eigenkonto;DE55555555555555555555;55555555;10.04.2026;-12.50;Self Recipient;CSV Eigenkonto;DE55555555555555555555;55555555
				""");

		new FileImportCSVBean(null).importFileToDatabase(csvFile.toString());

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		Booking importedBooking = bookings.get(0);
		assertNotNull(importedBooking.getRecipient());
		assertEquals(account.getIban(), importedBooking.getRecipient().getIban());
		assertNull(importedBooking.getCrossAccountId());
		assertNull(importedBooking.getCrossBookingId());
		assertEquals(BookingType.REMOVAL, importedBooking.getBookingType());
	}

	@Test
	void importFileToDatabase_shouldPreferFileBankNamesRegardlessOfRowOrder() throws Exception {
		BankAccount account = createAccount("CSV Konto", "DE11111111111111111111", "11111111");
		insertInstitute("50010517", "Lookup Bank", "LOOKDEFFXXX");
		Path csvFile = writeCsv("""
				KONTO_NAME;KONTO_IBAN;KONTO_NR;BUCHUNGSDATUM;BETRAG;VERWENDUNGSZWECK;EMPFAENGER_NAME;EMPFAENGER_IBAN;EMPFAENGER_BIC;EMPFAENGER_BLZ;EMPFAENGER_BANK
				CSV Konto;DE11111111111111111111;11111111;13.04.2026;-4.00;Mit Bank B;CSV Empfaenger;DE44500105175407324931;LOOKDEFFXXX;50010517;Bank aus Datei B
				CSV Konto;DE11111111111111111111;11111111;10.04.2026;-1.00;Ohne Bank zuerst;CSV Empfaenger;DE44500105175407324931;LOOKDEFFXXX;50010517;
				CSV Konto;DE11111111111111111111;11111111;14.04.2026;-5.00;Ohne Bank zuletzt;CSV Empfaenger;DE44500105175407324931;LOOKDEFFXXX;50010517;
				CSV Konto;DE11111111111111111111;11111111;11.04.2026;-2.00;Mit Bank A;CSV Empfaenger;DE44500105175407324931;LOOKDEFFXXX;50010517;Bank aus Datei A
				CSV Konto;DE11111111111111111111;11111111;12.04.2026;-3.00;Ohne Bank mittig;CSV Empfaenger;DE44500105175407324931;LOOKDEFFXXX;50010517;
				""");

		new FileImportCSVBean(null).importFileToDatabase(csvFile.toString());

		List<Recipient> recipients = dbController.getAll(Recipient.class);
		assertEquals(List.of("Bank aus Datei A", "Bank aus Datei B"), recipients.stream().map(recipient -> recipient.getBank()).sorted().toList());
		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(5, bookings.size());
		assertEquals(List.of("Bank aus Datei A", "Bank aus Datei B"),
				bookings.stream().map(booking -> booking.getRecipient().getBank()).distinct().sorted().toList());
		assertEquals(3, bookings.stream().filter(booking -> "Bank aus Datei A".equals(booking.getRecipient().getBank())).count());
		assertEquals(2, bookings.stream().filter(booking -> "Bank aus Datei B".equals(booking.getRecipient().getBank())).count());
	}

	@Test
	void importFileToDatabase_shouldRejectMismatchingContextAccount() throws Exception {
		BankAccount selectedAccount = createAccount("Ausgewaehlt", "DE33333333333333333333", "33333333");
		createAccount("Andere Bank", "DE44444444444444444444", "44444444");
		Path csvFile = writeCsv("""
				KONTO_NAME;KONTO_IBAN;KONTO_NR;BUCHUNGSDATUM;BETRAG;VERWENDUNGSZWECK
				Andere Bank;DE44444444444444444444;44444444;10.04.2026;1.00;Falsches Konto
				""");

		FileImportCSVBean importer = new FileImportCSVBean(null, selectedAccount);

		String csvFileName = csvFile.toString();
		assertThrows(GBankingException.class, () -> importer.importFileToDatabase(csvFileName));
	}

	private BankAccount createAccount(String name, String iban, String number) {
		BankAccount account = TestData.createSampleAccount(null);
		account.setAccountName(name);
		account.setIban(iban);
		account.setNumber(number);
		return dbController.insertOrUpdate(account);
	}

	private void insertInstitute(String blz, String bankName, String bic) {
		int importHistoryId = dbController.insertOrUpdate(new ImportHistory("recipient-bank-lookup.csv")).getId();
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBankName(bankName);
		institute.setBic(bic);
		institute.setImportNumber(1);
		institute.setLastChanged(LocalDate.of(2026, 4, 10));
		institute.setImportFile(importHistoryId);
		institute.setStateType(InstituteStatus.ACTIVE);
		dbController.insertOrUpdate(institute);
	}

	private Path writeCsv(String content) throws Exception {
		Path csvFile = tempDir.resolve("import-" + System.nanoTime() + ".csv");
		Files.writeString(csvFile, content.stripIndent(), StandardCharsets.UTF_8);
		return csvFile;
	}
}
