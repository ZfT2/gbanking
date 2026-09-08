package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;

class EnablebankingSetupServiceTest {
	@Test
	void mapApiAccount_shouldReadHolderAndAdditionalAccountData() {
		Map<String, Object> account = Map.of("uid", "uid", "identification_hash", "hash",
				"name", "Max Mustermann", "currency", "EUR", "cash_account_type", "CACC",
				"account_id", Map.of("iban", "DE44500105175407324931"),
				"all_account_ids", List.of(Map.of("scheme_name", "BBAN", "identification", "4711")),
				"account_servicer", Map.of("bic_fi", "BICADEFFXXX", "clearing_system_member_id",
						Map.of("clearing_system_id", "DEBLZ", "member_id", "50010517")));

		EnablebankingRemoteAccount remote = EnablebankingApiClient.mapAccount(account);

		assertEquals("Max Mustermann", remote.ownerName());
		assertEquals("4711", remote.number());
		assertEquals("50010517", remote.blz());
		assertEquals("BICADEFFXXX", remote.bic());
	}

	@Test
	void mapAccount_shouldUseApiAccountDetailsAndCreatePsd2Name() {
		EnablebankingRemoteAccount remote = remote("DE44500105175407324931", "BICADEFFXXX", "87654321",
				"4711", "CACC", "USD", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Example Bank", "DE", remote, null);

		assertEquals(AccountType.CURRENT_ACCOUNT + " - 4711 - USD - (PSD2)", account.getAccountName());
		assertEquals("4711", account.getNumber());
		assertEquals("87654321", account.getBlz());
		assertEquals("BICADEFFXXX", account.getBic());
		assertEquals("Max Mustermann", account.getOwnerName());
	}

	@Test
	void mapAccount_shouldDeriveGermanDetailsAndOmitUnknownAccountType() {
		EnablebankingRemoteAccount remote = remote("DE44 5001 0517 5407 3249 31", "BICADEFFXXX", null,
				null, "OTHR", "EUR", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Example Bank", "DE", remote, null);

		assertEquals("5407324931 - EUR - (PSD2)", account.getAccountName());
		assertEquals("5407324931", account.getNumber());
		assertEquals("50010517", account.getBlz());
	}

	private EnablebankingRemoteAccount remote(String iban, String bic, String blz, String number,
			String type, String currency, String ownerName) {
		return new EnablebankingRemoteAccount("uid", "hash", iban, bic, blz, number, type, currency, ownerName);
	}
}
