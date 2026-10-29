package de.zft2.gbanking.paypal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.testdata.TestDataFactory;

class PaypalAccountTransactionServiceTest implements BaseMessages {

	private static final String API_USER = "api-user";
	private static final String SIGNATURE = "signature";
	private static final Instant END = Instant.parse("2026-07-22T12:34:56Z");
	private static final Instant TIMESTAMP = Instant.parse("2026-07-19T12:34:56Z");

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	private final DBController dbController = database.controller();
	private final PaypalSoapClient client = mock(PaypalSoapClient.class);
	private final AccountTransactionService accountTransactionService = mock(AccountTransactionService.class);
	private final PaypalAccountTransactionService service = new PaypalAccountTransactionService(client, accountTransactionService);
	private final char[] password = "secret".toCharArray();

	@Test
	void resolveStart_shouldLimitInitialRetrievalToThreeYears() {
		assertEquals(Instant.parse("2023-07-22T12:34:56Z"), service.resolveStart(new BankAccount(), END));
	}

	@Test
	void resolveStart_shouldClampOldBookingOverlapToPaypalLimit() {
		BankAccount account = new BankAccount();
		when(accountTransactionService.getLastOnlineBookingDate(account)).thenReturn(LocalDate.of(2020, Month.JANUARY, 1));

		assertEquals(Instant.parse("2023-07-22T12:34:56Z"), service.resolveStart(account, END));
	}

	@Test
	void resolveStart_shouldKeepOverlapForRecentBookings() {
		BankAccount account = new BankAccount();
		when(accountTransactionService.getLastOnlineBookingDate(account)).thenReturn(LocalDate.of(2026, Month.JULY, 20));

		assertEquals(Instant.parse("2026-07-19T00:00:00Z"), service.resolveStart(account, END));
	}

	@Test
	void mapBooking_shouldUseNetAmountAndKeepEmbeddedFeeAsMetadata() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		PaypalTransaction transaction = new PaypalTransaction(TIMESTAMP, "Payment", "payer@example.org",
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
		BankAccount account = TestDataFactory.createEuroAccount(42);
		PaypalTransaction fee = new PaypalTransaction(Instant.parse("2026-07-19T12:35:00Z"), "Fee", "", "", "FEE123", "Success",
				new BigDecimal("-0.50"), BigDecimal.ZERO, "EUR", new BigDecimal("-0.50"), "EUR");

		Booking booking = service.mapBooking(account, fee);

		assertEquals(new BigDecimal("-0.50"), booking.getAmount());
		assertEquals("Fee", booking.getPurpose());
	}

	@ParameterizedTest
	@CsvSource({ "-10.00, -8.75", "10.00, 8.75" })
	void mapBooking_shouldStoreSettlementInBaseCurrencyAndOriginalPaypalAmountAsForeignCurrency(String netAmount,
			String expectedAmount) {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		PaypalTransaction transaction = transaction("USD123", netAmount, "USD");
		PaypalTransactionDetails details = new PaypalTransactionDetails(new BigDecimal("-8.75"), "EUR", new BigDecimal("0.875"));

		Booking booking = service.mapBooking(account, transaction, details);

		assertEquals(new BigDecimal(expectedAmount), booking.getAmount());
		assertEquals(new BigDecimal(netAmount), booking.getForeignCurrencyDetails().getForeignAmount());
		assertEquals(Currency.USD, booking.getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new BigDecimal("0.875"), booking.getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
	}

	@Test
	void mapBooking_shouldRejectDetailsWithoutSettlementAmount() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		PaypalTransaction transaction = transaction("USD123", "-10.00", "USD");
		PaypalTransactionDetails details = new PaypalTransactionDetails(null, "EUR", null);

		assertThrows(GBankingException.class, () -> service.mapBooking(account, transaction, details));
	}

	@Test
	void mapBooking_shouldUseTheDisplayNameAsRecipientWithoutPayerEmail() {
		PaypalTransaction transaction = new PaypalTransaction(TIMESTAMP, "Payment", " ", "Test Person", "ABC123", "Success",
				BigDecimal.ONE, BigDecimal.ZERO, "EUR", BigDecimal.ONE, "EUR");

		Booking booking = service.mapBooking(TestDataFactory.createEuroAccount(42), transaction);

		assertEquals("Payment - Test Person", booking.getPurpose());
		assertEquals("Test Person", booking.getRecipient().getName());
		assertNull(booking.getRecipient().getAccountNumber());
		assertEquals(PaypalSupport.DISPLAY_NAME, booking.getRecipient().getBank());
	}

