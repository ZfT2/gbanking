package de.zft2.gbanking.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.ServiceRegistry;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingSplitServiceTest {

	private DBController dbController;
	private BookingSplitService service;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
		service = ServiceRegistry.getService(BookingSplitService.class);
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
	void shouldSaveSplitBookingsAndCreateCounterBookingOnOfflineAccount() {
		BankAccount onlineAccount = insertAccount("Onlinekonto", false);
		BankAccount offlineAccount = insertAccount("Offlinekonto", true);
		Booking parentBooking = insertParentBooking(onlineAccount, new BigDecimal("-100.00"));

		Booking splitWithoutCounterAccount = createSplitBooking(new BigDecimal("-25.00"), null);
		Booking splitWithCounterAccount = createSplitBooking(new BigDecimal("-75.00"), offlineAccount.getId());

		List<Booking> savedSplitBookings = service.saveSplitBookings(parentBooking, List.of(splitWithoutCounterAccount, splitWithCounterAccount), List.of());

		assertEquals(2, savedSplitBookings.size());
		assertEquals(1, dbController.getAllByParentFull(Booking.class, onlineAccount.getId()).size());

		Booking savedSplitWithCounterAccount = savedSplitBookings.stream()
				.filter(booking -> Integer.valueOf(offlineAccount.getId()).equals(booking.getCrossAccountId()))
				.findFirst()
				.orElseThrow();
		assertEquals(parentBooking.getId(), savedSplitWithCounterAccount.getParentBookingId());
		assertEquals(Source.MANUELL, savedSplitWithCounterAccount.getSource());
		assertEquals(BookingType.REBOOKING_OUT, savedSplitWithCounterAccount.getBookingType());
		assertNotNull(savedSplitWithCounterAccount.getCrossBookingId());

		Booking counterBooking = dbController.getById(Booking.class, savedSplitWithCounterAccount.getCrossBookingId());
		assertNotNull(counterBooking);
		assertEquals(offlineAccount.getId(), counterBooking.getAccountId());
		assertNull(counterBooking.getParentBookingId());
		assertEquals(parentBooking.getAccountId(), counterBooking.getCrossAccountId());
		assertEquals(savedSplitWithCounterAccount.getId(), counterBooking.getCrossBookingId());
		assertEquals(0, new BigDecimal("75.00").compareTo(counterBooking.getAmount()));
		assertEquals(BookingType.REBOOKING_IN, counterBooking.getBookingType());

		List<Booking> offlineBookings = dbController.getAllByParentFull(Booking.class, offlineAccount.getId());
		assertEquals(1, offlineBookings.size());
		assertEquals(counterBooking.getId(), offlineBookings.get(0).getId());
	}

	@Test
	void shouldDeleteCounterBookingWhenSplitBookingIsDeleted() {
		BankAccount onlineAccount = insertAccount("Onlinekonto", false);
		BankAccount offlineAccount = insertAccount("Offlinekonto", true);
		Booking parentBooking = insertParentBooking(onlineAccount, new BigDecimal("-50.00"));
		List<Booking> savedSplitBookings = service.saveSplitBookings(parentBooking,
				List.of(createSplitBooking(new BigDecimal("-50.00"), offlineAccount.getId())), List.of());
		Booking splitBooking = savedSplitBookings.get(0);
		Integer counterBookingId = splitBooking.getCrossBookingId();

		service.saveSplitBookings(parentBooking, List.of(), List.of(splitBooking.getId()));

		assertTrue(service.getSplitBookings(parentBooking).isEmpty());
		assertNull(dbController.getById(Booking.class, counterBookingId));
		assertTrue(dbController.getAllByParentFull(Booking.class, offlineAccount.getId()).isEmpty());
	}

	@Test
	void shouldKeepCounterBookingAsRegularBookingWhenSplitBookingIsDeleted() {
		BankAccount onlineAccount = insertAccount("Onlinekonto", false);
		BankAccount offlineAccount = insertAccount("Offlinekonto", true);
		Booking parentBooking = insertParentBooking(onlineAccount, new BigDecimal("-60.00"));
		List<Booking> savedSplitBookings = service.saveSplitBookings(parentBooking,
				List.of(createSplitBooking(new BigDecimal("-60.00"), offlineAccount.getId())), List.of());
		Booking splitBooking = savedSplitBookings.get(0);
		Integer counterBookingId = splitBooking.getCrossBookingId();

		service.saveSplitBookings(parentBooking, List.of(), Map.of(splitBooking.getId(), false));

		assertTrue(service.getSplitBookings(parentBooking).isEmpty());
		Booking counterBooking = dbController.getById(Booking.class, counterBookingId);
		assertNotNull(counterBooking);
		assertNull(counterBooking.getCrossAccountId());
		assertNull(counterBooking.getCrossBookingId());
		assertEquals(BookingType.DEPOSIT, counterBooking.getBookingType());
		assertEquals(0, new BigDecimal("60.00").compareTo(counterBooking.getAmount()));
	}

	@Test
	void shouldDeleteSplitAndCounterBookingsWhenParentBookingIsDeleted() {
		BankAccount onlineAccount = insertAccount("Onlinekonto", false);
		BankAccount offlineAccount = insertAccount("Offlinekonto", true);
		Booking parentBooking = insertParentBooking(onlineAccount, new BigDecimal("-80.00"));
		List<Booking> savedSplitBookings = service.saveSplitBookings(parentBooking,
				List.of(createSplitBooking(new BigDecimal("-80.00"), offlineAccount.getId())), List.of());

		assertTrue(service.deleteBookingWithSplits(parentBooking));

		assertNull(dbController.getById(Booking.class, parentBooking.getId()));
		assertTrue(service.getSplitBookings(parentBooking).isEmpty());
		assertNull(dbController.getById(Booking.class, savedSplitBookings.get(0).getCrossBookingId()));
		assertTrue(dbController.getAllByParentFull(Booking.class, offlineAccount.getId()).isEmpty());
	}

	@Test
	void shouldRejectCounterBookingForForeignCurrencyParent() {
		BankAccount sourceAccount = insertAccount("Onlinekonto", false);
		BankAccount targetAccount = insertAccount("Offlinekonto", true);
		Booking parentBooking = insertParentBooking(sourceAccount, new BigDecimal("-80.00"));
		BookingForeignCurrencyDetails foreign = new BookingForeignCurrencyDetails();
		foreign.setForeignAmount(new BigDecimal("-100.00"));
		foreign.setForeignCurrency(Currency.USD);
		foreign.setExchangeRateToBaseCurrency(new BigDecimal("0.8"));
		parentBooking.setForeignCurrencyDetails(foreign);

		assertThrows(GBankingException.class, () -> service.saveSplitBookings(parentBooking,
				List.of(createSplitBooking(new BigDecimal("-80.00"), targetAccount.getId())), List.of()));
	}

	@Test
	void shouldRejectCounterBookingBetweenDifferentBaseCurrencies() {
		BankAccount sourceAccount = insertAccount("Onlinekonto", false);
		BankAccount targetAccount = insertAccount("Offlinekonto", true);
		targetAccount.setBaseCurrency(Currency.USD);
		dbController.insertOrUpdate(targetAccount);
		Booking parentBooking = insertParentBooking(sourceAccount, new BigDecimal("-80.00"));

		assertThrows(GBankingException.class, () -> service.saveSplitBookings(parentBooking,
				List.of(createSplitBooking(new BigDecimal("-80.00"), targetAccount.getId())), List.of()));
	}

	private BankAccount insertAccount(String name, boolean offline) {
		BankAccount account = new BankAccount();
		account.setAccountName(name);
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setAccountState(AccountState.ACTIVE);
		account.setBankName("Testbank");
		account.setCurrency("EUR");
		int accountNumber = Math.abs(name.hashCode() & 0x7FFFFFFF) % name.length();
		account.setIban("DE" + accountNumber);
		account.setNumber(String.valueOf(accountNumber));
		account.setOfflineAccount(offline);
		account.setSource(offline ? Source.MANUELL : Source.ONLINE);
		return dbController.insertOrUpdate(account);
	}

	private Booking insertParentBooking(BankAccount account, BigDecimal amount) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(LocalDate.of(2026, Month.MAY, 19));
		booking.setDateValue(LocalDate.of(2026, Month.MAY, 19));
		booking.setPurpose("Ausgangsbuchung");
		booking.setAmount(amount);
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.ONLINE);
		return dbController.insertOrUpdate(booking);
	}

	private Booking createSplitBooking(BigDecimal amount, Integer crossAccountId) {
		Booking booking = new Booking();
		booking.setPurpose("Teilbuchung");
		booking.setAmount(amount);
		booking.setCrossAccountId(crossAccountId);
		return booking;
	}
}
