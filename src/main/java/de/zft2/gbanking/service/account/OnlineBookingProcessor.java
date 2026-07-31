package de.zft2.gbanking.service.account;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import de.zft2.core.exception.ConfigurationException;
import de.zft2.core.process.BookingProcessor;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.service.importproperties.ImportPropertiesFileSupport;
import de.zft2.gbanking.util.AppPaths;

class OnlineBookingProcessor extends BookingProcessor<Booking, BankAccount> {

	private static final String TRANSFER_PROPERTIES_FILE_NAME = "accountTransfer.properties";

	OnlineBookingProcessor() throws ConfigurationException {
		super();
	}

	static void generateCrossBookingsForOnline(List<BankAccount> accounts) throws ConfigurationException {
		CoreTransferStateSnapshot coreSnapshot = snapshotCoreTransferState();
		try {
			propsTransfer = loadPersistentTransferProperties();
			accountNumbersMap = createAccountNumbersMap(propsTransfer);
			new OnlineBookingProcessor().generateCrossBookings(accounts, 6, null);
		} finally {
			restoreCoreTransferState(coreSnapshot);
		}
	}

	private static CoreTransferStateSnapshot snapshotCoreTransferState() {
		return new CoreTransferStateSnapshot(copyProperties(propsTransfer), copyAccountNumbersMap(accountNumbersMap));
	}

	private static void restoreCoreTransferState(CoreTransferStateSnapshot snapshot) {
		propsTransfer = snapshot.propsTransfer();
		accountNumbersMap = snapshot.accountNumbersMap();
	}

	private static Properties loadPersistentTransferProperties() {
		Path propertiesDirectory = AppPaths.getImportPropertiesDirectory();
		Path configuredPath = propertiesDirectory.resolve(TRANSFER_PROPERTIES_FILE_NAME);
		if (Files.isRegularFile(configuredPath)) {
			return loadProperties(configuredPath);
		}
		Path internalPath = propertiesDirectory.resolve("intern").resolve(TRANSFER_PROPERTIES_FILE_NAME);
		if (Files.isRegularFile(internalPath)) {
			return loadProperties(internalPath);
		}
		return new Properties();
	}

	private static Properties loadProperties(Path path) {
		Properties properties = new Properties();
		try {
			properties.putAll(ImportPropertiesFileSupport.read(path));
			return properties;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read booking-core transfer properties file " + path, e);
		}
	}

	private static Map<String, Collection<String>> createAccountNumbersMap(Properties properties) {
		Map<String, Collection<String>> accountNumbersByName = new HashMap<>();
		for (String accountName : properties.stringPropertyNames()) {
			accountNumbersByName.put(accountName, List.of(properties.getProperty(accountName).split(";")));
		}
		return accountNumbersByName;
	}

	private static Properties copyProperties(Properties properties) {
		if (properties == null) {
			return new Properties();
		}
		Properties copy = new Properties();
		copy.putAll(properties);
		return copy;
	}

	private static Map<String, Collection<String>> copyAccountNumbersMap(Map<String, Collection<String>> source) {
		if (source == null) {
			return new HashMap<>();
		}
		Map<String, Collection<String>> copy = new HashMap<>();
		for (Map.Entry<String, Collection<String>> entry : source.entrySet()) {
			copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return copy;
	}

	private record CoreTransferStateSnapshot(Properties propsTransfer, Map<String, Collection<String>> accountNumbersMap) {
	}
}
