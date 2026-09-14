package de.zft2.gbanking.service.account;

import de.zft2.gbanking.db.dao.enu.DataType;
import de.zft2.gbanking.service.settings.SettingDefinition;
import de.zft2.gbanking.service.settings.SettingsStore;

public final class AccountStatementSettings {

	public static final String SETTING_AUTO_ACKNOWLEDGE = "accountstatement.auto.acknowledge";
	public static final String SETTING_REDOWNLOAD_ACKNOWLEDGED = "accountstatement.redownload.acknowledged";
	public static final String SETTING_DOWNLOAD_OVERVIEW = "accountstatement.download.overview";
	public static final String SETTING_ENCRYPT_FILES = "accountstatement.encrypt.files";

	private static final String COMMENT_AUTO_ACKNOWLEDGE = "Kontoauszuege nach erfolgreichem Speichern automatisch bei der Bank quittieren";
	private static final String COMMENT_REDOWNLOAD_ACKNOWLEDGED = "Bereits quittierte Kontoauszuege per Jahr und Auszugsnummer erneut abrufen";
	private static final String COMMENT_DOWNLOAD_OVERVIEW = "Uebersicht der Kontoauszuege per HKKAU vor dem Abruf laden";
	private static final String COMMENT_ENCRYPT_FILES = "Kontoauszugsdateien mit dem Mandantenschluessel verschluesselt speichern";
	private static final SettingDefinition AUTO_ACKNOWLEDGE = booleanSetting(SETTING_AUTO_ACKNOWLEDGE, true, COMMENT_AUTO_ACKNOWLEDGE);
	private static final SettingDefinition REDOWNLOAD_ACKNOWLEDGED = booleanSetting(SETTING_REDOWNLOAD_ACKNOWLEDGED, false,
			COMMENT_REDOWNLOAD_ACKNOWLEDGED);
	private static final SettingDefinition DOWNLOAD_OVERVIEW = booleanSetting(SETTING_DOWNLOAD_OVERVIEW, false, COMMENT_DOWNLOAD_OVERVIEW);
	private static final SettingDefinition ENCRYPT_FILES = booleanSetting(SETTING_ENCRYPT_FILES, false, COMMENT_ENCRYPT_FILES);

	private AccountStatementSettings() {
	}

	public static void ensureSettingsExist() {
		SettingsStore.current().ensure(AUTO_ACKNOWLEDGE, REDOWNLOAD_ACKNOWLEDGED, DOWNLOAD_OVERVIEW, ENCRYPT_FILES);
	}

	public static boolean isAutoAcknowledgeEnabled() {
		return getBooleanSetting(SETTING_AUTO_ACKNOWLEDGE, true);
	}

	public static boolean isRedownloadAcknowledgedEnabled() {
		return getBooleanSetting(SETTING_REDOWNLOAD_ACKNOWLEDGED, false);
	}

	public static boolean isDownloadOverviewEnabled() {
		return getBooleanSetting(SETTING_DOWNLOAD_OVERVIEW, false);
	}

	public static boolean isFileEncryptionEnabled() {
		return getBooleanSetting(SETTING_ENCRYPT_FILES, false);
	}

	private static boolean getBooleanSetting(String attribute, boolean defaultValue) {
		ensureSettingsExist();
		return SettingsStore.current().getBoolean(attribute, defaultValue);
	}

	private static SettingDefinition booleanSetting(String attribute, boolean defaultValue, String comment) {
		return new SettingDefinition(attribute, Boolean.toString(defaultValue), DataType.BOOLEAN, true, true, comment);
	}
}
