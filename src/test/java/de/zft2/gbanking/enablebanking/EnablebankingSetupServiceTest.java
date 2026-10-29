package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccessPaypal;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Psd2ClientMode;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.testdata.TestDataFactory;

class EnablebankingSetupServiceTest {

	@RegisterExtension
	static final TestDatabaseExtension database = new TestDatabaseExtension();

	private static final String ASPSP_NAME = "Example Bank";
	private static final String PSU_TYPE = "personal";
	private static final String SESSION_ID = "session-id";
	private static final String EUR = "EUR";
	private static final String IBAN = "DE44500105175407324931";
	private static final String PEM = "pem";
	private static final byte[] KEY = { 1, 2, 3 };
	private static final OffsetDateTime VALID_UNTIL = OffsetDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZoneOffset.UTC);

	private final DBController dbController = database.controller();
	private final EnablebankingAuthorizationService authorizationService = mock(EnablebankingAuthorizationService.class);
	private final EnablebankingApiClient client = mock(EnablebankingApiClient.class);
	private final BankAccessService bankAccessService = mock(BankAccessService.class);
	private final EnablebankingAccountTransactionService retrievalService = mock(EnablebankingAccountTransactionService.class);
	private final EnablebankingSetupService service = new EnablebankingSetupService(authorizationService);

	@BeforeEach
	void registerServices() {
		ServiceRegistry.setService(BankAccessService.class, bankAccessService);
		ServiceRegistry.setService(EnablebankingAccountTransactionService.class, retrievalService);
	}

	@AfterEach
	void removeServices() {
		ServiceRegistry.removeService(BankAccessService.class);
		ServiceRegistry.removeService(EnablebankingAccountTransactionService.class);
	}

	@Test
	void getPersonalConfiguration_shouldReturnTheOldestPersonalConfigurationOrANewOne() {
		assertEquals(0, service.getPersonalConfiguration().getId());
		storeConfiguration(Psd2ClientMode.CENTRAL_SERVICE);
		Psd2ClientConfiguration oldest = storeConfiguration(Psd2ClientMode.PERSONAL);
		storeConfiguration(Psd2ClientMode.PERSONAL);

		assertEquals(oldest.getId(), service.getPersonalConfiguration().getId());
	}

	@ParameterizedTest
	@CsvSource({ "' ', pem, https://callback, Die Enablebanking Application-ID fehlt.",
			"app, , https://callback, Bitte die PEM-Datei mit dem privaten Enablebanking-Schlüssel auswählen.",
			"app, ' ', https://callback, Bitte die PEM-Datei mit dem privaten Enablebanking-Schlüssel auswählen.",
			"app, pem, ' ', Die Enablebanking Callback-URL fehlt." })
	void configure_shouldRejectAnIncompleteConfiguration(String applicationId, String pem, String callbackUrl,
			String message) {
		try (MockedStatic<EnablebankingPrivateKeyReader> keyReader = mockStatic(EnablebankingPrivateKeyReader.class)) {
			keyReader.when(() -> EnablebankingPrivateKeyReader.readPkcs8(PEM)).thenReturn(KEY);

			EnablebankingException exception = assertThrows(EnablebankingException.class,
					() -> service.configure(applicationId, pem, callbackUrl));

			assertEquals(message, exception.getMessage());
		}
		assertTrue(dbController.getAll(Psd2ClientConfiguration.class).isEmpty());
	}

	@Test
	void configure_shouldValidateTheApplicationStoreTheConfigurationAndSortTheAspsps() {
		Psd2ClientConfiguration existing = storeConfiguration(Psd2ClientMode.PERSONAL);
		when(client.getAspsps()).thenReturn(List.of(aspsp("Zeta", "DE"), aspsp("alpha", "DE"), aspsp("Beta", "AT"),
				aspsp("Gamma", "DE")));
		try (MockedStatic<EnablebankingPrivateKeyReader> keyReader = mockStatic(EnablebankingPrivateKeyReader.class);
				MockedConstruction<EnablebankingCallbackMaterialService> materialServices = mockConstruction(
						EnablebankingCallbackMaterialService.class);
				MockedConstruction<EnablebankingApiClient> clients = mockConstruction(EnablebankingApiClient.class,
						withSettings().defaultAnswer(delegatesTo(client)))) {
			keyReader.when(() -> EnablebankingPrivateKeyReader.readPkcs8(PEM)).thenReturn(KEY);

			List<EnablebankingAspsp> aspsps = service.configure(" application-id ", PEM, " https://callback ");

			assertEquals(List.of("Beta", "alpha", "Gamma", "Zeta"), aspsps.stream().map(aspsp -> aspsp.name()).toList());
			assertEquals(1, clients.constructed().size());
			verify(materialServices.constructed().get(0))
					.ensureMaterial(argThat(configuration -> configuration.getId() == existing.getId()));
		}
		verify(client).validateApplication();
		List<Psd2ClientConfiguration> stored = dbController.getAll(Psd2ClientConfiguration.class);
		assertEquals(1, stored.size());
		assertEquals("application-id", stored.get(0).getApplicationId());
		assertEquals("https://callback", stored.get(0).getCallbackUrl());
		assertArrayEquals(KEY, stored.get(0).getPrivateKeyPkcs8());
	}

	@Test
	void authorize_shouldRejectAnUnauthorizedSession() {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		EnablebankingAspsp aspsp = aspsp(ASPSP_NAME, "DE");
		when(authorizationService.authorize(configuration, aspsp, PSU_TYPE, null))
				.thenReturn(new EnablebankingSession(SESSION_ID, "PENDING", VALID_UNTIL, List.of()));

		EnablebankingException exception = assertThrows(EnablebankingException.class,
				() -> service.authorize(configuration, aspsp, PSU_TYPE, null));

		assertEquals("Die Enablebanking-Sitzung wurde nicht autorisiert.", exception.getMessage());
		assertEquals(0, configuration.getId());
	}

	@Test
	void authorize_shouldCreateTheAccessWithTheSelectableAccounts() {
		storeAccount(storeAccess("10000000", true).getId(), "hbci-hash", null);
		storeAccount(storeAccess("20000000", false).getId(), "inactive-hash", null);
		BankAccount offline = storeAccount(null, null, IBAN);
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		EnablebankingAspsp aspsp = aspsp(ASPSP_NAME, "DE");
		EnablebankingAuthMethod authMethod = new EnablebankingAuthMethod("redirect", "Redirect", PSU_TYPE, "REDIRECT");
		List<EnablebankingRemoteAccount> remoteAccounts = List.of(remoteAccount(null, null, EUR),
				remoteAccount("no-currency-hash", null, null), remoteAccount("hbci-hash", null, EUR),
				remoteAccount("offline-hash", IBAN, EUR), remoteAccount("inactive-hash", null, EUR),
				remoteAccount("new-hash", null, EUR));
		when(authorizationService.authorize(configuration, aspsp, PSU_TYPE, "redirect"))
				.thenReturn(authorizedSession(remoteAccounts));

		BankAccess access = service.authorize(configuration, aspsp, PSU_TYPE, authMethod);

		assertEquals(0, access.getId());
		assertEquals(BankAccessType.ENABLEBANKING, access.getAccessType());
		assertEquals(ASPSP_NAME, access.getBankName());
		assertTrue(access.isActive());
		BankAccessEnablebanking details = access.getEnablebanking();
		assertTrue(configuration.getId() > 0);
		assertEquals(configuration.getId(), details.getPsd2ClientConfigurationId());
		assertEquals(ASPSP_NAME, details.getAspspName());
		assertEquals("DE", details.getAspspCountry());
		assertEquals(PSU_TYPE, details.getPsuType());
		assertEquals("redirect", details.getAuthMethod());
		assertEquals(SESSION_ID, details.getSessionId());
		assertEquals(VALID_UNTIL, details.getValidUntil());
		List<BankAccount> accounts = access.getAccounts();
		assertEquals(List.of("offline-hash", "inactive-hash", "new-hash"), providerAccountIds(accounts));
		assertEquals(offline.getId(), accounts.get(0).getId());
		assertEquals(AccountType.SAVINGS_ACCOUNT, accounts.get(0).getAccountType());
		assertEquals(0, accounts.get(1).getId());
	}

	@Test
	void authorize_shouldPreferADirectPaypalAccessAndItsAccounts() {
		BankAccess paypalAccess = TestDataFactory.createSampleBankAccess("30000000");
		BankAccessPaypal paypal = new BankAccessPaypal();
		paypal.setUserId("paypal-user");
		paypal.setApiUsername("paypal-api-user");
		paypal.setApiSignature("paypal-signature");
		paypalAccess.setAccessType(BankAccessType.PAYPAL);
		paypalAccess.setPaypal(paypal);
		storeAccount(dbController.insertOrUpdate(paypalAccess).getId(), "paypal-hash", null);
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		when(authorizationService.authorize(any(), any(), any(), any())).thenReturn(authorizedSession(
				List.of(remoteAccount("paypal-hash", null, EUR), remoteAccount("bank-hash", null, EUR))));

		BankAccess paypalAspspAccess = service.authorize(configuration, aspsp("PayPal (Europe)", "LU"), PSU_TYPE, null);
		BankAccess bankAccess = service.authorize(configuration, aspsp(ASPSP_NAME, "DE"), PSU_TYPE, null);

		assertTrue(paypalAspspAccess.getAccounts().isEmpty());
		assertEquals(List.of("bank-hash"), providerAccountIds(bankAccess.getAccounts()));
		assertNull(bankAccess.getEnablebanking().getAuthMethod());
	}

	@Test
	void save_shouldRejectAMissingAccessOrMissingAccounts() {
		BankAccess access = new BankAccess();
		List<BankAccount> accounts = List.of(new BankAccount());

		assertFalse(service.save(null, accounts));
		assertFalse(service.save(access, null));
		assertFalse(service.save(access, List.of()));
		verifyNoInteractions(bankAccessService, retrievalService);
	}

	@Test
	void save_shouldPersistTheAccessAndRetrieveTheSelectedAccounts() {
		BankAccess access = new BankAccess();
		BankAccount first = new BankAccount();
		BankAccount second = new BankAccount();
		List<BankAccount> selected = List.of(first, second);
		when(bankAccessService.saveBankAccessAccountsToDB(access)).thenReturn(true);

		assertTrue(service.save(access, selected));

		assertSame(selected, access.getAccounts());
		verify(retrievalService).retrieve(first);
		verify(retrievalService).retrieve(second);
	}

	@Test
	void save_shouldNotRetrieveAccountsIfTheAccessWasNotSaved() {
		BankAccess access = new BankAccess();
		List<BankAccount> selected = List.of(new BankAccount());

		assertThrows(GBankingException.class, () -> service.save(access, selected));

		verify(retrievalService, never()).retrieve(any());
	}

	@Test
	void cancelAuthorization_shouldDeleteTheSessionOnlyWithAStoredConfiguration() {
		BankAccess withoutConfiguration = enablebankingAccess(4711);
		BankAccess withConfiguration = enablebankingAccess(storeConfiguration(Psd2ClientMode.PERSONAL).getId());
		try (MockedConstruction<EnablebankingApiClient> clients = mockConstruction(EnablebankingApiClient.class,
				withSettings().defaultAnswer(delegatesTo(client)))) {
			service.cancelAuthorization(null);
			service.cancelAuthorization(new BankAccess());
			service.cancelAuthorization(withoutConfiguration);
			service.cancelAuthorization(withConfiguration);

			assertEquals(1, clients.constructed().size());
		}
		verify(client).deleteSession(SESSION_ID);
	}

	@ParameterizedTest
	@CsvSource({ "LOAN, CREDIT_ACCOUNT", "CASH, CASH_ACCOUNT", "card, CREDIT_CARD" })
	void mapAccount_shouldMapTheCashAccountType(String cashAccountType, AccountType expectedType) {
		EnablebankingRemoteAccount remote = remote(IBAN, "BICADEFFXXX", "87654321", "4711", cashAccountType, EUR,
				"Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount(ASPSP_NAME, "DE", remote, null);

		assertEquals(expectedType, account.getAccountType());
	}

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

	@Test
	void mapAccount_shouldMoveGermanIbanFromAccountNumberToIbanField() {
		EnablebankingRemoteAccount remote = remote(null, "BICADEFFXXX", "50010517",
				"DE44500105175407324931", "CACC", "EUR", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Example Bank", "DE", remote, null);

		assertEquals("DE44500105175407324931", account.getIban());
		assertEquals("5407324931", account.getNumber());
		assertEquals("50010517", account.getBlz());
	}

	@Test
	void mapAccount_shouldMoveNorwegianIbanFromAccountNumberToIbanField() {
		EnablebankingRemoteAccount remote = remote(null, "ESSENORESS", null,
				"NO9386011117947", "CARD", "NOK", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Bank Norwegian", "NO", remote, null);

		assertEquals("NO9386011117947", account.getIban());
		assertEquals("1117947", account.getNumber());
		assertTrue(account.isSEPAAccount());
	}

	@Test
	void mapAccount_shouldKeepExistingAccountNumberWhenRemoteNumberIsAnIban() {
		BankAccount existing = new BankAccount();
		existing.setNumber("4711");
		EnablebankingRemoteAccount remote = remote(null, "ESSENORESS", null,
				"NO9386011117947", "CARD", "NOK", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Bank Norwegian", "NO", remote, existing);

		assertEquals("NO9386011117947", account.getIban());
		assertEquals("4711", account.getNumber());
	}

	@Test
	void mapAccount_shouldReplaceAccountNumberThatHeldTheIban() {
		BankAccount existing = new BankAccount();
		existing.setNumber("NO9386011117947");
		EnablebankingRemoteAccount remote = remote(null, "ESSENORESS", null,
				"NO9386011117947", "CARD", "NOK", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccount("Bank Norwegian", "NO", remote, existing);

		assertEquals("NO9386011117947", account.getIban());
		assertEquals("1117947", account.getNumber());
	}

	@Test
	void mapAccountDetails_shouldKeepTheCorrectedAccountType() {
		BankAccount existing = new BankAccount();
		existing.setAccountType(AccountType.SAVINGS_ACCOUNT);
		EnablebankingRemoteAccount remote = remote("DE44500105175407324931", "BICADEFFXXX", "87654321",
				"4711", "CACC", "EUR", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccountDetails("Example Bank", "DE", remote, existing);

		assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
	}

	@Test
	void mapAccountDetails_shouldDeriveTheAccountTypeWhenNoneWasCorrected() {
		EnablebankingRemoteAccount remote = remote("DE44500105175407324931", "BICADEFFXXX", "87654321",
				"4711", "SVGS", "EUR", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccountDetails("Example Bank", "DE", remote, null);

		assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
	}

	@Test
	void mapAccountDetails_shouldKeepAnExplicitlyUnknownAccountType() {
		BankAccount existing = new BankAccount();
		existing.setAccountType(AccountType.UNKNOWN_ACCOUNT);
		EnablebankingRemoteAccount remote = remote("DE44500105175407324931", "BICADEFFXXX", "87654321",
				"4711", "CACC", "EUR", "Max Mustermann");

		BankAccount account = EnablebankingSetupService.mapAccountDetails("Example Bank", "DE", remote, existing);

		assertEquals(AccountType.UNKNOWN_ACCOUNT, account.getAccountType());
	}

	@Test
	void applyAccountType_shouldCorrectTheAccountTypeAndTheDerivedAccountName() {
		BankAccount account = new BankAccount();
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setNumber("4711");
		account.setCurrency("EUR");
		account.setAccountName(AccountType.CURRENT_ACCOUNT + " - 4711 - EUR - (PSD2)");

		EnablebankingSetupService.applyAccountType(account, AccountType.SAVINGS_ACCOUNT);

		assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
		// The account name contains the localized account type label, so it is derived from the enum
		// to keep the assertion independent of the locale of the build.
		assertEquals(AccountType.SAVINGS_ACCOUNT + " - 4711 - EUR - (PSD2)", account.getAccountName());
	}

	@Test
	void applyAccountType_shouldKeepACustomAccountName() {
		BankAccount account = new BankAccount();
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setNumber("4711");
		account.setCurrency("EUR");
		account.setAccountName("Mein Konto");

		EnablebankingSetupService.applyAccountType(account, AccountType.SAVINGS_ACCOUNT);

		assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
		assertEquals("Mein Konto", account.getAccountName());
	}

	@Test
	void applyAccountType_shouldIgnoreAnUnchangedAccountType() {
		BankAccount account = new BankAccount();
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setAccountName(AccountType.CURRENT_ACCOUNT + " - 4711 - EUR - (PSD2)");

		EnablebankingSetupService.applyAccountType(account, AccountType.CURRENT_ACCOUNT);

		assertEquals(AccountType.CURRENT_ACCOUNT + " - 4711 - EUR - (PSD2)", account.getAccountName());
	}

	@Test
	void applyAccountType_shouldIgnoreMissingValuesAndKeepAnEmptyAccountName() {
		BankAccount account = new BankAccount();
		account.setAccountType(AccountType.CURRENT_ACCOUNT);

		EnablebankingSetupService.applyAccountType(null, AccountType.SAVINGS_ACCOUNT);
		EnablebankingSetupService.applyAccountType(account, null);
		assertEquals(AccountType.CURRENT_ACCOUNT, account.getAccountType());
		EnablebankingSetupService.applyAccountType(account, AccountType.SAVINGS_ACCOUNT);

		assertEquals(AccountType.SAVINGS_ACCOUNT, account.getAccountType());
		assertNull(account.getAccountName());
	}

	private EnablebankingRemoteAccount remote(String iban, String bic, String blz, String number,
			String type, String currency, String ownerName) {
		return new EnablebankingRemoteAccount("uid", "hash", iban, bic, blz, number, type, currency, ownerName);
	}

	private static EnablebankingRemoteAccount remoteAccount(String identificationHash, String iban, String currency) {
		return new EnablebankingRemoteAccount("uid", identificationHash, iban, null, null, null, "SVGS", currency,
				"Max Mustermann");
	}

	private static EnablebankingAspsp aspsp(String name, String country) {
		return new EnablebankingAspsp(name, country, 180, List.of(PSU_TYPE), List.of());
	}

	private static EnablebankingSession authorizedSession(List<EnablebankingRemoteAccount> remoteAccounts) {
		return new EnablebankingSession(SESSION_ID, "AUTHORIZED", VALID_UNTIL, remoteAccounts);
	}

	private static BankAccess enablebankingAccess(int configurationId) {
		BankAccessEnablebanking details = new BankAccessEnablebanking();
		details.setPsd2ClientConfigurationId(configurationId);
		details.setSessionId(SESSION_ID);
		BankAccess access = new BankAccess();
		access.setEnablebanking(details);
		return access;
	}

	private static List<String> providerAccountIds(List<BankAccount> accounts) {
		return accounts.stream().map(account -> account.getProviderAccountId()).toList();
	}

	private Psd2ClientConfiguration storeConfiguration(Psd2ClientMode clientMode) {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		configuration.setClientMode(clientMode);
		return dbController.insertOrUpdate(configuration);
	}

	private BankAccess storeAccess(String blz, boolean active) {
		BankAccess access = TestDataFactory.createSampleBankAccess(blz);
		access.setActive(active);
		return dbController.insertOrUpdate(access);
	}

	private BankAccount storeAccount(Integer bankAccessId, String providerAccountId, String iban) {
		BankAccount account = TestDataFactory.createSampleAccount(bankAccessId);
		account.setProviderAccountId(providerAccountId);
		account.setIban(iban);
		return dbController.insertOrUpdate(account);
	}
}
