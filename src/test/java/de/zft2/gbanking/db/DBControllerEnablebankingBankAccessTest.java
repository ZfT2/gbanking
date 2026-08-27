package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccessPaypal;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Psd2ClientMode;

class DBControllerEnablebankingBankAccessTest extends DBControllerIntegrationBaseTest {

	@Test
	void shouldPersistEnablebankingConfigurationAccessAndProviderAccountId() throws Exception {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		configuration.setApplicationId("application-id");
		configuration.setPrivateKeyPkcs8(new byte[] { 1, 2, 3 });
		configuration.setCallbackPrivateKeyPkcs8(new byte[] { 4, 5, 6 });
		configuration.setCallbackCertificate(new byte[] { 7, 8, 9 });
		db.insertOrUpdate(configuration);

		BankAccess access = enablebankingAccess(configuration.getId());
		db.insertOrUpdate(access);
		BankAccount account = TestData.createSampleAccount(access.getId());
		account.setProviderAccountId("stable-identification-hash");
		db.insertOrUpdate(account);

		Psd2ClientConfiguration loadedConfiguration = db.getById(Psd2ClientConfiguration.class, configuration.getId());
		BankAccess loadedAccess = db.getBankAccessById(access.getId());
		BankAccount loadedAccount = db.getById(BankAccount.class, account.getId());

		assertArrayEquals(new byte[] { 1, 2, 3 }, loadedConfiguration.getPrivateKeyPkcs8());
		assertEquals(Psd2ClientConfiguration.DEFAULT_CALLBACK_URL, loadedConfiguration.getCallbackUrl());
		assertEquals(BankAccessType.ENABLEBANKING, loadedAccess.getAccessType());
		assertNotNull(loadedAccess.getEnablebanking());
		assertEquals("session-id", loadedAccess.getEnablebanking().getSessionId());
		assertEquals("stable-identification-hash", loadedAccount.getProviderAccountId());
	}

	@Test
	void commonBankAccessTableShouldOnlyContainSharedColumns() throws Exception {
		try (Statement statement = DBController.getConnection().createStatement();
				ResultSet columns = statement.executeQuery("PRAGMA table_info(bankAccess)")) {
			List<String> expectedColumns = List.of("id", "bankName", "active", "accessType", "updatedAt");
			java.util.ArrayList<String> actualColumns = new java.util.ArrayList<>();
			while (columns.next()) {
				actualColumns.add(columns.getString("name"));
			}
			assertEquals(expectedColumns, actualColumns);
			assertTrue(tableExists("bankAccessFints"));
			assertTrue(tableExists("bankAccessPaypal"));
			assertTrue(tableExists("bankAccessEnablebanking"));
			assertFalse(actualColumns.contains("blz"));
		}
	}

	@Test
	void psd2ClientConfigurationShouldSupportUpdateListAndDelete() {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		configuration.setApplicationId("application-id");
		configuration.setPrivateKeyPkcs8(new byte[] { 1, 2, 3 });
		db.insertOrUpdate(configuration);

		configuration.setClientMode(Psd2ClientMode.CENTRAL_SERVICE);
		configuration.setApplicationId("updated-application-id");
		configuration.setCallbackUrl("https://127.0.0.1:19443/callback");
		configuration.setCallbackCertificate(new byte[] { 4, 5, 6 });
		db.insertOrUpdate(configuration);

		Psd2ClientConfiguration storedConfiguration = db.getById(Psd2ClientConfiguration.class, configuration.getId());
		assertEquals(Psd2ClientMode.CENTRAL_SERVICE, storedConfiguration.getClientMode());
		assertEquals("updated-application-id", storedConfiguration.getApplicationId());
		assertEquals("https://127.0.0.1:19443/callback", storedConfiguration.getCallbackUrl());
		assertArrayEquals(new byte[] { 4, 5, 6 }, storedConfiguration.getCallbackCertificate());
		assertEquals(configuration.getId(), db.getAll(Psd2ClientConfiguration.class).get(0).getId());

		assertTrue(db.delete(configuration, null));
		assertNull(db.getById(Psd2ClientConfiguration.class, configuration.getId()));
		assertTrue(db.getAll(Psd2ClientConfiguration.class).isEmpty());
	}

	@Test
	void changingBankAccessTypeShouldRemoveObsoleteDetailRows() throws Exception {
		BankAccess access = db.insertOrUpdate(TestData.createSampleBankAccess("44444444"));
		assertDetailRowCounts(access.getId(), 1, 0, 0);

		BankAccessPaypal paypal = new BankAccessPaypal();
		paypal.setUserId("paypal-user");
		paypal.setApiUsername("paypal-api-user");
		paypal.setApiSignature("paypal-signature");
		access.setAccessType(BankAccessType.PAYPAL);
		access.setPaypal(paypal);
		db.insertOrUpdate(access);

		assertDetailRowCounts(access.getId(), 0, 1, 0);
		BankAccess paypalAccess = db.getBankAccessById(access.getId());
		assertEquals(BankAccessType.PAYPAL, paypalAccess.getAccessType());
		assertEquals("paypal-user", paypalAccess.getPaypal().getUserId());

		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		configuration.setApplicationId("application-id");
		db.insertOrUpdate(configuration);
		BankAccessEnablebanking enablebanking = enablebankingAccess(configuration.getId()).getEnablebanking();
		access.setAccessType(BankAccessType.ENABLEBANKING);
		access.setEnablebanking(enablebanking);
		db.insertOrUpdate(access);

		assertDetailRowCounts(access.getId(), 0, 0, 1);
		BankAccess enablebankingAccess = db.getBankAccessById(access.getId());
		assertEquals(BankAccessType.ENABLEBANKING, enablebankingAccess.getAccessType());
		assertEquals("session-id", enablebankingAccess.getEnablebanking().getSessionId());
	}

	private BankAccess enablebankingAccess(int configurationId) {
		BankAccess access = new BankAccess();
		access.setAccessType(BankAccessType.ENABLEBANKING);
		access.setBankName("Example Bank");
		access.setActive(true);
		BankAccessEnablebanking details = new BankAccessEnablebanking();
		details.setPsd2ClientConfigurationId(configurationId);
		details.setAspspName("Example Bank");
		details.setAspspCountry("DE");
		details.setPsuType("personal");
		details.setSessionId("session-id");
		details.setValidUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
		access.setEnablebanking(details);
		return access;
	}

	private boolean tableExists(String name) throws Exception {
		try (var statement = DBController.getConnection().prepareStatement(
				"SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
			statement.setString(1, name);
			try (ResultSet result = statement.executeQuery()) {
				return result.next();
			}
		}
	}

	private void assertDetailRowCounts(int bankAccessId, int expectedFints, int expectedPaypal,
			int expectedEnablebanking) throws Exception {
		assertEquals(expectedFints, countDetailRows(
				"SELECT COUNT(*) FROM bankAccessFints WHERE bankAccess_id = ?", bankAccessId));
		assertEquals(expectedPaypal, countDetailRows(
				"SELECT COUNT(*) FROM bankAccessPaypal WHERE bankAccess_id = ?", bankAccessId));
		assertEquals(expectedEnablebanking, countDetailRows(
				"SELECT COUNT(*) FROM bankAccessEnablebanking WHERE bankAccess_id = ?", bankAccessId));
	}

	private int countDetailRows(String sql, int bankAccessId) throws Exception {
		try (PreparedStatement statement = DBController.getConnection().prepareStatement(sql)) {
			statement.setInt(1, bankAccessId);
			try (ResultSet result = statement.executeQuery()) {
				assertTrue(result.next());
				return result.getInt(1);
			}
		}
	}
}
