package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.service.account.AccountTransactionService;

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

		service.updateStatus(dialog, 0.45d, "UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2);

		verify(dialog).updateCurrentAction(Messages.getInstance()
				.getFormattedMessage("UI_DIALOG_ENABLEBANKING_STATUS_TRANSACTIONS", 2));
		verify(dialog).updateProgress(0.45d);
	}

	@Test
	void shouldMapInstructedAmountAsForeignCurrencyDetails() {
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setBaseCurrency(Currency.EUR);
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
}
