package de.zft2.gbanking.service.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Setting;

public final class SettingsStore {

	private final DBController dbController;

	public SettingsStore(DBController dbController) {
		this.dbController = Objects.requireNonNull(dbController, "dbController");
	}

	public static SettingsStore current() {
		return new SettingsStore(DBController.getInstance("."));
	}

	public List<Setting> getAll() {
		List<Setting> settings = dbController.getAll(Setting.class);
		return settings != null ? settings : List.of();
	}

	public Optional<Setting> find(String attribute) {
		return getAll().stream()
				.filter(setting -> attribute.equals(setting.getAttribute()))
				.findFirst();
	}

	public String getString(String attribute, String defaultValue) {
		return find(attribute).map(setting -> setting.getValue()).orElse(defaultValue);
	}

	public boolean getBoolean(String attribute, boolean defaultValue) {
		return find(attribute)
				.map(setting -> Boolean.parseBoolean(setting.getValue()))
				.orElse(defaultValue);
	}

	public void ensure(SettingDefinition... definitions) {
		Map<String, Setting> settingsByAttribute = new LinkedHashMap<>();
		for (Setting setting : getAll()) {
			settingsByAttribute.put(setting.getAttribute(), setting);
		}
		for (SettingDefinition definition : definitions) {
			if (!settingsByAttribute.containsKey(definition.attribute())) {
				Setting setting = save(new Setting(), definition, definition.defaultValue());
				settingsByAttribute.put(definition.attribute(), setting);
			}
		}
	}

	public Setting save(SettingDefinition definition, String value) {
		return save(find(definition.attribute()).orElseGet(() -> new Setting()), definition, value);
	}

	public Setting save(Setting setting, SettingDefinition definition, String value) {
		Objects.requireNonNull(setting, "setting");
		Objects.requireNonNull(definition, "definition");
		setting.setAttribute(definition.attribute());
		setting.setValue(value);
		setting.setDataType(definition.dataType());
		setting.setEditable(definition.editable());
		setting.setVisible(definition.visible());
		setting.setComment(definition.comment());
		Setting savedSetting = dbController.insertOrUpdate(setting);
		return savedSetting != null ? savedSetting : setting;
	}
}
