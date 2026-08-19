package de.zft2.gbanking.service.importproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.core.config.ImportProperties;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;

class ImportPropertiesSynchronizationServiceTest {

	@TempDir
	Path tempDirectory;

	private DBController dbController;
	private Path propertiesDirectory;
	private String previousPropertiesDirectory;

	@BeforeEach
	void setup() throws Exception {
		propertiesDirectory = tempDirectory.resolve("properties");
		Files.createDirectories(propertiesDirectory);
		previousPropertiesDirectory = System.getProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY);
		System.setProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY, propertiesDirectory.toString());
		dbController = DBController.getInstance(tempDirectory.resolve("database").toString());
	}

	@AfterEach
	void cleanup() {
		DBController.resetConnection();
		if (previousPropertiesDirectory == null) {
			System.clearProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY);
		} else {
			System.setProperty(ImportProperties.IMPORT_PROPERTIES_DIRECTORY_PROPERTY, previousPropertiesDirectory);
		}
	}

	@Test
	void shouldImportLegacyValuesOnceAndSynchronizeDatabaseChanges() throws Exception {
		BankAccount account = insertAccount("Main account", "DE12345678901234567890", "0012345678");
		writeLegacyFiles("Main account", "DE12345678901234567890;0012345678");

		ImportPropertiesSynchronizationService service = new ImportPropertiesSynchronizationService();
		service.initializeAndSynchronize();

		List<BankAccountIdentifier> identifiers = dbController.getBankAccountIdentifiers(account.getId());
		assertEquals(3, identifiers.size());
		assertTrue(identifiers.stream().anyMatch(identifier -> identifier.propertyType() == AccountIdentifierType.ACCOUNT));
		assertEquals("Legacy interest", settingValue("pattern.bookings.INTEREST"));

		dbController.replaceBankAccountIdentifiers(account.getId(), List.of(
				new BankAccountIdentifier(0, account.getId(), AccountIdentifierType.ACCOUNT_TRANSFER, "DB-ONLY")));
		Setting interestSetting = findSetting("pattern.bookings.INTEREST");
		interestSetting.setValue("Database interest");
		dbController.insertOrUpdate(interestSetting);
		service.initializeAndSynchronize();

		assertEquals(Map.of("Main account", "DB-ONLY"), ImportPropertiesFileSupport.read(propertiesDirectory.resolve("accountTransfer.properties")));
		assertEquals("Database interest", ImportPropertiesFileSupport.read(propertiesDirectory.resolve("bookings.properties")).get("INTEREST"));
	}

	@Test
	void shouldSynchronizePatternDefaultsFromFreshDatabase() throws Exception {
		new ImportPropertiesSynchronizationService().synchronize();

		Map<String, String> bookingPatterns = ImportPropertiesFileSupport.read(propertiesDirectory.resolve("bookings.properties"));
		assertTrue(bookingPatterns.containsKey("INTEREST"));
		assertTrue(bookingPatterns.containsKey("TAX"));
	}

	@Test
	void shouldKeepUnmatchedLegacyIdentifiersUntilAccountExists() throws Exception {
		writeLegacyFiles("Later account", "LATER-123");
		ImportPropertiesSynchronizationService service = new ImportPropertiesSynchronizationService();
		service.initializeAndSynchronize();

		assertEquals("LATER-123", ImportPropertiesFileSupport.read(propertiesDirectory.resolve("accountTransfer.properties")).get("Later account"));
		BankAccount account = insertAccount("Later account", null, null);
		service.synchronize();

		assertEquals(2, dbController.getBankAccountIdentifiers(account.getId()).size());
		assertFalse(dbController.getAll(Setting.class).stream()
				.anyMatch(setting -> setting.getAttribute().startsWith("import.properties.pending.")));
	}

	@Test
	void propertyFileSupportShouldRoundTripSpecialCharacters() throws Exception {
		Path file = propertiesDirectory.resolve("roundtrip.properties");
		Map<String, String> expected = Map.of("Account name:=!", "^Text\\s+(.*);äöü", "simple", " leading value");

		ImportPropertiesFileSupport.write(file, expected);

		assertEquals(expected, ImportPropertiesFileSupport.read(file));
	}

	private void writeLegacyFiles(String accountName, String transferValues) throws Exception {
		write("account.properties", accountName + "=SPECIAL-ACCOUNT\n");
		write("accountTransfer.properties", accountName + "=" + transferValues + "\n");
		write("accountCancel.properties", "DEFAULT=^Cancellation (.*)$\n");
		write("accountSkip.properties", "SKIP=Ignored account\n");
		write("bookings.properties", "INTEREST=Legacy interest\nINTEREST_WHOLE_WORD=Interest\nTAX=Tax\n");
	}

	private void write(String fileName, String content) throws Exception {
		Files.writeString(propertiesDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
	}

	private BankAccount insertAccount(String name, String iban, String number) {
		BankAccount account = new BankAccount();
		account.setAccountName(name);
		account.setIban(iban);
		account.setNumber(number);
		account.setCurrency("EUR");
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setAccountState(AccountState.ACTIVE);
		account.setSource(Source.MANUELL);
		account.setOfflineAccount(true);
		return dbController.insertOrUpdate(account);
	}

	private String settingValue(String attribute) {
		return findSetting(attribute).getValue();
	}

	private Setting findSetting(String attribute) {
		return dbController.getAll(Setting.class).stream().filter(setting -> attribute.equals(setting.getAttribute())).findFirst().orElseThrow();
	}
}
