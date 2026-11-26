package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.account.PendingBookingSnapshot;
import de.zft2.gbanking.testdata.TestDataFactory;

class ExternalPendingBookingPersistenceTest extends DBControllerIntegrationBaseTest {

	@Test
	void completeSnapshotShouldOnlyReplacePendingBookingsInCoveredPeriod() {
		BankAccess access = TestDataFactory.createSampleBankAccess("12345678");
		db.insertOrUpdate(access);
		BankAccount account = TestDataFactory.createSampleAccount(access.getId());
		db.insertOrUpdate(account);
		db.insertOrUpdate(pending(account, LocalDate.of(2026, Month.JULY, 1), "old"));
		db.insertOrUpdate(pending(account, LocalDate.of(2026, Month.AUGUST, 10), "replace"));
		Booking current = pending(account, LocalDate.of(2026, Month.AUGUST, 15), "current");

		AccountTransactionRetrievalResult result = new AccountTransactionService().persistExternalAccountData(account,
				Optional.empty(), List.of(), Optional.of(new PendingBookingSnapshot(List.of(current),
						LocalDate.of(2026, Month.AUGUST, 1))), "Enablebanking");

		List<Booking> pending = db.getAllByParent(Booking.class, account.getId()).stream()
				.filter(booking -> booking.getSource().isPrenotification()).toList();
		assertTrue(result.successful());
		assertEquals(1, result.pendingBookingCount());
		assertEquals(List.of("current", "old"), pending.stream().map(Booking::getPurpose).sorted().toList());
	}

	@Test
	void invalidPendingBookingShouldRollBackAllRetrievedAccountData() {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setBalance(new BigDecimal("25.00"));
		db.insertOrUpdate(account);
		Booking previous = pending(account, LocalDate.of(2026, Month.AUGUST, 10), "previous");
		previous.setSource(Source.ONLINE_PRENO_NEW);
		db.insertOrUpdate(previous);
		Booking invalid = pending(account, previous.getDateBooking(), "invalid");
		invalid.setAmount(null);
		Optional<PendingBookingSnapshot> snapshot = Optional.of(new PendingBookingSnapshot(List.of(invalid), null));
		List<Booking> bookings = List.of(TestDataFactory.createSampleBooking(account.getId()));
		Optional<BigDecimal> balance = Optional.of(BigDecimal.TEN);
		AccountTransactionService service = new AccountTransactionService();

		assertThrows(GBankingException.class,
				() -> service.persistExternalAccountData(account, balance, bookings, snapshot, "Enablebanking"));

		List<Booking> stored = db.getAllByParent(Booking.class, account.getId());
		assertEquals(1, stored.size());
		assertEquals(previous.getId(), stored.get(0).getId());
		assertEquals(Source.ONLINE_PRENO_NEW, stored.get(0).getSource());
		assertEquals(new BigDecimal("25.00"), db.getById(BankAccount.class, account.getId()).getBalance());
		assertNull(db.getBankAccountRetrievalStatus(account.getId()));
	}

	private Booking pending(BankAccount account, LocalDate date, String purpose) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose(purpose);
		booking.setAmount(new BigDecimal("-10.00"));
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.ONLINE_PRENO);
		return booking;
	}
}
