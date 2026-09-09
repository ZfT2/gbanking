package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.testdata.TestDataFactory;

class EnablebankingAccountTransactionServiceTest {

	private final EnablebankingAccountTransactionService service = new EnablebankingAccountTransactionService(
			new AccountTransactionService(), new EnablebankingAuthorizationService());

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
		HbciCallbackMessageDialog dialog = mock(HbciCallbackMessageDialog.class);
		String message = Messages.getInstance()
				.getFormattedMessage("UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2);

		service.updateStatus(dialog, 0.45d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2);

		verify(dialog).updateCurrentAction(message);
		verify(dialog).appendMessages(message);
		verify(dialog).updateProgress(0.45d);
	}

	@Test
	void shouldOfferReauthorizationFromExactlyNinetyDays() {
		LocalDateTime now = LocalDateTime.of(2026, 9, 8, 14, 0);
		BankAccountRetrievalStatus due = retrievalStatus(now.minusDays(90));
		BankAccountRetrievalStatus notDue = retrievalStatus(now.minusDays(90).plusSeconds(1));

		assertTrue(EnablebankingAccountTransactionService.isReauthorizationDue(due, now));
		assertFalse(EnablebankingAccountTransactionService.isReauthorizationDue(notDue, now));
		assertFalse(EnablebankingAccountTransactionService.isReauthorizationDue(null, now));
	}

	@Test
	void shouldFallBackThroughConfiguredInitialTransactionPeriods() {
		EnablebankingApiClient client = mock(EnablebankingApiClient.class);
		HbciCallbackMessageDialog dialog = mock(HbciCallbackMessageDialog.class);
		EnablebankingException wrongPeriod = new EnablebankingException("wrong period", 400,
				"WRONG_TRANSACTIONS_PERIOD");
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		List<Integer> lookbackDays = List.of(1440, 1080, 720, 360, 180, 90);

		when(client.getTransactions("account-uid", null, "longest", null))
				.thenReturn(new EnablebankingTransactionPage(List.of(), null));
		when(client.getTransactions("account-uid", today.minusDays(1439), null, null))
				.thenReturn(new EnablebankingTransactionPage(List.of(), null));
		for (int days : lookbackDays.subList(1, lookbackDays.size() - 1)) {
			when(client.getTransactions("account-uid", today.minusDays(days - 1L), null, null))
					.thenThrow(wrongPeriod);
		}
		Map<String, Object> booking = transaction("BOOK", "CRDT", "10.50", "booked");
		when(client.getTransactions("account-uid", today.minusDays(89), null, null))
				.thenReturn(new EnablebankingTransactionPage(List.of(booking), null));

		var result = service.retrieveTransactions(client, "account-uid", null, dialog);

		assertEquals(today.minusDays(89), result.from());
		assertEquals(List.of(booking), result.transactions());
		InOrder calls = inOrder(client);
		calls.verify(client).getTransactions("account-uid", null, "longest", null);
		for (int days : lookbackDays) {
			calls.verify(client).getTransactions("account-uid", today.minusDays(days - 1L), null, null);
		}
	}

	@Test
	void shouldMapInstructedAmountAsForeignCurrencyDetails() {
		BankAccount account = TestDataFactory.createEuroAccount(42);
		Map<String, Object> transaction = transaction("BOOK", "DBIT", "8.75", "foreign");
		transaction.put("exchange_rate", Map.of("exchange_rate", "0.875",
				"instructed_amount", Map.of("amount", "10.00", "currency", "USD")));

		var mapped = service.mapTransactions(account, List.of(transaction), null);

		assertEquals(new java.math.BigDecimal("-8.75"), mapped.booked().get(0).getAmount());
		assertEquals(new java.math.BigDecimal("-10.00"), mapped.booked().get(0).getForeignCurrencyDetails().getForeignAmount());
		assertEquals(Currency.USD, mapped.booked().get(0).getForeignCurrencyDetails().getForeignCurrency());
		assertEquals(new java.math.BigDecimal("0.875"),
				mapped.booked().get(0).getForeignCurrencyDetails().getExchangeRateToBaseCurrency());
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

	private BankAccountRetrievalStatus retrievalStatus(LocalDateTime retrievedAt) {
		return new BankAccountRetrievalStatus(42, retrievedAt, AccountRetrievalStatus.SUCCESS, 0, 0, null);
	}
}
