package de.zft2.gbanking.file.imp;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.DataType;

public final class FileImportSettings {

	public static final String SETTING_IMPORT_EMPTY_XML_ACCOUNTS = "import.xml.emptyAccounts";
	private static final boolean DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS = true;
	private static final String COMMENT_IMPORT_EMPTY_XML_ACCOUNTS = "Auch leere Konten aus XML-Importdateien importieren";

	private FileImportSettings() {
	}

	public static void ensureSettingsExist() {
		DBController dbController = DBController.getInstance(".");
		List<Setting> settings = dbController.getAll(Setting.class);
		if (settings != null && settings.stream().anyMatch(setting -> SETTING_IMPORT_EMPTY_XML_ACCOUNTS.equals(setting.getAttribute()))) {
			return;
		}

		Setting setting = new Setting();
		setting.setAttribute(SETTING_IMPORT_EMPTY_XML_ACCOUNTS);
		setting.setValue(Boolean.toString(DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS));
		setting.setDataType(DataType.BOOLEAN);
		setting.setEditable(true);
		setting.setVisible(true);
		setting.setComment(COMMENT_IMPORT_EMPTY_XML_ACCOUNTS);
		setting.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		dbController.insertOrUpdate(setting);
	}

	public static boolean isEmptyXmlAccountImportEnabled() {
		ensureSettingsExist();
		List<Setting> settings = DBController.getInstance(".").getAll(Setting.class);
		if (settings == null) {
			return DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS;
		}
		return settings.stream()
				.filter(setting -> SETTING_IMPORT_EMPTY_XML_ACCOUNTS.equals(setting.getAttribute()))
				.map(setting -> Boolean.parseBoolean(setting.getValue()))
				.findFirst()
				.orElse(DEFAULT_IMPORT_EMPTY_XML_ACCOUNTS);
	}
}
