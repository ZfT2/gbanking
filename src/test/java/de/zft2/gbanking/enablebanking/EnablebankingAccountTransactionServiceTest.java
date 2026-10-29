package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.account.PendingBookingSnapshot;
import de.zft2.gbanking.testdata.TestDataFactory;

class EnablebankingAccountTransactionServiceTest implements BaseMessages {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 8);
	private static final String ACCOUNT_UID = "account-uid";
	private static final String LONGEST = "longest";
	private static final String HASH = "hash";
	private static final String SESSION_ID = "session-id";
	private static final String NEW_SESSION_ID = "new-session-id";
	private static final String ASPSP_NAME = "Example Bank";
	private static final String PSU_TYPE = "personal";
	private static final String AUTHORIZED = "AUTHORIZED";
	private static final String RATE_LIMIT_MESSAGE = "too many requests";

	private final DBController dbController = database.controller();
	private final AccountTransactionService accountTransactionService = mock(AccountTransactionService.class);
	private final EnablebankingAuthorizationService authorizationService = mock(EnablebankingAuthorizationService.class);
	private final EnablebankingApiClient client = mock(EnablebankingApiClient.class);
	private final HbciCallbackMessageDialog dialog = mock(HbciCallbackMessageDialog.class);
	private final EnablebankingAccountTransactionService service = new EnablebankingAccountTransactionService(
			accountTransactionService, authorizationService,
			Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

	@BeforeEach
	void stubPersistence() {
		when(accountTransactionService.persistExternalAccountData(any(), any(), any(), any(), any()))
				.thenReturn(AccountTransactionRetrievalResult.success(1, 0));
	}

	@Test
	void shouldMapBookPdngAndHoldWhileIgnoringOtherStatuses() {
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setCurrency("EUR");

		var mapped = service.mapTransactions(account, List.of(transaction("BOOK", "CRDT", "10.50", "booked"),
				transaction("PDNG", "DBIT", "2.00", "pending"), transaction("HOLD", "DBIT", "3.00", "held"),
				transaction("RJCT", "DBIT", "4.00", "rejected")), null);

		assertEquals(1, mapped.booked().size());
		assertEquals(2, mapped.pending().size());
		assertEquals("10.50", mapped.booked().get(0).getAmount().toPlainString());
		assertEquals("-2.00", mapped.pending().get(0).getAmount().toPlainString());
		assertEquals(Source.ONLINE_PRENO_NEW, mapped.pending().get(0).getSource());
		assertNotNull(mapped.booked().get(0).getAdditionalDetails().getInstref());
	}

	@Test
	void shouldUpdateExistingStatusDialog() {
		String message = getText("UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2);

		service.updateStatus(dialog, 0.45d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2);

		verify(dialog).updateCurrentAction(message);
		verify(dialog).appendMessages(message);
		verify(dialog).updateProgress(0.45d);
	}

	@Test
	void shouldOfferReauthorizationFromExactlyNinetyDays() {
		LocalDateTime now = LocalDateTime.of(2026, Month.SEPTEMBER, 8, 14, 0);
		BankAccountRetrievalStatus due = retrievalStatus(42, now.minusDays(90));
		BankAccountRetrievalStatus notDue = retrievalStatus(42, now.minusDays(90).plusSeconds(1));

		assertTrue(EnablebankingAccountTransactionService.isReauthorizationDue(due, now));
		assertFalse(EnablebankingAccountTransactionService.isReauthorizationDue(notDue, now));
		assertFalse(EnablebankingAccountTransactionService.isReauthorizationDue(null, now));
	}

	@Test
	void shouldFallBackThroughConfiguredInitialTransactionPeriods() {
		EnablebankingException wrongPeriod = new EnablebankingException("wrong period", 400,
				"WRONG_TRANSACTIONS_PERIOD");
		List<Integer> lookbackDays = List.of(1440, 1080, 720, 360, 180, 90);

		when(client.getTransactions(ACCOUNT_UID, null, LONGEST, null))
				.thenReturn(new EnablebankingTransactionPage(List.of(), null));
		when(client.getTransactions(ACCOUNT_UID, TODAY.minusDays(1439), null, null))
				.thenReturn(new EnablebankingTransactionPage(List.of(), null));
		for (int days : lookbackDays.subList(1, lookbackDays.size() - 1)) {
			when(client.getTransactions(ACCOUNT_UID, TODAY.minusDays(days - 1L), null, null))
					.thenThrow(wrongPeriod);
		}
		Map<String, Object> booking = transaction("BOOK", "CRDT", "10.50", "booked");
		when(client.getTransactions(ACCOUNT_UID, TODAY.minusDays(89), null, null))
				.thenReturn(new EnablebankingTransactionPage(List.of(booking), null));

		var result = service.retrieveTransactions(client, ACCOUNT_UID, null, dialog);

		assertEquals(TODAY.minusDays(89), result.from());
		assertEquals(List.of(booking), result.transactions());
		InOrder calls = inOrder(client);
		calls.verify(client).getTransactions(ACCOUNT_UID, null, LONGEST, null);
		for (int days : lookbackDays) {
			calls.verify(client).getTransactions(ACCOUNT_UID, TODAY.minusDays(days - 1L), null, null);
		}
	}

	@Test
	void shouldMapInstructedAmountAsForeignCurrencyDetails() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		Map<String, Object> transaction = transaction("BOOK", "DBIT", "8.75", "foreign");
		transaction.put("exchange_rate", Map.of("exchange_rate", "0.875",
				"instructed_amount", Map.of("amount", "10.00", "currency", "USD")));

		var mapped = service.mapTransactions(account, List.of(transaction), null);

		assertEquals(new BigDecimal("-8.75"), mapped.booked().get(0).getAmount());
		assertEquals(new BigDecimal("-10.00"), mapped.booked().get(0).getForeignCurrencyDetails().getForeignAmount());
		assertEquals(Currency.USD, mapped.booked().get(0).getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new BigDecimal("0.875"),
				mapped.booked().get(0).getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
	}

	@Test
	void shouldIgnoreTransactionsWithoutAnyDate() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		Map<String, Object> withoutDate = transaction("BOOK", "CRDT", "10.50", "without date");
		withoutDate.remove("booking_date");
		withoutDate.remove("value_date");
		Map<String, Object> withValueDateOnly = transaction("BOOK", "CRDT", "11.50", "value date only");
		withValueDateOnly.remove("booking_date");

		var mapped = service.mapTransactions(account, List.of(withoutDate, withValueDateOnly), null);

		assertEquals(1, mapped.booked().size());
		assertEquals(1, mapped.skippedWithoutDate());
		assertEquals("11.50", mapped.booked().get(0).getAmount().toPlainString());
		assertEquals(LocalDate.of(2026, 8, 21), mapped.booked().get(0).getDateBooking());
	}

	@Test
	void retrieveShouldContinueValidSessionFromLastBookingDateAfterDeclinedReauthorization() {
		BankAccess access = createAccess();
		access.getEnablebanking().setRateLimitUntil(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
		dbController.insertOrUpdate(access);
		BankAccount account = createAccount(access, HASH);
		markRetrievedLongAgo(account);
		EnablebankingRemoteAccount remote = remote(HASH);
		LocalDate from = LocalDate.of(2026, Month.AUGUST, 20);
		Map<String, Object> outdated = transaction("BOOK", "CRDT", "1.00", "outdated");
		outdated.put("booking_date", "2026-08-19");
		Map<String, Object> withoutDate = transaction("PDNG", "DBIT", "2.00", "without date");
		withoutDate.remove("booking_date");
		withoutDate.remove("value_date");
		when(client.getSession(SESSION_ID)).thenReturn(session(SESSION_ID, AUTHORIZED, remote));
		when(client.getAccountDetails(remote.uid())).thenReturn(remote);
		when(accountTransactionService.getLastOnlineBookingDate(account)).thenReturn(from.plusDays(7));
		when(client.getTransactions(remote.uid(), from, null, null)).thenReturn(new EnablebankingTransactionPage(
				List.of(transaction("BOOK", "CRDT", "10.50", "booked"), outdated), "next-page"));
		when(client.getTransactions(remote.uid(), from, null, "next-page")).thenReturn(new EnablebankingTransactionPage(
				List.of(transaction("PDNG", "DBIT", "2.00", "pending"), withoutDate), " "));
		when(client.getBalances(remote.uid())).thenReturn(List.of(balance("OTHR", Map.of("amount", "1.00")),
				balance("CLBD", Map.of()), balance("ITBD", Map.of("amount", "90.00", "currency", "EUR"))));

		AccountTransactionRetrievalResult result = withMockedUi(() -> service.retrieve(account));

		assertEquals(AccountTransactionRetrievalResult.success(1, 0), result);
		verify(accountTransactionService).persistExternalAccountData(eq(account), eq(Optional.of(new BigDecimal("90.00"))),
				argThat(bookings -> bookings.size() == 1), argThat(pending -> pending.orElseThrow().bookings().size() == 1
						&& from.equals(pending.orElseThrow().fromInclusive())), eq("Enablebanking"));
		verify(dialog).updateCurrentAction(getText("UI_DIALOG_ENABLEBANKING_STATUS_REAUTHORIZATION_SKIPPED"));
		verify(dialog).appendMessages(getText("UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS_SKIPPED", 1));
		verify(dialog).markFinished(true);
		verify(authorizationService, never()).authorize(any(), any(), any(), any());
		assertNull(dbController.getBankAccessById(access.getId()).getEnablebanking().getRateLimitUntil());
	}

	@ParameterizedTest
	@CsvSource({ "AUTHORIZED, 0, 0", "PENDING, 60, 0", "AUTHORIZED, 60, 401" })
	void retrieveShouldAuthorizeNewSessionAndRetrieveLongestHistoryIfStoredSessionIsUnusable(String status,
			long validMinutes, int httpStatus) {
		BankAccess access = createAccess();
		BankAccount account = createAccount(access, HASH);
		EnablebankingRemoteAccount remote = remote(HASH);
		if (httpStatus > 0) {
			when(client.getSession(SESSION_ID)).thenThrow(new EnablebankingException("unauthorized", httpStatus));
		} else {
			when(client.getSession(SESSION_ID)).thenReturn(new EnablebankingSession(SESSION_ID, status,
					OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(validMinutes), List.of(remote)));
		}
		stubAuthorization(session(NEW_SESSION_ID, AUTHORIZED, remote), "Other Bank", ASPSP_NAME);
		stubLongestRetrieval(remote);

		AccountTransactionRetrievalResult result = withMockedUi(() -> service.retrieve(account));

		assertEquals(AccountTransactionRetrievalResult.success(1, 0), result);
		assertEquals(NEW_SESSION_ID, dbController.getBankAccessById(access.getId()).getEnablebanking().getSessionId());
		verify(accountTransactionService).persistExternalAccountData(eq(account), eq(Optional.empty()),
				argThat(bookings -> bookings.size() == 1), eq(Optional.of(new PendingBookingSnapshot(List.of(), null))),
				eq("Enablebanking"));
		verify(accountTransactionService, never()).getLastOnlineBookingDate(any());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Other Bank   | AUTHORIZED | hash    | Das konfigurierte Kreditinstitut wird von Enablebanking nicht mehr angeboten.",
			"Example Bank | PENDING    | hash    | Die Enablebanking-Sitzung wurde nicht autorisiert.",
			"Example Bank | AUTHORIZED | unknown | Das Konto konnte in der aktuellen Enablebanking-Sitzung nicht wiedererkannt werden." })
	void retrieveShouldReportAuthorizationAndAccountMatchingErrors(String aspspName, String sessionStatus,
			String providerAccountId, String expectedMessage) {
		BankAccount account = createAccount(createAccess(), providerAccountId);
		when(client.getSession(SESSION_ID)).thenThrow(new EnablebankingException("expired", 401));
		stubAuthorization(session(NEW_SESSION_ID, sessionStatus, remote(HASH)), aspspName);

		AccountTransactionRetrievalResult result = withMockedUi(() -> service.retrieve(account));

		assertEquals(AccountTransactionRetrievalResult.failure(expectedMessage), result);
		verify(accountTransactionService).persistExternalRetrievalStatus(account, result);
		verify(dialog).appendMessages(expectedMessage);
		verify(dialog).markFinished(false);
	}

	@Test
	void retrieveShouldStartAndHonorRateLimitCooldown() {
		BankAccess access = createAccess();
		BankAccount account = createAccount(access, HASH);
		when(client.getSession(SESSION_ID)).thenThrow(new EnablebankingException(RATE_LIMIT_MESSAGE, 429));

		AccountTransactionRetrievalResult rejected = withMockedUi(() -> service.retrieve(account));
		AccountTransactionRetrievalResult cooldown = service.retrieve(account);

		OffsetDateTime rateLimitUntil = dbController.getBankAccessById(access.getId()).getEnablebanking().getRateLimitUntil();
		assertEquals(AccountTransactionRetrievalResult.failure(RATE_LIMIT_MESSAGE), rejected);
		assertEquals(AccountTransactionRetrievalResult.failure(getText("ERROR_ENABLEBANKING_RATE_LIMIT", rateLimitUntil)), cooldown);
		assertTrue(rateLimitUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(5)));
		verify(client).getSession(SESSION_ID);
	}

	@Test
	void retrieveShouldFailWithoutUsableEnablebankingAccess() {
		BankAccess inactiveAccess = createAccess();
		inactiveAccess.setActive(false);
		dbController.insertOrUpdate(inactiveAccess);
		BankAccess fintsAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		AccountTransactionRetrievalResult expected = AccountTransactionRetrievalResult.failure(
				getText("ERROR_ACCOUNT_TRANSACTION_RETRIEVAL_NO_BANK_ACCESS"));

		for (BankAccount account : Arrays.asList(null, TestDataFactory.createSampleAccount(null),
				createAccount(inactiveAccess, HASH), createAccount(fintsAccess, HASH))) {
			assertEquals(expected, service.retrieve(account));
		}
		verify(accountTransactionService, times(4)).persistExternalRetrievalStatus(any(), eq(expected));
	}

	@Test
	void retrieveShouldReauthorizeDueAccessAndServeBatchResultsToRemainingAccounts() {
		BankAccess access = createAccess();
		BankAccount account = createAccount(access, HASH);
		BankAccount otherAccount = createAccount(access, "other-hash");
		markRetrievedLongAgo(account);
		when(dialog.requestConfirmation(any(), any(), any(), any())).thenReturn(true);
		stubAuthorization(session(NEW_SESSION_ID, AUTHORIZED, remote(HASH), remote("other-hash")), ASPSP_NAME);
		stubLongestRetrieval(remote(HASH));
		stubLongestRetrieval(remote("other-hash"));

		AccountTransactionRetrievalResult result = withMockedUi(() -> service.retrieve(account));
		AccountTransactionRetrievalResult batchResult = service.retrieve(otherAccount);

		assertEquals(AccountTransactionRetrievalResult.success(1, 0), result);
		assertEquals(AccountTransactionRetrievalResult.success(1, 0), batchResult);
		assertEquals(NEW_SESSION_ID, dbController.getBankAccessById(access.getId()).getEnablebanking().getSessionId());
		verify(client, never()).getSession(any());
		verify(authorizationService).authorize(any(), any(), any(), any());
		verify(accountTransactionService, times(2)).persistExternalAccountData(any(), any(), any(), any(), any());
		verify(dialog).markFinished(true);
	}

	@Test
	void reauthorizeAndRetrieveShouldRejectMissingOrNonEnablebankingAccess() {
		BankAccess fintsAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("10020030"));
		BankAccess unknownAccess = new BankAccess();
		unknownAccess.setId(4711);
		String expectedMessage = getText("ERROR_ENABLEBANKING_REAUTHORIZATION_ACCESS");

		for (BankAccess access : Arrays.asList(null, unknownAccess, fintsAccess)) {
			assertEquals(expectedMessage, assertThrows(EnablebankingException.class,
					() -> service.reauthorizeAndRetrieve(access)).getMessage());
		}
	}

	@Test
	void reauthorizeAndRetrieveShouldFailWithoutAssignedAccounts() {
		BankAccess access = createAccess();
		stubAuthorization(session(NEW_SESSION_ID, AUTHORIZED), ASPSP_NAME);

		assertFalse(withMockedUi(() -> service.reauthorizeAndRetrieve(access)));
		verify(dialog).appendMessages(getText("ERROR_ENABLEBANKING_REAUTHORIZATION_NO_ACCOUNTS"));
		verify(dialog).markFinished(false);
	}

	@Test
	void reauthorizeAndRetrieveShouldContinueAfterAccountErrorsAndStopAtRateLimit() {
		BankAccess access = createAccess();
		List.of("hash-1", "hash-2", "hash-3").forEach(hash -> createAccount(access, hash));
		List<BankAccount> accounts = dbController.getAllByParent(BankAccount.class, access.getId());
		List<EnablebankingRemoteAccount> remotes = accounts.stream()
				.map(account -> remote(account.getProviderAccountId())).toList();
		stubAuthorization(session(NEW_SESSION_ID, AUTHORIZED, remotes.toArray(new EnablebankingRemoteAccount[0])),
				ASPSP_NAME);
		stubLongestRetrieval(remotes.get(0));
		when(client.getBalances(remotes.get(0).uid()))
				.thenReturn(List.of(balance("CLBD", Map.of("amount", "5.00", "currency", "USD"))));
		when(client.getAccountDetails(remotes.get(1).uid())).thenThrow(new EnablebankingException(RATE_LIMIT_MESSAGE, 429));

		assertFalse(withMockedUi(() -> service.reauthorizeAndRetrieve(access)));
		verifyPersistedFailure(accounts.get(0), "Der Kontosaldo wurde nicht in der Kontowährung EUR geliefert.");
		verifyPersistedFailure(accounts.get(1), RATE_LIMIT_MESSAGE);
		verifyPersistedFailure(accounts.get(2), RATE_LIMIT_MESSAGE);
		verify(client, never()).getAccountDetails(remotes.get(2).uid());
		assertNotNull(dbController.getBankAccessById(access.getId()).getEnablebanking().getRateLimitUntil());
		verify(dialog).markFinished(false);
	}

	@Test
	void retrieveTransactionsShouldEndInitialFallbackAtShortestPeriodAndRejectInvalidResponses() {
		EnablebankingException serverError = new EnablebankingException("server error", 500);
		when(client.getTransactions(eq(ACCOUNT_UID), any(), any(), isNull()))
				.thenReturn(new EnablebankingTransactionPage(List.of(), null));

		var emptyResult = service.retrieveTransactions(client, ACCOUNT_UID, null, dialog);
		doThrow(new EnablebankingException("wrong period", 400, "WRONG_TRANSACTIONS_PERIOD")).when(client)
				.getTransactions(eq(ACCOUNT_UID), any(LocalDate.class), isNull(), isNull());
		EnablebankingException unavailablePeriod = assertThrows(EnablebankingException.class,
				() -> service.retrieveTransactions(client, ACCOUNT_UID, null, dialog));
		doThrow(serverError).when(client).getTransactions(eq(ACCOUNT_UID), any(LocalDate.class), isNull(), isNull());
		EnablebankingException rethrown = assertThrows(EnablebankingException.class,
				() -> service.retrieveTransactions(client, ACCOUNT_UID, null, dialog));
		doReturn(new EnablebankingTransactionPage(List.of(), "same-key")).when(client)
				.getTransactions(eq(ACCOUNT_UID), eq(TODAY), isNull(), any());
		EnablebankingException repeatedKey = assertThrows(EnablebankingException.class,
				() -> service.retrieveTransactions(client, ACCOUNT_UID, TODAY, dialog));

		assertEquals(List.of(), emptyResult.transactions());
		assertEquals(TODAY.minusDays(89), emptyResult.from());
		assertEquals("Enablebanking stellt auch für die letzten 90 Tage keine Umsätze bereit.", unavailablePeriod.getMessage());
		assertSame(serverError, rethrown);
		assertEquals("Enablebanking hat einen ungültigen Fortsetzungsschlüssel geliefert.", repeatedKey.getMessage());
	}

	@Test
	void shouldMapCounterpartyAndFallbackFieldsAndSkipBookingsBeforeStart() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		Map<String, Object> debit = transaction("BOOK", "DBIT", "8.00", " ");
		debit.put("booking_date", "invalid-date");
		debit.put("transaction_date", "2026-08-22T10:15:00");
		debit.put("remittance_information_unstructured", "fallback purpose");
		debit.put("creditor", Map.of("name", "Shop"));
		debit.put("creditor_account", Map.of("iban", "DE02120300000000202051"));
		debit.put("creditor_agent", Map.of("bic_fi", "BYLADEM1001"));
		debit.put("balance_after_transaction", Map.of("balance_amount", Map.of("amount", "91.50")));
		debit.put("bank_transaction_code", Map.of("code", "PMNT"));
		Map<String, Object> beforeStart = transaction("BOOK", "CRDT", "1.00", "before start");
		beforeStart.put("booking_date", "2026-08-19");
		Map<String, Object> withoutAmount = transaction("BOOK", "CRDT", "1.00", "without amount");
		withoutAmount.put("transaction_amount", Map.of("currency", "EUR"));
		List<Map<String, Object>> invalidTransactions = List.of(withoutAmount);

		var mapped = service.mapTransactions(account, List.of(debit, beforeStart,
				transaction("BOOK", "CRDT", "2.00", "credit")), LocalDate.of(2026, Month.AUGUST, 20));
		EnablebankingException missingAmount = assertThrows(EnablebankingException.class,
				() -> service.mapTransactions(account, invalidTransactions, null));

		Booking debitBooking = mapped.booked().get(0);
		assertEquals(2, mapped.booked().size());
		assertEquals(LocalDate.of(2026, Month.AUGUST, 22), debitBooking.getDateBooking());
		assertEquals("fallback purpose", debitBooking.getPurpose());
		assertEquals(List.of("Shop", "DE02120300000000202051", "BYLADEM1001"), Arrays.asList(
				debitBooking.getRecipient().getName(), debitBooking.getRecipient().getIban(),
				debitBooking.getRecipient().getBic()));
		assertEquals(new BigDecimal("91.50"), debitBooking.getBalance());
		assertEquals("PMNT", debitBooking.getAdditionalDetails().getGvcode());
		assertNull(mapped.booked().get(1).getRecipient());
		assertEquals("Ein Enablebanking-Umsatz enthält keinen Betrag.", missingAmount.getMessage());
	}

	private <T> T withMockedUi(Supplier<T> action) {
		try (MockedStatic<DialogWindowSupport> windows = mockStatic(DialogWindowSupport.class);
				MockedConstruction<HbciCallbackMessageDialog> dialogs = mockConstruction(HbciCallbackMessageDialog.class,
						withSettings().defaultAnswer(delegatesTo(dialog)));
				MockedConstruction<EnablebankingApiClient> clients = mockConstruction(EnablebankingApiClient.class,
						withSettings().defaultAnswer(delegatesTo(client)))) {
			return action.get();
		}
	}

	private BankAccess createAccess() {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		configuration.setApplicationId("application-id");
		dbController.insertOrUpdate(configuration);
		BankAccessEnablebanking details = new BankAccessEnablebanking();
		details.setPsd2ClientConfigurationId(configuration.getId());
		details.setAspspName(ASPSP_NAME);
		details.setAspspCountry("DE");
		details.setPsuType(PSU_TYPE);
		details.setSessionId(SESSION_ID);
		BankAccess access = new BankAccess();
		access.setAccessType(BankAccessType.ENABLEBANKING);
		access.setBankName(ASPSP_NAME);
		access.setActive(true);
		access.setEnablebanking(details);
		return dbController.insertOrUpdate(access);
	}

	private BankAccount createAccount(BankAccess access, String providerAccountId) {
		BankAccount account = TestDataFactory.createSampleAccount(access.getId());
		account.setProviderAccountId(providerAccountId);
		return dbController.insertOrUpdate(account);
	}

	private void markRetrievedLongAgo(BankAccount account) {
		dbController.upsertBankAccountRetrievalStatus(
				retrievalStatus(account.getId(), LocalDateTime.now(ZoneId.systemDefault()).minusDays(91)));
	}

	private void stubAuthorization(EnablebankingSession session, String... aspspNames) {
		when(client.getAspsps()).thenReturn(Arrays.stream(aspspNames)
				.map(name -> new EnablebankingAspsp(name, "de", 90, List.of(PSU_TYPE), List.of())).toList());
		when(authorizationService.authorize(any(), any(), eq(PSU_TYPE), isNull())).thenReturn(session);
	}

	private void stubLongestRetrieval(EnablebankingRemoteAccount remote) {
		when(client.getAccountDetails(remote.uid())).thenReturn(remote);
		when(client.getTransactions(remote.uid(), null, LONGEST, null)).thenReturn(
				new EnablebankingTransactionPage(List.of(transaction("BOOK", "CRDT", "10.50", "booked")), null));
	}

	private static EnablebankingRemoteAccount remote(String identificationHash) {
		return new EnablebankingRemoteAccount(identificationHash + "-uid", identificationHash, null, "TESTDEFFXXX",
				null, null, "CACC", "EUR", null);
	}

	private static EnablebankingSession session(String sessionId, String status, EnablebankingRemoteAccount... accounts) {
		return new EnablebankingSession(sessionId, status, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
				List.of(accounts));
	}

	private static Map<String, Object> balance(String type, Map<String, String> amount) {
		return Map.of("balance_type", type, "balance_amount", amount);
	}

	private void verifyPersistedFailure(BankAccount account, String message) {
		verify(accountTransactionService).persistExternalRetrievalStatus(
				argThat(persisted -> persisted.getId() == account.getId()),
				eq(AccountTransactionRetrievalResult.failure(message)));
	}

	private Map<String, Object> transaction(String status, String indicator, String amount, String purpose) {
		Map<String, Object> transaction = new LinkedHashMap<>();
		transaction.put("status", status);
		transaction.put("credit_debit_indicator", indicator);
		transaction.put("booking_date", "2026-08-20");
		transaction.put("value_date", "2026-08-21");
		transaction.put("transaction_amount", Map.of("amount", amount, "currency", "EUR"));
		transaction.put("remittance_information", List.of(purpose));
		transaction.put("entry_reference", purpose + "-reference");
		return transaction;
	}

	private BankAccountRetrievalStatus retrievalStatus(int accountId, LocalDateTime retrievedAt) {
		return new BankAccountRetrievalStatus(accountId, retrievedAt, AccountRetrievalStatus.SUCCESS, 0, 0, null);
	}
}
