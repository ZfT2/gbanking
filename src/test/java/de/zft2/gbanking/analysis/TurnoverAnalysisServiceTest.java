package de.zft2.gbanking.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.analysis.TurnoverAnalysisService.AccountSelectionMode;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.AnalysisConfiguration;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.FlowDirection;
import de.zft2.gbanking.analysis.TurnoverAnalysisService.PeriodType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;

class TurnoverAnalysisServiceTest {

	private final TurnoverAnalysisService service = new TurnoverAnalysisService();

	@Test
	void eligibleAccountsExcludeIgnoredAndEmptyAccounts() {
		BankAccount active = account(1, "Giro", AccountState.ACTIVE, true);
		BankAccount inactive = account(2, "Tagesgeld", AccountState.INACTIVE, false);
		BankAccount ignored = account(3, "Ignoriert", AccountState.IGNORE, true);
		BankAccount empty = account(4, "Leer", AccountState.ACTIVE, true);

		List<Booking> bookings = List.of(
				booking(11, active, "10.00", LocalDate.of(2026, Month.JANUARY, 1), null),
				booking(12, inactive, "20.00", LocalDate.of(2026, Month.JANUARY, 2), null),
				booking(13, ignored, "30.00", LocalDate.of(2026, Month.JANUARY, 3), null));

		List<BankAccount> result = service.getEligibleAccounts(List.of(active, inactive, ignored, empty), bookings);

		assertIterableEquals(List.of(active, inactive), result);
	}

	@Test
	void linkedRebookingsAreNeutralForExternalFlowsAndCategories() {
		BankAccount giro = account(1, "Giro", AccountState.ACTIVE, true);
		BankAccount savings = account(2, "Tagesgeld", AccountState.ACTIVE, true);
		Category salary = new Category(1, "Einnahmen/Gehalt");
		Category rent = new Category(2, "Wohnen/Miete");
		Category transfer = new Category(3, "Umbuchung");

		Booking salaryBooking = booking(11, giro, "1000.00", LocalDate.of(2026, Month.JANUARY, 5), salary);
		Booking rentBooking = booking(12, giro, "-200.00", LocalDate.of(2026, Month.JANUARY, 6), rent);
		Booking transferOut = booking(13, giro, "-300.00", LocalDate.of(2026, Month.JANUARY, 7), transfer);
		Booking transferIn = booking(14, savings, "300.00", LocalDate.of(2026, Month.JANUARY, 7), transfer);
		linkRebooking(transferOut, transferIn);

		AnalysisConfiguration config = new AnalysisConfiguration(AccountSelectionMode.ALL, List.of(), PeriodType.ALL_TIME, null, null);

		var result = service.analyze(List.of(giro, savings), List.of(salaryBooking, rentBooking, transferOut, transferIn), config,
				LocalDate.of(2026, Month.JANUARY, 31));

		assertEquals(new BigDecimal("1000.00"), result.summary().income());
		assertEquals(new BigDecimal("200.00"), result.summary().expenses());
		assertEquals(new BigDecimal("800.00"), result.summary().netExternalFlow());
		assertEquals(new BigDecimal("300.00"), result.summary().neutralTransferAmount());
		assertEquals(1, result.summary().neutralTransferCount());
		assertEquals(2, result.summary().neutralBookingCount());
		assertEquals(new BigDecimal("800.00"), result.summary().selectedBalanceChange());
		assertEquals(new BigDecimal("800.00"), result.balancePoints().get(result.balancePoints().size() - 1).balance());
		assertEquals(2, result.categorySlices().size());
		assertEquals(FlowDirection.INCOME, result.categorySlices().get(0).direction());
		assertEquals("Einnahmen/Gehalt", result.categorySlices().get(0).categoryName());
		assertEquals(new BigDecimal("1000.00"), result.categorySlices().get(0).amount());
		assertEquals("Wohnen/Miete", result.categorySlices().get(1).categoryName());
	}