	@Test
	void retrieve_shouldPersistAFailureWithoutAnActivePaypalAccess() {
		Integer hbciAccessId = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("12345678")).getId();
		AccountTransactionRetrievalResult expected = AccountTransactionRetrievalResult.failure(
				getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_NO_BANK_ACCESS"));

		for (BankAccount account : Arrays.asList(null, TestDataFactory.createEuroAccount(42),
				TestDataFactory.createWithIdAndBankAccess(43, 4711), TestDataFactory.createWithIdAndBankAccess(44, hbciAccessId),
				paypalAccount(false))) {
			assertEquals(expected, service.retrieve(account, password));
			verify(accountTransactionService).persistExternalRetrievalStatus(account, expected);
		}
		assertArrayEquals(new char[password.length], password);
		verifyNoInteractions(client);
	}

	@Test
	void retrieve_shouldPersistTheBaseCurrencyBalanceAndTheDistinctBookings() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		when(client.getBalances(API_USER, password, SIGNATURE)).thenReturn(List.of(
				new PaypalBalance("USD", new BigDecimal("5.67")), new PaypalBalance("EUR", new BigDecimal("12.34"))));
		PaypalTransaction payment = transaction("EUR123", "-10.00", "EUR");
		when(searchTransactions()).thenReturn(List.of(payment, transaction("USD123", "-10.00", "USD"), payment));
		when(client.getTransactionDetails(API_USER, password, SIGNATURE, "USD123"))
				.thenReturn(new PaypalTransactionDetails(new BigDecimal("-8.75"), "EUR", new BigDecimal("0.875")));
		AccountTransactionRetrievalResult success = AccountTransactionRetrievalResult.success(2, 0);
		when(accountTransactionService.persistExternalAccountData(eq(account), eq(new BigDecimal("12.34")), anyList()))
				.thenReturn(success);

		assertSame(success, service.retrieve(account, password));

