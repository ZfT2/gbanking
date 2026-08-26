package de.zft2.gbanking.paypal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.service.account.AccountTransactionService;

class PaypalAccountTransactionServiceTest {

	@Test
	void resolveStart_shouldLimitInitialRetrievalToThreeYears() {
		AccountTransactionService accountTransactionService = mock(AccountTransactionService.class);
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				accountTransactionService);
		BankAccount account = new BankAccount();
		Instant end = Instant.parse("2026-07-22T12:34:56Z");

		assertEquals(Instant.parse("2023-07-22T12:34:56Z"), service.resolveStart(account, end));
	}

	@Test
	void resolveStart_shouldClampOldBookingOverlapToPaypalLimit() {
		AccountTransactionService accountTransactionService = mock(AccountTransactionService.class);
		BankAccount account = new BankAccount();
		when(accountTransactionService.getLastOnlineBookingDate(account)).thenReturn(LocalDate.of(2020, Month.JANUARY, 1));
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				accountTransactionService);
		Instant end = Instant.parse("2026-07-22T12:34:56Z");

		assertEquals(Instant.parse("2023-07-22T12:34:56Z"), service.resolveStart(account, end));
	}

	@Test
	void resolveStart_shouldKeepOverlapForRecentBookings() {
		AccountTransactionService accountTransactionService = mock(AccountTransactionService.class);
		BankAccount account = new BankAccount();
		when(accountTransactionService.getLastOnlineBookingDate(account)).thenReturn(LocalDate.of(2026, Month.JULY, 20));
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				accountTransactionService);

		assertEquals(Instant.parse("2026-07-19T00:00:00Z"),
				service.resolveStart(account, Instant.parse("2026-07-22T12:34:56Z")));
	}

	@Test
	void mapBooking_shouldUseNetAmountAndKeepEmbeddedFeeAsMetadata() {
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				mock(AccountTransactionService.class));
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setBaseCurrency(Currency.EUR);
		PaypalTransaction transaction = new PaypalTransaction(Instant.parse("2026-07-19T12:34:56Z"), "Payment", "payer@example.org",
				"Test Person", "ABC123", "Success", new BigDecimal("10.00"), new BigDecimal("-0.50"), "EUR",
				new BigDecimal("9.50"), "EUR");

		Booking booking = service.mapBooking(account, transaction);

		assertEquals(new BigDecimal("9.50"), booking.getAmount());
		assertEquals(new BigDecimal("-0.50"), booking.getFee().getAmount());
		assertEquals(Currency.EUR, booking.getFee().getCurrency());
		assertEquals("Payment", booking.getAdditionalDetails().getGvcode());
		assertEquals("payer@example.org", booking.getRecipient().getAccountNumber());
	}

	@Test
	void mapBooking_shouldMapStandaloneFeeTransactionAsOwnBooking() {
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				mock(AccountTransactionService.class));
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setBaseCurrency(Currency.EUR);
		PaypalTransaction fee = new PaypalTransaction(Instant.parse("2026-07-19T12:35:00Z"), "Fee", "", "", "FEE123", "Success",
				new BigDecimal("-0.50"), BigDecimal.ZERO, "EUR", new BigDecimal("-0.50"), "EUR");

		Booking booking = service.mapBooking(account, fee);

		assertEquals(new BigDecimal("-0.50"), booking.getAmount());
		assertEquals("Fee", booking.getPurpose());
	}

	@Test
	void mapBooking_shouldStoreSettlementInBaseCurrencyAndOriginalPaypalAmountAsForeignCurrency() {
		PaypalAccountTransactionService service = new PaypalAccountTransactionService(mock(PaypalSoapClient.class),
				mock(AccountTransactionService.class));
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setBaseCurrency(Currency.EUR);
		PaypalTransaction transaction = new PaypalTransaction(Instant.parse("2026-07-19T12:34:56Z"), "Payment", "", "",
				"USD123", "Success", new BigDecimal("-10.00"), BigDecimal.ZERO, "USD", new BigDecimal("-10.00"), "USD");
		PaypalTransactionDetails details = new PaypalTransactionDetails(new BigDecimal("-8.75"), "EUR", new BigDecimal("0.875"));

		Booking booking = service.mapBooking(account, transaction, details);

		assertEquals(new BigDecimal("-8.75"), booking.getAmount());
		assertEquals(new BigDecimal("-10.00"), booking.getForeignCurrencyDetails().getForeignAmount());
		assertEquals(Currency.USD, booking.getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new BigDecimal("0.875"), booking.getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
	}
}
