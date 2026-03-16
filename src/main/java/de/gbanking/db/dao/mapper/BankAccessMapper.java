package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.SqlFields;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.gbanking.db.dao.enu.TanProcedure;
import de.gbanking.util.TypeConverter;

public class BankAccessMapper extends AbstractDaoMapper<BankAccess, Void> {

	@Override
	public void setParamsFull(BankAccess bankAccess, PreparedStatement ps) throws SQLException {
		ps.setString(1, bankAccess.getBankName());
		ps.setString(2, bankAccess.getCountry());
		ps.setString(3, bankAccess.getBlz());
		ps.setString(4, bankAccess.getHbciURL());
		ps.setInt(5, bankAccess.getPort());
		ps.setString(6, bankAccess.getUserId());
		ps.setString(7, bankAccess.getCustomerId());
		ps.setString(8, bankAccess.getSysId());
		ps.setString(9, bankAccess.getTanProcedure().name());
		ps.setString(10, TypeConverter.toCommaSeparatedString(bankAccess.getAllowedTwostepMechanisms()));
		ps.setString(11, bankAccess.getHbciVersion());
		ps.setString(12, bankAccess.getBpdVersion());
		ps.setString(13, bankAccess.getUpdVersion());
		ps.setString(14, bankAccess.getFilterType().name());
		ps.setBoolean(15, bankAccess.isActive());
		ps.setDate(16, TypeConverter.toSqlDateNow());
		if (bankAccess.getId() > 0) {
			ps.setInt(17, bankAccess.getId());
		}
	}

	@Override
	public void setParamsDelete(BankAccess bankAccess, PreparedStatement ps) throws SQLException {
		ps.setString(1, bankAccess.getBlz());
	}

	@Override
	public void mapDao(BankAccess access, ResultSet rs) throws SQLException {
		if (access == null)
			access = new BankAccess();
		super.mapDao(access, rs);
		access.setBankName(rs.getString(SqlFields.BANKNAME));
		access.setCountry(rs.getString("country"));
		access.setBlz(rs.getString("blz"));
		access.setHbciURL(rs.getString("hbciURL"));
		access.setPort(rs.getInt("port"));
		access.setUserId(rs.getString("userId"));
		access.setCustomerId(rs.getString("customerId"));
		access.setSysId(rs.getString("sysId"));
		access.setTanProcedure(TanProcedure.valueOf(rs.getString("tanProcedure")));
		access.setAllowedTwostepMechanisms(TypeConverter.toList(rs.getString("allowedTwostepMechanisms")));
		access.setHbciVersion(rs.getString("hbciVersion"));
		access.setBpdVersion(rs.getString("bpdVersion"));
		access.setUpdVersion(rs.getString("updVersion"));
		access.setFilterType(HbciEncodingFilterType.valueOf(rs.getString("hbciFilterType")));
		access.setActive(rs.getBoolean("active"));
	}

}
