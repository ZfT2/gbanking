package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import de.gbanking.db.dao.ParameterData;
import de.gbanking.db.dao.enu.ParameterDataType;
import de.gbanking.util.TypeConverter;

public class ParameterDataMapper extends AbstractDaoMapper<ParameterData, Void> {

	@Override
	public void setParamsFull(ParameterData parameterData, PreparedStatement ps) throws SQLException {
		ps.setString(2, parameterData.getPdKey());
		ps.setString(3, parameterData.getPdType().name());
		ps.setDate(4, TypeConverter.toSqlDateNow());
	}

	@Override
	public void setParamsFull(Set<ParameterData> entitySet, PreparedStatement ps) throws SQLException {
		Iterator<ParameterData> parameterDataIterator = entitySet.iterator();
		while (parameterDataIterator.hasNext()) {
			ParameterData parameterData = parameterDataIterator.next();
			setParamsFull(parameterData, ps);
			ps.addBatch();
		}
	}

	@Override
	public void mapDao(ParameterData pd, ResultSet rs) throws SQLException {
		if (pd == null)
			pd = new ParameterData();
		super.mapDao(pd, rs);
		pd.setPdKey(rs.getString("pdKey"));
		ParameterDataType typ = ParameterDataType.valueOf(rs.getString("pdType"));
		pd.setPdType(typ);
	}

}
