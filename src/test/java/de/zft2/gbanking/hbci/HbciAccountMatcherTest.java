package de.zft2.gbanking.hbci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.messages.Messages;

class HbciAccountMatcherTest {

	private static final String IBAN = "DE44500105175407324931";

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", value = {
			// equal IBANs decide, even if the account numbers differ
			"DE44500105175407324931 | 1          | -  | de44 5001 0517 5407 3249 31 | 2        | -  | true",
			// conflicting IBANs are not overruled by an equal account number
			"DE44500105175407324931 | 12345678   | -  | DE02120300000000202051      | 12345678 | -  | false",
			// a missing IBAN falls back to the account number, leading zeroes are ignored
			"DE44500105175407324931 | 0012345678 | -  | -                           | 12345678 | -  | true",
			"-                      | 12345678   | -  | DE02120300000000202051      | 12345678 | -  | true",
			"-                      | 12345678   | -  | -                           | 87654321 | -  | false",
			// subaccount numbers must match if both sides provide one
			"-                      | 12345678   | 01 | -                           | 12345678 | 02 | false",
			"-                      | 12345678   | 01 | -                           | 12345678 | 1  | true",
			"-                      | 12345678   | -  | -                           | 12345678 | 00 | true",
			"-                      | -          | -  | DE02120300000000202051      | 12345678 | -  | false" })
	void findAccount_shouldApplyMatchingRules(String accountIban, String accountNumber, String accountSubnumber, String kontoIban,
			String kontoNumber, String kontoSubnumber, boolean expectedMatch) {
		Konto konto = konto(kontoIban, kontoNumber, kontoSubnumber);

		Konto result = HbciAccountMatcher.findAccount(new Konto[] { konto }, account(accountIban, accountNumber, accountSubnumber));

		assertSame(expectedMatch ? konto : null, result);
	}

	@Test
	void findAccount_shouldPreferIbanMatchOverAmbiguousNumberMatches() {
		Konto ibanMatch = konto(IBAN, "99", null);
		Konto[] accounts = { konto(null, "12345678", null), konto(null, "12345678", null), ibanMatch };

		assertSame(ibanMatch, HbciAccountMatcher.findAccount(accounts, account(IBAN, "12345678", null)));
	}

	@Test
	void findAccount_shouldRejectAmbiguousMatches() {
		BankAccount bankAccount = account(null, "12345678", null);
		Konto[] accounts = { konto(null, "12345678", "00"), konto(null, "12345678", "01") };

		GBankingException exception = assertThrows(GBankingException.class, () -> HbciAccountMatcher.findAccount(accounts, bankAccount));

		assertEquals(Messages.getInstance().getFormattedMessage("EXCEPTION_HBCI_ACCOUNT_AMBIGUOUS", "*****678"), exception.getMessage());
	}

	@Test
	void findUnique_shouldSkipMissingCandidatesAndAccounts() {
		BankAccount bankAccount = account(null, "12345678", null);
		Konto konto = konto(null, "12345678", null);
		String[] candidates = { null, "without account", "match" };

		assertNull(HbciAccountMatcher.findAccount(null, bankAccount));
		assertNull(HbciAccountMatcher.findAccount(new Konto[] { konto }, null));
		assertEquals("match", HbciAccountMatcher.findUnique(candidates, candidate -> "match".equals(candidate) ? konto : null, bankAccount));
	}

	private static BankAccount account(String iban, String number, String subnumber) {
		BankAccount account = new BankAccount();
		account.setIban(iban);
		account.setNumber(number);
		account.setSubnumber(subnumber);
		return account;
	}

	private static Konto konto(String iban, String number, String subnumber) {
		Konto konto = new Konto();
		konto.iban = iban;
		konto.number = number;
		konto.subnumber = subnumber;
		return konto;
	}
}
