package de.zft2.gbanking.service.account;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;

public final class AccountStatementSettings {

	public static final String SETTING_AUTO_ACKNOWLEDGE = "accountstatement.auto.acknowledge";
	public static final String SETTING_REDOWNLOAD_ACKNOWLEDGED = "accountstatement.redownload.acknowledged";
	public static final String SETTING_DOWNLOAD_OVERVIEW = "accountstatement.download.overview";
	public static final String SETTING_ENCRYPT_FILES = "accountstatement.encrypt.files";

	private static final String COMMENT_AUTO_ACKNOWLEDGE = "Kontoauszuege nach erfolgreichem Speichern automatisch bei der Bank quittieren";
	private static final String COMMENT_REDOWNLOAD_ACKNOWLEDGED = "Bereits quittierte Kontoauszuege per Jahr und Auszugsnummer erneut abrufen";
	private static final String COMMENT_DOWNLOAD_OVERVIEW = "Uebersicht der Kontoauszuege per HKKAU vor dem Abruf laden";
	private static final String COMMENT_ENCRYPT_FILES = "Kontoauszugsdateien mit dem Mandantenschluessel verschluesselt speichern";

	private AccountStatementSettings() {
	}

	public static void ensureSettingsExist() {
		DBController dbController = DBController.getInstance(".");
		List<Setting> settings = dbController.getAll(Setting.class);
		ensureSetting(dbController, settings, SETTING_AUTO_ACKNOWLEDGE, true, COMMENT_AUTO_ACKNOWLEDGE);
		ensureSetting(dbController, settings, SETTING_REDOWNLOAD_ACKNOWLEDGED, false, COMMENT_REDOWNLOAD_ACKNOWLEDGED);
		ensureSetting(dbController, settings, SETTING_DOWNLOAD_OVERVIEW, false, COMMENT_DOWNLOAD_OVERVIEW);
		ensureSetting(dbController, settings, SETTING_ENCRYPT_FILES, false, COMMENT_ENCRYPT_FILES);
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

	private static void ensureSetting(DBController dbController, List<Setting> settings, String attribute, boolean defaultValue, String comment) {
		boolean exists = settings != null && settings.stream().anyMatch(setting -> attribute.equals(setting.getAttribute()));
		if (exists) {
			return;
		}

		Setting setting = new Setting();
		setting.setAttribute(attribute);
		setting.setValue(Boolean.toString(defaultValue));
		setting.setDataType(DataType.BOOLEAN);
		setting.setEditable(true);
		setting.setVisible(true);
		setting.setComment(comment);
		setting.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		dbController.insertOrUpdate(setting);
	}

	private static boolean getBooleanSetting(String attribute, boolean defaultValue) {
		ensureSettingsExist();
		return DBController.getInstance(".").getAll(Setting.class).stream()
				.filter(setting -> attribute.equals(setting.getAttribute()))
				.map(setting -> Boolean.parseBoolean(setting.getValue()))
				.findFirst()
				.orElse(defaultValue);
	}
}
