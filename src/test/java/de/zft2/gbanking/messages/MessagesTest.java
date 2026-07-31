package de.zft2.gbanking.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessagesTest {
	private static final Pattern MESSAGE_PARAMETER = Pattern.compile("\\{\\d+}");

	private Locale previousLocale;

	@BeforeEach
	void rememberLocale() {
		previousLocale = Messages.getLocale();
	}

	@AfterEach
	void restoreLocale() {
		Messages.setLocale(previousLocale);
	}

	@Test
	void shouldUseEnglishBundleWhenEnglishLocaleIsSelected() {
		Messages.setLocale(Locale.ENGLISH);

		assertEquals("Select tenant", Messages.getInstance().getMessage("UI_DIALOG_TENANT_SELECTION_TITLE"));
	}

	@Test
	void shouldUseGermanBundleWhenGermanLocaleIsSelected() {
		Messages.setLocale(Locale.GERMAN);

		assertEquals("Mandant auswählen", Messages.getInstance().getMessage("UI_DIALOG_TENANT_SELECTION_TITLE"));
	}

	@Test
	void germanAndEnglishBundlesShouldContainTheSameUniqueKeys() throws IOException {
		List<String> germanKeys = loadKeys("/messages_de.properties");
		List<String> englishKeys = loadKeys("/messages_en.properties");

		assertEquals(germanKeys.size(), new HashSet<>(germanKeys).size(), "German message keys must be unique");
		assertEquals(englishKeys.size(), new HashSet<>(englishKeys).size(), "English message keys must be unique");
		assertEquals(germanKeys, englishKeys);
	}

	@Test
	void germanAndEnglishMessagesShouldUseTheSameParameters() throws IOException {
		Map<String, String> germanMessages = loadMessages("/messages_de.properties");
		Map<String, String> englishMessages = loadMessages("/messages_en.properties");

		for (Map.Entry<String, String> germanMessage : germanMessages.entrySet()) {
			assertEquals(parameters(germanMessage.getValue()), parameters(englishMessages.get(germanMessage.getKey())), germanMessage.getKey());
		}
	}

	private List<String> loadKeys(String resourceName) throws IOException {
		InputStream inputStream = MessagesTest.class.getResourceAsStream(resourceName);
		assertNotNull(inputStream);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
					.map(line -> line.substring(0, line.indexOf('=')).trim()).toList();
		}
	}

	private Map<String, String> loadMessages(String resourceName) throws IOException {
		InputStream inputStream = MessagesTest.class.getResourceAsStream(resourceName);
		assertNotNull(inputStream);
		Map<String, String> messagesByKey = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			for (String line : reader.lines().filter(value -> !value.isBlank() && !value.startsWith("#") && value.contains("=")).toList()) {
				int separatorIndex = line.indexOf('=');
				messagesByKey.put(line.substring(0, separatorIndex).trim(), line.substring(separatorIndex + 1));
			}
		}
		return messagesByKey;
	}

	private Set<String> parameters(String message) {
		Set<String> parameters = new HashSet<>();
		Matcher matcher = MESSAGE_PARAMETER.matcher(message);
		while (matcher.find()) {
			parameters.add(matcher.group());
		}
		return parameters;
	}
}
