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
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import de.zft2.gbanking.db.dao.BookingCreditCardDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileImportCsvCreditcardTest {

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
	void importFileToDatabase_shouldDetectDetailedFormatAndPersistCreditcardDetails() throws Exception {
		BankAccount account = createCreditcardAccount("Kreditkarte Urlaub");
		Path csvFile = writeCsv("""
				\uFEFFTransactionDate;Text;Type;Currency Amount;Currency Rate;Currency;Amount;Merchant Area;Merchant Category;BookDate;ValueDate
				14.08.2022;ORLEN STACJA NR 790;Einkauf;-182,52;0,214880561;PLN;-39,22;SZCZECIN;Service Stations;15.08.2022;01.09.2022
				""");

		FileImportCSVBean importer = new FileImportCSVBean(null, account);
		importer.importFileToDatabase(csvFile.toString());

		List<Booking> bookings = dbController.getAllByParentFull(Booking.class, account.getId());
		assertEquals(1, bookings.size());
		Booking booking = bookings.get(0);
		assertEquals(new BigDecimal("-39.22"), booking.getAmount());
		assertEquals("EUR", booking.getCurrency());
		assertEquals("ORLEN STACJA NR 790", booking.getPurpose());
		assertEquals(LocalDate.of(2022, Month.AUGUST, 15), booking.getDateBooking());
		assertEquals(LocalDate.of(2022, Month.SEPTEMBER, 1), booking.getDateValue());
		BookingCreditCardDetails details = booking.getCreditCardDetails();
		assertNotNull(details);
		assertEquals(LocalDate.of(2022, Month.AUGUST, 14), details.getTransactionDate());
		assertEquals("Einkauf", details.getType());
		assertEquals(0, new BigDecimal("-182.52").compareTo(details.getCurrencyAmount()));
		assertEquals(0, new BigDecimal("0.214880561").compareTo(details.getCurrencyRate()));
		assertEquals("PLN", details.getCurrency());
		assertEquals("SZCZECIN", details.getMerchantArea());
		assertEquals("Service Stations", details.getMerchantCategory());
		assertEquals(List.of(), importer.getRejectedRows());
	}

	@Test
	void importFileToDatabase_shouldDetectSimpleFormatAndRejectRowsWithInvalidAmountColumns() throws Exception {
		BankAccount account = createCreditcardAccount("Kreditkarte Alltag");
		Path csvFile = writeCsv("""
				Datum;Beschreibung;Wertstellungsdatum;Aus dem Konto;Auf das Konto
				01.05.2026;Kartenrabatt;01.05.2026;;15
				01.05.2026;Shell Tankstelle 14007400, Kronberg Im T, DE - Kartenzahlung;01.05.2026;150;
				02.05.2026;Doppelt;02.05.2026;10;20
				03.05.2026;Ohne Betrag;03.05.2026;;
				""");

		FileImportCSVBean importer = new FileImportCSVBean(null, account);
		importer.importFileToDatabase(csvFile.toString());

		Map<String, Booking> bookingsByPurpose = dbController.getAllByParentFull(Booking.class, account.getId()).stream()
				.collect(Collectors.toMap(booking -> booking.getPurpose(), booking -> booking));
		assertEquals(2, bookingsByPurpose.size());
		assertEquals(new BigDecimal("15.00"), bookingsByPurpose.get("Kartenrabatt").getAmount());
		assertEquals(new BigDecimal("-150.00"),
				bookingsByPurpose.get("Shell Tankstelle 14007400, Kronberg Im T, DE - Kartenzahlung").getAmount());
		assertEquals("EUR", bookingsByPurpose.get("Kartenrabatt").getCurrency());
		assertNull(bookingsByPurpose.get("Kartenrabatt").getCreditCardDetails());
		assertEquals(List.of(4L, 5L), importer.getRejectedRows().stream().map(row -> row.lineNumber()).toList());
	}

	@Test
	void importFileToDatabase_shouldAssignPreferredExistingRecipientWithoutCreatingRecipients() throws Exception {
		BankAccount account = createCreditcardAccount("Kreditkarte Empfänger");
		String defaultIban = "DE11111111111111111111";
		String latestIban = "DE22222222222222222222";
		Recipient defaultRecipient = createReferencedRecipient(account, "Default", defaultIban, LocalDate.of(2024, Month.JANUARY, 1), true);
		createReferencedRecipient(account, "Später verwendet", defaultIban, LocalDate.of(2025, Month.JANUARY, 1), false);
		createReferencedRecipient(account, "Früher verwendet", latestIban, LocalDate.of(2023, Month.JANUARY, 1), false);
		Recipient latestRecipient = createReferencedRecipient(account, "Zuletzt verwendet", latestIban, LocalDate.of(2025, Month.JANUARY, 1), false);
		int recipientCountBeforeImport = dbController.getAll(Recipient.class).size();
		Path csvFile = writeCsv("""
				Datum;Beschreibung;Wertstellungsdatum;Aus dem Konto;Auf das Konto
				01.05.2026;Bezahlung von DE11111111111111111111 Kreditkartenabrechnung;01.05.2026;100;
				02.05.2026;Bezahlung von de22222222222222222222 Kreditkartenabrechnung;02.05.2026;200;
				03.05.2026;Bezahlung von DE33333333333333333333 Kreditkartenabrechnung;03.05.2026;300;
				""");

		FileImportCSVBean importer = new FileImportCSVBean(null, account);
		importer.importFileToDatabase(csvFile.toString());

		Map<String, Booking> bookingsByPurpose = dbController.getAllByParentFull(Booking.class, account.getId()).stream()
				.filter(booking -> booking.getPurpose().startsWith("Bezahlung von"))
				.collect(Collectors.toMap(Booking::getPurpose, booking -> booking));
		Booking defaultBooking = bookingsByPurpose.get("Bezahlung von DE11111111111111111111 Kreditkartenabrechnung");
		Booking latestBooking = bookingsByPurpose.get("Bezahlung von de22222222222222222222 Kreditkartenabrechnung");
		Booking unmatchedBooking = bookingsByPurpose.get("Bezahlung von DE33333333333333333333 Kreditkartenabrechnung");
		assertEquals(defaultRecipient.getId(), defaultBooking.getRecipientId());
		assertEquals(latestRecipient.getId(), latestBooking.getRecipientId());
		assertNull(unmatchedBooking.getRecipient());
		assertEquals(recipientCountBeforeImport, dbController.getAll(Recipient.class).size());
	}

	@Test
	void importFileToDatabase_shouldRejectUnknownHeaderFormat() throws Exception {
		BankAccount account = createCreditcardAccount("Kreditkarte Unbekannt");
		Path csvFile = writeCsv("Foo;Bar" + System.lineSeparator() + "1;2" + System.lineSeparator());

		FileImportCSVBean importer = new FileImportCSVBean(null, account);
		String fileName = csvFile.toString();

		assertThrows(GBankingException.class, () -> importer.importFileToDatabase(fileName));
	}

	private BankAccount createCreditcardAccount(String accountName) {
		BankAccount account = TestData.createSampleAccount(null);
		account.setAccountName(accountName);
		account.setAccountType(AccountType.CREDIT_CARD);
		account.setOfflineAccount(true);
		return dbController.insertOrUpdate(account);
	}

	private Recipient createReferencedRecipient(BankAccount account, String name, String iban, LocalDate usageDate,
			boolean defaultRecipient) {
		Recipient recipient = new Recipient(name, iban, null, null, null, null, Source.MANUELL);
		recipient.setDefault(defaultRecipient);
		recipient = dbController.insertOrUpdate(recipient);
		Booking booking = TestData.createBookingWithParams(account.getId(), recipient.getId(), "Verwendung " + name, -1,
				BookingType.REMOVAL);
		booking.setDateBooking(usageDate);
		booking.setDateValue(usageDate);
		dbController.insertOrUpdate(booking);
		return recipient;
	}

	private Path writeCsv(String content) throws Exception {
		Path csvFile = tempDir.resolve("creditcard-import-" + System.nanoTime() + ".csv");
		Files.writeString(csvFile, content.stripIndent(), StandardCharsets.UTF_8);
		return csvFile;
	}
}
