package de.zft2.gbanking.service.importproperties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountIdentifier;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;
import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.util.AppPaths;

public class ImportPropertiesSynchronizationService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(ImportPropertiesSynchronizationService.class);
	private static final String INITIALIZED_SETTING = "import.properties.initialized";
	private static final String PATTERN_PREFIX = "pattern.";
	private static final String PENDING_PREFIX = "import.properties.pending.";
	private static final Object SYNCHRONIZATION_LOCK = new Object();
	private static final List<PatternFile> PATTERN_FILES = List.of(
			new PatternFile("accountCancel.properties", PATTERN_PREFIX + "accountCancel."),
			new PatternFile("accountSkip.properties", PATTERN_PREFIX + "accountSkip."),
			new PatternFile("bookings.properties", PATTERN_PREFIX + "bookings."));

	private final Path propertiesDirectory = AppPaths.getImportPropertiesDirectory();

	public void initializeAndSynchronize() {
		synchronized (SYNCHRONIZATION_LOCK) {
			Map<String, Setting> settings = settingsByAttribute();
			if (!isInitialized(settings.get(INITIALIZED_SETTING))) {
				importPatternSettings(settings);
				stageAccountIdentifiers(settings);
				saveSetting(settings, INITIALIZED_SETTING, "true", DataType.BOOLEAN, false, false,
						"Legacy-Import der Mustererkennungen wurde ausgefuehrt");
				log.info("Imported legacy booking recognition properties into the tenant database.");
			}
			synchronizeInternal();
		}
	}

	public void synchronize() {
		synchronized (SYNCHRONIZATION_LOCK) {
			synchronizeInternal();
		}
	}

	private void synchronizeInternal() {
		resolvePendingAccountIdentifiers();
		Map<String, Map<String, String>> valuesByFile = createCompatibilityValues();
		try {
			writeCompatibilityFiles(valuesByFile);
			BookingCorePropertiesAdapter.reload(valuesByFile);
		} catch (IOException exception) {
			throw new GBankingException("Import properties could not be synchronized with the database", exception);
		}
	}

	public static boolean isPatternSetting(String attribute) {
		return attribute != null && attribute.startsWith(PATTERN_PREFIX);
	}

	public static String getPatternDisplayAttribute(String attribute) {
		return isPatternSetting(attribute) ? attribute.substring(PATTERN_PREFIX.length()) : attribute;
	}

	private void importPatternSettings(Map<String, Setting> settings) {
		for (PatternFile patternFile : PATTERN_FILES) {
			for (Map.Entry<String, String> entry : readProperties(patternFile.fileName()).entrySet()) {
				String attribute = patternFile.settingPrefix() + entry.getKey();
				Setting existing = settings.get(attribute);
				String comment = existing != null ? existing.getComment() : patternFile.fileName() + ": " + entry.getKey();
				saveSetting(settings, attribute, entry.getValue(), DataType.STRING, true, true, comment);
			}
		}
	}

	private void stageAccountIdentifiers(Map<String, Setting> settings) {
		for (AccountIdentifierType type : AccountIdentifierType.values()) {
			for (Map.Entry<String, String> entry : readProperties(type.getFileName()).entrySet()) {
				String attribute = pendingAttribute(type, entry.getKey());
				saveSetting(settings, attribute, entry.getValue(), DataType.STRING, false, false,
						"Noch nicht zugeordnete Konto-Kennungen fuer " + entry.getKey());
			}
		}
	}

	private Map<String, String> readProperties(String fileName) {
		try {
			return ImportPropertiesFileSupport.read(propertiesDirectory.resolve(fileName));
		} catch (IOException exception) {
			throw new GBankingException("Could not read legacy import properties file " + fileName, exception);
		}
	}

	private void resolvePendingAccountIdentifiers() {
		List<BankAccount> accounts = dbController.getAll(BankAccount.class);
		if (accounts.isEmpty()) {
			return;
		}

		for (Setting setting : dbController.getAll(Setting.class)) {
			resolvePendingAccountIdentifier(accounts, setting);
		}
	}

	private void resolvePendingAccountIdentifier(List<BankAccount> accounts, Setting setting) {
		PendingProperty pending = toPendingProperty(setting);
		if (pending == null) {
			return;
		}
		BankAccount account = findMatchingAccount(accounts, pending);
		if (account == null) {
			return;
		}
		List<BankAccountIdentifier> identifiers = new ArrayList<>(dbController.getBankAccountIdentifiers(account.getId()));
		for (String value : splitValues(setting.getValue())) {
			identifiers.add(new BankAccountIdentifier(0, account.getId(), pending.type(), value));
		}
		dbController.replaceBankAccountIdentifiers(account.getId(), identifiers);
		dbController.delete(setting, null);
	}

	private BankAccount findMatchingAccount(List<BankAccount> accounts, PendingProperty pending) {
		List<BankAccount> nameMatches = accounts.stream()
				.filter(account -> account.getAccountName() != null && account.getAccountName().equalsIgnoreCase(pending.accountName())).toList();
		if (nameMatches.size() == 1) {
			return nameMatches.get(0);
		}
		List<BankAccount> identifierMatches = accounts.stream().filter(account -> splitValues(pending.value()).stream()
				.anyMatch(value -> matchesAccountValue(value, account.getIban()) || matchesAccountValue(value, account.getNumber()))).toList();
		return identifierMatches.size() == 1 ? identifierMatches.get(0) : null;
	}

	private boolean matchesAccountValue(String pendingValue, String accountValue) {
		if (accountValue == null || accountValue.isBlank()) {
			return false;
		}
		String normalized = accountValue.trim();
		return pendingValue.equalsIgnoreCase(normalized) || pendingValue.equalsIgnoreCase(normalized.replaceFirst("^0+(?!$)", ""))
				|| normalized.length() > 10 && pendingValue.equalsIgnoreCase(normalized.substring(normalized.length() - 10));
	}

	private Map<String, Map<String, String>> createCompatibilityValues() {
		Map<String, Map<String, String>> valuesByFile = new LinkedHashMap<>();
		for (PatternFile patternFile : PATTERN_FILES) {
			valuesByFile.put(patternFile.fileName(), new TreeMap<>());
		}
		for (AccountIdentifierType type : AccountIdentifierType.values()) {
			valuesByFile.put(type.getFileName(), new TreeMap<>());
		}

		for (Setting setting : dbController.getAll(Setting.class)) {
			addPatternSetting(valuesByFile, setting);
			addPendingSetting(valuesByFile, setting);
		}
		addPersistedAccountIdentifiers(valuesByFile);
		return valuesByFile;
	}

	private void addPatternSetting(Map<String, Map<String, String>> valuesByFile, Setting setting) {
		for (PatternFile patternFile : PATTERN_FILES) {
			if (setting.getAttribute() != null && setting.getAttribute().startsWith(patternFile.settingPrefix())) {
				String key = setting.getAttribute().substring(patternFile.settingPrefix().length());
				valuesByFile.get(patternFile.fileName()).put(key, setting.getValue() != null ? setting.getValue() : "");
				return;
			}
		}
	}

	private void addPendingSetting(Map<String, Map<String, String>> valuesByFile, Setting setting) {
		PendingProperty pending = toPendingProperty(setting);
		if (pending != null) {
			valuesByFile.get(pending.type().getFileName()).put(pending.accountName(), pending.value());
		}
	}

	private void addPersistedAccountIdentifiers(Map<String, Map<String, String>> valuesByFile) {
		Map<Integer, BankAccount> accountsById = new HashMap<>();
		for (BankAccount account : dbController.getAll(BankAccount.class)) {
			accountsById.put(account.getId(), account);
		}
		for (BankAccountIdentifier identifier : dbController.getAllBankAccountIdentifiers()) {
			BankAccount account = accountsById.get(identifier.accountId());
			if (account != null && account.getAccountName() != null) {
				valuesByFile.get(identifier.propertyType().getFileName()).merge(account.getAccountName(), identifier.value(),
						(left, right) -> mergeValues(left, right));
			}
		}
	}

	private static String mergeValues(String left, String right) {
		Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		values.addAll(splitValues(left));
		values.addAll(splitValues(right));
		return String.join(";", values);
	}

	private void writeCompatibilityFiles(Map<String, Map<String, String>> valuesByFile) throws IOException {
		for (Map.Entry<String, Map<String, String>> entry : valuesByFile.entrySet()) {
			ImportPropertiesFileSupport.write(propertiesDirectory.resolve(entry.getKey()), entry.getValue());
		}
		Path internalDirectory = propertiesDirectory.resolve("intern");
		for (String fileName : List.of("account.properties", "accountTransfer.properties", "accountCancel.properties")) {
			ImportPropertiesFileSupport.write(internalDirectory.resolve(fileName), valuesByFile.get(fileName));
		}
		log.debug("Synchronized booking recognition properties in {}", propertiesDirectory);
	}

	private Map<String, Setting> settingsByAttribute() {
		Map<String, Setting> settings = new LinkedHashMap<>();
		for (Setting setting : dbController.getAll(Setting.class)) {
			settings.put(setting.getAttribute(), setting);
		}
		return settings;
	}

	private void saveSetting(Map<String, Setting> settings, String attribute, String value, DataType dataType, boolean editable, boolean visible,
			String comment) {
		Setting setting = settings.computeIfAbsent(attribute, ImportPropertiesSynchronizationService::createSetting);
		setting.setValue(value);
		setting.setDataType(dataType);
		setting.setEditable(editable);
		setting.setVisible(visible);
		setting.setComment(comment);
		dbController.insertOrUpdate(setting);
	}

	private static Setting createSetting(String attribute) {
		Setting setting = new Setting();
		setting.setAttribute(attribute);
		return setting;
	}

	private boolean isInitialized(Setting setting) {
		return setting != null && Boolean.parseBoolean(setting.getValue());
	}

	private PendingProperty toPendingProperty(Setting setting) {
		String attribute = setting.getAttribute();
		if (attribute == null || !attribute.startsWith(PENDING_PREFIX)) {
			return null;
		}
		String remainder = attribute.substring(PENDING_PREFIX.length());
		int separator = remainder.indexOf('.');
		if (separator <= 0 || separator == remainder.length() - 1) {
			return null;
		}
		try {
			AccountIdentifierType type = AccountIdentifierType.forPropertyValue(remainder.substring(0, separator));
			String accountName = remainder.substring(separator + 1);
			return new PendingProperty(type, accountName, setting.getValue());
		} catch (IllegalArgumentException exception) {
			log.warn("Ignoring invalid pending account identifier setting {}", attribute);
			return null;
		}
	}

	private String pendingAttribute(AccountIdentifierType type, String accountName) {
		return PENDING_PREFIX + type.getPropertyValue() + "." + accountName;
	}

	private static List<String> splitValues(String values) {
		if (values == null || values.isBlank()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String value : values.split(";")) {
			if (!value.isBlank()) {
				result.add(value.trim());
			}
		}
		return result;
	}

	private record PatternFile(String fileName, String settingPrefix) {
	}

	private record PendingProperty(AccountIdentifierType type, String accountName, String value) {
	}
}
