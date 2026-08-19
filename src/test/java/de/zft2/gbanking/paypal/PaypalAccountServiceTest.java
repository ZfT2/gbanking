package de.zft2.gbanking.paypal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Source;

class PaypalAccountServiceTest {

	private final PaypalSoapClient client = mock(PaypalSoapClient.class);
	private final DBController database = mock(DBController.class);
	private final PaypalAccountService service = new PaypalAccountService(client, database);

	@Test
	void initialize_shouldCreateOneAccountUsingThePrimaryBalance() throws InterruptedException {
		BankAccess access = access("owner@example.org");
		stubBalances(access);
		when(database.getAll(BankAccount.class)).thenReturn(List.of());

		service.initialize(access);

		assertEquals(1, access.getAccounts().size());
		BankAccount account = access.getAccounts().get(0);
		assertEquals("PayPal - owner@example.org", account.getAccountName());
		assertEquals("EUR", account.getCurrency());
		assertEquals(new BigDecimal("12.34"), account.getBalance());
		assertEquals(Source.ONLINE, account.getSource());
		assertFalse(account.isOfflineAccount());
	}

	@Test
	void initialize_shouldAutomaticallyLinkSingleEmailMatch() throws InterruptedException {
		BankAccess access = access("owner@example.org");
		BankAccount importedAccount = account(23, "Privat owner@example.org", null);
		importedAccount.setBankName(PaypalSupport.DISPLAY_NAME);
		stubBalances(access);
		when(database.getAll(BankAccount.class)).thenReturn(List.of(importedAccount));

		service.initialize(access);

		BankAccount linkedAccount = access.getAccounts().get(0);
		assertSame(importedAccount, linkedAccount);
		assertEquals(23, linkedAccount.getId());
		assertEquals("Privat owner@example.org", linkedAccount.getAccountName());
		assertEquals("EUR", linkedAccount.getCurrency());
	}

	@Test
	void initialize_shouldNotAutomaticallyLinkAmbiguousEmailMatches() throws InterruptedException {
		BankAccess access = access("owner@example.org");
		stubBalances(access);
		when(database.getAll(BankAccount.class)).thenReturn(List.of(
				account(23, "PayPal owner@example.org", null),
				account(24, "PayPal privat owner@example.org", null)));

		service.initialize(access);

		assertEquals(0, access.getAccounts().get(0).getId());
	}

	@Test
	void getLinkableAccounts_shouldOnlyReturnPaypalAccountsWithoutBankAccess() {
		BankAccount linkable = account(23, "PayPal privat", null);
		BankAccount linked = account(24, "PayPal geschäftlich", 7);
		BankAccount other = account(25, "Bargeld", null);
		when(database.getAll(BankAccount.class)).thenReturn(List.of(linkable, linked, other));

		assertEquals(List.of(linkable), service.getLinkableAccounts());
	}

	@Test
	void linkAccount_shouldApplyRetrievedPaypalDataToSelectedAccount() {
		BankAccess access = access("owner@example.org");
		BankAccount retrievedAccount = account(0, "PayPal - owner@example.org", null);
		retrievedAccount.setCurrency("EUR");
		retrievedAccount.setBalance(new BigDecimal("12.34"));
		access.setAccounts(List.of(retrievedAccount));
		BankAccount selectedAccount = account(23, "PayPal Import", null);

		service.linkAccount(access, selectedAccount);

		assertSame(selectedAccount, access.getAccounts().get(0));
		assertEquals("EUR", selectedAccount.getCurrency());
		assertEquals(new BigDecimal("12.34"), selectedAccount.getBalance());
		assertEquals(PaypalSupport.BANK_CODE, selectedAccount.getBlz());
		assertNull(selectedAccount.getBankAccessId());
	}

	private void stubBalances(BankAccess access) throws InterruptedException {
		when(client.getBalances(access.getPaypalApiUsername(), access.getPin(), access.getPaypalApiSignature())).thenReturn(List.of(
				new PaypalBalance("EUR", new BigDecimal("12.34")),
				new PaypalBalance("USD", new BigDecimal("5.67"))));
	}

	private BankAccess access(String email) {
		BankAccess access = new BankAccess();
		access.setAccessType(BankAccessType.PAYPAL);
		access.setUserId(email);
		access.setPaypalApiUsername("api-user");
		access.setPaypalApiSignature("signature");
		access.setPin("password".toCharArray());
		return access;
	}

	private BankAccount account(int id, String name, Integer bankAccessId) {
		BankAccount account = new BankAccount();
		account.setId(id);
		account.setAccountName(name);
		account.setBankAccessId(bankAccessId);
		return account;
	}
}
