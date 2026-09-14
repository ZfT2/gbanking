package de.zft2.gbanking.hbci;

import java.util.ArrayList;
import java.util.List;

import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;

import org.kapott.hbci.smartcardio.ChipTanCardService;
import org.kapott.hbci.smartcardio.SmartCardService;

import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;

public final class ChipTanUsbSupport {

	public static final String SETTING_ENABLED = "hbci.chiptanusb.enabled";
	public static final String SETTING_READER_NAME = "hbci.chiptanusb.readerName";

	private static final String COMMENT_ENABLED = "chipTAN-USB über Kartenleser für TAN-Eingaben aktivieren";
	private static final String COMMENT_READER_NAME = "Kartenlesername für chipTAN-USB; leer = ersten verfügbaren Leser verwenden";
	private static final SettingDefinition ENABLED = new SettingDefinition(SETTING_ENABLED, "false", DataType.BOOLEAN, true, true,
			COMMENT_ENABLED);
	private static final SettingDefinition READER_NAME = new SettingDefinition(SETTING_READER_NAME, "", DataType.STRING, true, true,
			COMMENT_READER_NAME);

	private ChipTanUsbSupport() {
	}

	public static void ensureSettingsExist() {
		SettingsStore.current().ensure(ENABLED, READER_NAME);
	}

	public static boolean isEnabled() {
		return Boolean.parseBoolean(getSettingValue(SETTING_ENABLED, "false"));
	}

	public static String getConfiguredReaderName() {
		return normalize(getSettingValue(SETTING_READER_NAME, ""));
	}

	public static boolean isChipTanPayload(String payload) {
		String normalizedPayload = normalize(payload);
		return !normalizedPayload.isBlank() && normalizedPayload.length() % 2 == 0 && normalizedPayload.matches("[0-9A-Fa-f]+");
	}

	public static String requestTan(String payload) {
		String readerName = getConfiguredReaderName();
		ChipTanCardService service = SmartCardService.createInstance(ChipTanCardService.class, readerName.isBlank() ? null : readerName);
		return service.getTan(payload);
	}

	public static List<String> getAvailableReaders() {
		List<String> result = new ArrayList<>();
		try {
			CardTerminals terminals = TerminalFactory.getDefault().terminals();
			if (terminals == null) {
				return result;
			}
			for (CardTerminal terminal : terminals.list()) {
				if (terminal != null && terminal.getName() != null && !terminal.getName().isBlank()) {
					result.add(terminal.getName());
				}
			}
		} catch (Exception ignored) {
			// Kartenleser sind optional; ohne Enumeration bleibt die freie Eingabe möglich.
		}
		return result;
	}

	private static String getSettingValue(String attribute, String defaultValue) {
		return SettingsStore.current().getString(attribute, defaultValue);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
