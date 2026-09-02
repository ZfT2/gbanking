package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ParameterData;
import de.zft2.gbanking.db.dao.enu.ParameterDataType;
import de.zft2.gbanking.util.TypeConverter;

public class ParameterDataMapper extends AbstractDaoMapper<ParameterData, Void> {

	public ParameterDataMapper() {
		super(ParameterData::new);
	}

	@Override
	public void setParamsFull(ParameterData parameterData, PreparedStatement ps) throws SQLException {
		ps.setString(1, parameterData.getPdKey());
		ps.setInt(2, parameterData.getPdType().getDbStateId());
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(ParameterData pd, ResultType resultType, ResultSet rs) throws SQLException {
		pd.setPdKey(rs.getString("pdKey"));
		ParameterDataType typ = ParameterDataType.forInt(rs.getInt("pdType"));
		pd.setPdType(typ);
	}

}