		List<Booking> bookings = persistedBookings();
		assertEquals(2, bookings.size());
		assertEquals(new BigDecimal("-10.00"), bookings.get(0).getAmount());
		assertEquals(new BigDecimal("-8.75"), bookings.get(1).getAmount());
		verify(client).getTransactionDetails(any(), any(), any(), any());
		assertArrayEquals(new char[password.length], password);
	}

	@Test
	void retrieve_shouldSplitTheIntervalIfPaypalReturnsTheMaximumNumberOfTransactions() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		stubEuroBalance();
		when(searchTransactions()).thenReturn(maximumTransactions())
				.thenReturn(List.of(transaction("FIRST", "1.00", "EUR")))
				.thenReturn(List.of(transaction("SECOND", "2.00", "EUR")));

		service.retrieve(account, password);

		List<Booking> bookings = persistedBookings();
		assertEquals(2, bookings.size());
		assertEquals(new BigDecimal("2.00"), bookings.get(1).getAmount());
		ArgumentCaptor<Instant> starts = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> ends = ArgumentCaptor.forClass(Instant.class);
		verify(client, times(3)).searchTransactions(any(), any(), any(), starts.capture(), ends.capture());
		assertEquals(starts.getAllValues().get(0), starts.getAllValues().get(1));
		assertEquals(ends.getAllValues().get(1), starts.getAllValues().get(2));
		assertEquals(ends.getAllValues().get(0), ends.getAllValues().get(2));
	}

	@Test
	void retrieve_shouldFailIfPaypalReturnsTheMaximumNumberOfTransactionsForOneSecond() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		stubEuroBalance();
		when(searchTransactions()).thenReturn(maximumTransactions());

		assertEquals(AccountTransactionRetrievalResult.failure("PayPal returned at least 100 transactions for one second"),
				service.retrieve(account, password));
	}

	@Test
	void retrieve_shouldFailWithoutABalanceInTheBaseCurrency() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		when(client.getBalances(API_USER, password, SIGNATURE)).thenReturn(List.of(new PaypalBalance("USD", BigDecimal.ONE)));
		AccountTransactionRetrievalResult expected = AccountTransactionRetrievalResult.failure(getText("ERROR_PAYPAL_NO_BALANCES"));

		assertEquals(expected, service.retrieve(account, password));
		verify(accountTransactionService).persistExternalRetrievalStatus(account, expected);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void retrieve_shouldPersistPaypalApiFailures(boolean authenticationFailure) throws InterruptedException {
		BankAccount account = paypalAccount(true);
		when(client.getBalances(API_USER, password, SIGNATURE)).thenThrow(new PaypalApiException("PayPal error", authenticationFailure));
		AccountTransactionRetrievalResult expected = authenticationFailure
				? AccountTransactionRetrievalResult.wrongPinFailure("PayPal error")
				: AccountTransactionRetrievalResult.failure("PayPal error");

		assertEquals(expected, service.retrieve(account, password));
		verify(accountTransactionService).persistExternalRetrievalStatus(account, expected);
		assertArrayEquals(new char[password.length], password);
	}

	@Test
	void retrieve_shouldPersistACancellationIfInterrupted() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		when(client.getBalances(API_USER, password, SIGNATURE)).thenThrow(new InterruptedException());

		AccountTransactionRetrievalResult result = service.retrieve(account, password);

		assertTrue(Thread.interrupted());
		assertEquals(AccountTransactionRetrievalResult.cancelled(getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_CANCELLED")), result);
		verify(accountTransactionService).persistExternalRetrievalStatus(account, result);
	}

	@Test
	void retrieve_shouldPersistAStorageFailureAndRethrowUnexpectedExceptions() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		stubEuroBalance();
		IllegalStateException storageFailure = new IllegalStateException("Storage failed");
		IllegalStateException statusFailure = new IllegalStateException("Status failed");
		when(accountTransactionService.persistExternalAccountData(any(BankAccount.class), any(BigDecimal.class), anyList()))
				.thenThrow(storageFailure);
		doThrow(statusFailure).when(accountTransactionService).persistExternalRetrievalStatus(any(), any());

		assertSame(storageFailure, assertThrows(IllegalStateException.class, () -> service.retrieve(account, password)));
		assertArrayEquals(new Throwable[] { statusFailure }, storageFailure.getSuppressed());
		verify(accountTransactionService).persistExternalRetrievalStatus(account,
				AccountTransactionRetrievalResult.failure(getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_STORAGE_FAILED")));
		assertArrayEquals(new char[password.length], password);
	}

	@Test
	void retrieve_shouldRethrowUnexpectedExceptionsWithoutSuppressedFailures() throws InterruptedException {
		BankAccount account = paypalAccount(true);
		IllegalStateException failure = new IllegalStateException("Unexpected");
		when(client.getBalances(API_USER, password, SIGNATURE)).thenThrow(failure);

		assertSame(failure, assertThrows(IllegalStateException.class, () -> service.retrieve(account, password)));
		assertEquals(0, failure.getSuppressed().length);
	}

	private BankAccount paypalAccount(boolean active) {
		BankAccess access = TestDataFactory.createSampleBankAccess(PaypalSupport.BANK_CODE);
		access.setAccessType(BankAccessType.PAYPAL);
		access.setActive(active);
		access.getPaypal().setUserId("owner@example.org");
		access.getPaypal().setApiUsername(API_USER);
		access.getPaypal().setApiSignature(SIGNATURE);
		BankAccount account = TestDataFactory.createEuroAccount(45);
		account.setBankAccessId(dbController.insertOrUpdate(access).getId());
		return account;
	}

	private void stubEuroBalance() throws InterruptedException {
		when(client.getBalances(API_USER, password, SIGNATURE)).thenReturn(List.of(new PaypalBalance("EUR", BigDecimal.TEN)));
	}

	private List<PaypalTransaction> searchTransactions() throws InterruptedException {
		return client.searchTransactions(eq(API_USER), eq(password), eq(SIGNATURE), any(Instant.class), any(Instant.class));
	}

	private List<Booking> persistedBookings() {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Booking>> bookings = ArgumentCaptor.forClass(List.class);
		verify(accountTransactionService).persistExternalAccountData(any(BankAccount.class), any(BigDecimal.class),
				bookings.capture());
		return bookings.getValue();
	}

	private static List<PaypalTransaction> maximumTransactions() {
		List<PaypalTransaction> transactions = new ArrayList<>();
		for (int index = 0; index < PaypalSoapClient.MAX_TRANSACTION_RESULTS; index++) {
			transactions.add(transaction("ID" + index, "1.00", "EUR"));
		}
		return transactions;
	}

	private static PaypalTransaction transaction(String transactionId, String netAmount, String currency) {
		BigDecimal amount = new BigDecimal(netAmount);
		return new PaypalTransaction(TIMESTAMP, "Payment", "", "", transactionId, "Success", amount, BigDecimal.ZERO, currency,
				amount, currency);
	}
}
