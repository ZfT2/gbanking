package de.zft2.gbanking.service.importproperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import de.zft2.core.config.ImportProperties;
import de.zft2.core.exception.ConfigurationException;
import de.zft2.core.process.BookingProcessor;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;

final class BookingCorePropertiesAdapter extends BookingProcessor<Booking, BankAccount> {

	private BookingCorePropertiesAdapter() throws ConfigurationException {
		super();
	}

	static void reload(Map<String, Map<String, String>> valuesByFile) throws ConfigurationException {
		propsTransfer = replace("accountTransfer.properties", true, valuesByFile.get("accountTransfer.properties"));
		propsAccount = replace("account.properties", true, valuesByFile.get("account.properties"));
		propsSkip = replace("accountSkip.properties", false, valuesByFile.get("accountSkip.properties"));
		replace("bookings.properties", false, valuesByFile.get("bookings.properties"));
		replace("accountCancel.properties", true, valuesByFile.get("accountCancel.properties"));
		accountNumbersMap = createAccountNumbersMap(propsTransfer);
	}

	private static ImportProperties replace(String fileName, boolean internal, Map<String, String> values) throws ConfigurationException {
		ImportProperties properties = ImportProperties.getInstance(fileName, internal);
		properties.clear();
		properties.putAll(values);
		return properties;
	}

	private static Map<String, Collection<String>> createAccountNumbersMap(Properties properties) {
		Map<String, Collection<String>> identifiersByAccount = new HashMap<>();
		for (String accountName : properties.stringPropertyNames()) {
			identifiersByAccount.put(accountName, new ArrayList<>(List.of(properties.getProperty(accountName).split(";"))));
		}
		return identifiersByAccount;
	}
}