	@Test
	void transferOutOfCustomSelectionChangesBalanceButNotExternalFlows() {
		BankAccount giro = account(1, "Giro", AccountState.ACTIVE, true);
		BankAccount savings = account(2, "Tagesgeld", AccountState.ACTIVE, true);
		Booking salaryBooking = booking(11, giro, "1000.00", LocalDate.of(2026, Month.JANUARY, 5), new Category(1, "Gehalt"));
		Booking rentBooking = booking(12, giro, "-200.00", LocalDate.of(2026, Month.JANUARY, 6), new Category(2, "Miete"));
		Booking transferOut = booking(13, giro, "-300.00", LocalDate.of(2026, Month.JANUARY, 7), new Category(3, "Umbuchung"));
		Booking transferIn = booking(14, savings, "300.00", LocalDate.of(2026, Month.JANUARY, 7), new Category(3, "Umbuchung"));
		linkRebooking(transferOut, transferIn);

		AnalysisConfiguration config = new AnalysisConfiguration(AccountSelectionMode.CUSTOM, List.of(giro.getId()), PeriodType.ALL_TIME, null, null);

		var result = service.analyze(List.of(giro, savings), List.of(salaryBooking, rentBooking, transferOut, transferIn), config,
				LocalDate.of(2026, Month.JANUARY, 31));

		assertEquals(new BigDecimal("800.00"), result.summary().netExternalFlow());
		assertEquals(new BigDecimal("500.00"), result.summary().selectedBalanceChange());
		assertEquals(new BigDecimal("500.00"), result.balancePoints().get(result.balancePoints().size() - 1).balance());
	}

	@Test
	void rollingPeriodUsesInclusiveCurrentDateWindow() {
		BankAccount giro = account(1, "Giro", AccountState.ACTIVE, true);
		Booking oldBooking = booking(11, giro, "10.00", LocalDate.of(2026, Month.APRIL, 30), null);
		Booking firstIncludedBooking = booking(12, giro, "20.00", LocalDate.of(2026, Month.MAY, 5), null);
		Booking todayBooking = booking(13, giro, "30.00", LocalDate.of(2026, Month.JUNE, 3), null);
		AnalysisConfiguration config = new AnalysisConfiguration(AccountSelectionMode.ALL, List.of(), PeriodType.LAST_30_DAYS, null, null);

		var result = service.analyze(List.of(giro), List.of(oldBooking, firstIncludedBooking, todayBooking), config, LocalDate.of(2026, Month.JUNE, 3));

		assertEquals(LocalDate.of(2026, Month.MAY, 5), result.dateRange().from());
		assertEquals(LocalDate.of(2026, Month.JUNE, 3), result.dateRange().to());
		assertEquals(new BigDecimal("50.00"), result.summary().income());
	}

	private BankAccount account(int id, String name, AccountState state, boolean online) {
		BankAccount account = new BankAccount();
		account.setId(id);
		account.setAccountName(name);
		account.setAccountState(state);
		if (online) {
			account.setBankAccessId(100 + id);
		}
		return account;
	}

	private Booking booking(int id, BankAccount account, String amount, LocalDate date, Category category) {
		Booking booking = new Booking();
		booking.setId(id);
		booking.setAccountId(account.getId());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setAmount(new BigDecimal(amount));
		booking.setSource(Source.ONLINE);
		booking.setBookingType(booking.getAmount().signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT);
		booking.setCategory(category);
		if (category != null) {
			booking.setCategoryId(category.getId());
		}
		return booking;
	}

	private void linkRebooking(Booking first, Booking second) {
		first.setCrossAccountId(second.getAccountId());
		first.setCrossBookingId(second.getId());
		first.setBookingType(BookingType.REBOOKING_OUT);
		second.setCrossAccountId(first.getAccountId());
		second.setCrossBookingId(first.getId());
		second.setBookingType(BookingType.REBOOKING_IN);
	}
}
