package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.account.PendingBookingSnapshot;

class ExternalPendingBookingPersistenceTest extends DBControllerIntegrationBaseTest {

	@Test
	void completeSnapshotShouldOnlyReplacePendingBookingsInCoveredPeriod() {
		BankAccess access = TestData.createSampleBankAccess("12345678");
		db.insertOrUpdate(access);
		BankAccount account = TestData.createSampleAccount(access.getId());
		db.insertOrUpdate(account);
		db.insertOrUpdate(pending(account, LocalDate.of(2026, 7, 1), "old"));
		db.insertOrUpdate(pending(account, LocalDate.of(2026, 8, 10), "replace"));
		Booking current = pending(account, LocalDate.of(2026, 8, 15), "current");

		AccountTransactionRetrievalResult result = new AccountTransactionService().persistExternalAccountData(account,
				Optional.empty(), List.of(), Optional.of(new PendingBookingSnapshot(List.of(current),
						LocalDate.of(2026, 8, 1))), "Enablebanking");

		List<Booking> pending = db.getAllByParent(Booking.class, account.getId()).stream()
				.filter(booking -> booking.getSource().isPrenotification()).toList();
		assertTrue(result.successful());
		assertEquals(1, result.pendingBookingCount());
		assertEquals(List.of("current", "old"), pending.stream().map(Booking::getPurpose).sorted().toList());
	}

	private Booking pending(BankAccount account, LocalDate date, String purpose) {
		Booking booking = new Booking();
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose(purpose);
		booking.setAmount(new BigDecimal("-10.00"));
		booking.setCurrency("EUR");
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.ONLINE_PRENO);
		return booking;
	}
}
