package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Setting;
import de.gbanking.db.enu.DataType;
import de.gbanking.util.TypeConverter;

public class SettingMapper extends AbstractDaoMapper<Setting, Void> {

	@Override
	public void setParamsFull(Setting setting, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setString(index++, setting.getAttribute());
		ps.setString(index++, setting.getValue());
		ps.setInt(index++, setting.getDataType().getDbStateId());
		ps.setBoolean(index++, setting.isEditable());
		ps.setBoolean(index++, setting.isVisible());
		ps.setString(index++, setting.getComment());
		ps.setDate(index++, TypeConverter.toSqlDateNow());

		if (setting.getId() > 0) {
			ps.setInt(index, setting.getId());
		}

	}

	@Override
	public void mapDao(Setting setting, ResultType resultType, ResultSet rs) throws SQLException {
		setting.setAttribute(rs.getString("attribute"));
		setting.setValue(rs.getString("value"));
		setting.setDataType((DataType) DataType.forInt(rs.getInt("dataType")));
		setting.setEditable(rs.getBoolean("editable"));
		setting.setVisible(rs.getBoolean("visible"));
		setting.setComment(rs.getString("comment"));
	}

}
