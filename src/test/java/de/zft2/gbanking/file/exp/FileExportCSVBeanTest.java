package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.file.BaseFileTest;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportCSVBeanTest extends BaseFileTest {

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
	void exportFileFromDatatbase_shouldWriteCsvHeaderAndBookingData() throws Exception {
		BankAccount account = createAccountWithBooking();
		Path exportFile = getExportFilePath("exports", "bookings.csv");

		boolean result = new FileExportCSVBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		assertTrue(result);
		assertTrue(Files.isRegularFile(exportFile));

		String csv = Files.readString(exportFile, StandardCharsets.UTF_8);
		assertTrue(csv.startsWith("KONTO_NAME;KONTO_IBAN;KONTO_NR;KONTO_BANK;KONTO_BIC"));
		assertTrue(csv.contains("CSV Zweck"));
		assertTrue(csv.contains("123,45"));
		assertTrue(csv.contains("CSV Empfaenger"));
		assertTrue(csv.contains("DE99999999999999999999"));
		assertTrue(csv.contains("Giro CSV"));
		assertTrue(csv.contains(account.getIban()));
		assertTrue(csv.contains(account.getNumber()));
		assertTrue(csv.contains("FREMDWAEHRUNGSBETRAG;FREMDWAEHRUNG;WECHSELKURS_ZUR_KONTOWAEHRUNG;GEBUEHR;GEBUEHRENWAEHRUNG"));
		assertTrue(csv.contains("100,00;USD;1.2345;1,25;EUR"));
	}

	@Test
	void exportFileFromDatatbase_shouldCreateEmptyCsvWithHeaderForAccountsWithoutBookings() throws Exception {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Leeres Konto");
		account = dbController.insertOrUpdate(account);
		Path exportFile = tempDir.resolve("empty.csv");

		boolean result = new FileExportCSVBean(null).exportFileFromDatatbase(List.of(account), exportFile.toString());

		List<String> lines = Files.readAllLines(exportFile, StandardCharsets.UTF_8);
		assertTrue(result);
		assertEquals(1, lines.size());
		assertFalse(lines.get(0).isBlank());
	}

	private BankAccount createAccountWithBooking() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName("Giro CSV");
		account.setAccountState(AccountState.ACTIVE);
		account = dbController.insertOrUpdate(account);

		Recipient recipient = new Recipient("CSV Empfaenger", "DE99999999999999999999", "TESTDEFFXXX", "99887766", "50010517", "Testbank",
				Source.MANUELL);
		recipient.setNote("CSV Notiz");
		recipient = dbController.insertOrUpdate(recipient);

		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(LocalDate.of(2026, Month.APRIL, 10));
		booking.setDateValue(LocalDate.of(2026, Month.APRIL, 11));
		booking.setPurpose("CSV Zweck");
		booking.setAmount(new BigDecimal("123.45"));
		booking.setRecipientId(recipient.getId());
		booking.setSource(Source.IMPORT);
		booking.setBookingType(BookingType.DEPOSIT);
		booking.setSepaCustomerRef("KREF");
		booking.setSepaEndToEnd("E2E");
		BookingForeignCurrencyDetails foreign = new BookingForeignCurrencyDetails();
		foreign.setForeignAmount(new BigDecimal("100.00"));
		foreign.setForeignCurrency(Currency.USD);
		foreign.setExchangeRateToBaseCurrency(new BigDecimal("1.2345"));
		booking.setForeignCurrencyDetails(foreign);
		BookingFee fee = new BookingFee();
		fee.setAmount(new BigDecimal("1.25"));
		fee.setCurrency(Currency.EUR);
		booking.setFee(fee);
		dbController.insertOrUpdate(booking);

		return account;
	}
}
