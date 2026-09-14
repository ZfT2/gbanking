package de.zft2.gbanking.file.imp;

import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;

public final class FileImportSettings {

	public static final String SETTING_IMPORT_EMPTY_XML_ACCOUNTS = "import.xml.emptyAccounts";
	private static final boolean DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS = true;
	private static final String COMMENT_IMPORT_EMPTY_XML_ACCOUNTS = "Auch leere Konten aus XML-Importdateien importieren";
	private static final SettingDefinition IMPORT_EMPTY_XML_ACCOUNTS = new SettingDefinition(SETTING_IMPORT_EMPTY_XML_ACCOUNTS,
			Boolean.toString(DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS), DataType.BOOLEAN, true, true, COMMENT_IMPORT_EMPTY_XML_ACCOUNTS);

	private FileImportSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore.current().ensure(IMPORT_EMPTY_XML_ACCOUNTS);
	}

	public static boolean isEmptyXmlAccountImportEnabled() {
		ensureSettingsExist();
		return SettingsStore.current().getBoolean(SETTING_IMPORT_EMPTY_XML_ACCOUNTS, DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS);
	}
}
