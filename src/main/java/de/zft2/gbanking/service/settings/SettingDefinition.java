package de.zft2.gbanking.service.settings;

import java.util.Objects;

import de.zft2.gbanking.db.dao.enu.DataType;

public record SettingDefinition(String attribute, String defaultValue, DataType dataType,
		boolean editable, boolean visible, String comment) {

	public SettingDefinition {
		Objects.requireNonNull(attribute, "attribute");
		Objects.requireNonNull(dataType, "dataType");
	}
}
