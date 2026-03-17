package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Institute;
import de.gbanking.db.dao.enu.InstituteStatus;
import de.gbanking.util.TypeConverter;

public class InstituteMapper extends AbstractDaoMapper<Institute, Void> {

	@Override
	public void setParamsFull(Institute institute, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, institute.getImportNumber());
		ps.setString(index++, institute.getBlz());
		ps.setString(index++, institute.getBic());
		ps.setString(index++, institute.getBankName());
		ps.setString(index++, institute.getPlace());
		ps.setString(index++, institute.getDataCenter());
		ps.setString(index++, institute.getOrganisation());
		ps.setString(index++, institute.getHbciDns());
		ps.setString(index++, institute.getHbciIp());
		index = setDoubleNullable(index, institute.getHbciVersion(), ps);
		ps.setString(index++, institute.getDdv());
		index = setBooleanNullable(index, institute.getRdh1(), ps);
		index = setBooleanNullable(index, institute.getRdh2(), ps);
		index = setBooleanNullable(index, institute.getRdh3(), ps);
		index = setBooleanNullable(index, institute.getRdh4(), ps);
		index = setBooleanNullable(index, institute.getRdh5(), ps);
		index = setBooleanNullable(index, institute.getRdh6(), ps);
		index = setBooleanNullable(index, institute.getRdh7(), ps);
		index = setBooleanNullable(index, institute.getRdh8(), ps);
		index = setBooleanNullable(index, institute.getRdh9(), ps);
		index = setBooleanNullable(index, institute.getRdh10(), ps);
		ps.setString(index++, institute.getPinUrl());
		ps.setString(index++, institute.getVersion());
		index = setDateNullable(index, TypeConverter.toSqlDateShort(institute.getLastChanged()), ps);
		ps.setInt(index++, institute.getStateType().getDbStateId());
		ps.setDate(index++, TypeConverter.toSqlDateNow());
		
		if (institute.getId() > 0) {
			ps.setInt(index, institute.getId());
		}

	}

	@Override
	public void mapDao(Institute institute, ResultType resultType, ResultSet rs) throws SQLException {
		super.mapDao(institute, resultType, rs);
		institute.setImportNumber(rs.getInt("importNumber"));
		institute.setBlz(rs.getString("blz"));
		institute.setBic(rs.getString("bic"));
		institute.setBankName(rs.getString("bankName"));
		institute.setPlace(rs.getString("place"));
		institute.setDataCenter(rs.getString("dataCenter"));
		institute.setOrganisation(rs.getString("organisation"));
		institute.setHbciDns(rs.getString("hbciDns"));
		institute.setHbciIp(rs.getString("hbciIp"));
		institute.setHbciVersion(getDoubleNullable("hbciVersion", rs));
		institute.setDdv(rs.getString("ddv"));
		
		institute.setRdh1(getBooleanNullable("rdh1", rs));
		institute.setRdh2(getBooleanNullable("rdh2", rs));
		institute.setRdh3(getBooleanNullable("rdh3", rs));
		institute.setRdh4(getBooleanNullable("rdh4", rs));
		institute.setRdh5(getBooleanNullable("rdh5", rs));
		institute.setRdh6(getBooleanNullable("rdh6", rs));
		institute.setRdh7(getBooleanNullable("rdh7", rs));
		institute.setRdh8(getBooleanNullable("rdh8", rs));
		institute.setRdh9(getBooleanNullable("rdh9", rs));
		institute.setRdh10(getBooleanNullable("rdh10", rs));
		
		institute.setPinUrl(rs.getString("pinUrl"));
		institute.setVersion(rs.getString("version"));
		institute.setLastChanged(TypeConverter.toCalendarFromSqlDate(rs.getDate("lastChanged")));
		institute.setStateType(InstituteStatus.forInt(rs.getInt("stateType")));
	}

}
