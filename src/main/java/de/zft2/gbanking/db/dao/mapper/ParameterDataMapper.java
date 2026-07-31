package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.enu.ParameterDataType;
import de.zft2.gbanking.util.TypeConverter;

public class ParameterDataMapper extends AbstractDaoMapper<ParameterData, Void> {

	@Override
	public void setParamsFull(ParameterData parameterData, PreparedStatement ps) throws SQLException {
		ps.setString(2, parameterData.getPdKey());
		ps.setInt(3, parameterData.getPdType().getDbStateId());
		ps.setTimestamp(4, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(ParameterData pd, ResultType resultType, ResultSet rs) throws SQLException {
		pd.setPdKey(rs.getString("pdKey"));
		ParameterDataType typ = ParameterDataType.forInt(rs.getInt("pdType"));
		pd.setPdType(typ);
	}

}
