package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.util.TypeConverter;

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
		ps.setInt(9, bankAccess.getTanProcedure().getDbStateId());
		ps.setString(10, TypeConverter.toCommaSeparatedString(bankAccess.getAllowedTwostepMechanisms()));
		ps.setString(11, bankAccess.getHbciVersion());
		ps.setString(12, bankAccess.getBpdVersion());
		ps.setString(13, bankAccess.getUpdVersion());
		ps.setInt(14, bankAccess.getFilterType().getDbStateId());
		ps.setBoolean(15, bankAccess.isActive());
		ps.setTimestamp(16, TypeConverter.toSqlTimestampNow());
		ps.setInt(17, bankAccess.getAccessType().getDbStateId());
		ps.setString(18, bankAccess.getPaypalApiUsername());
		ps.setString(19, bankAccess.getPaypalApiSignature());
		if (bankAccess.getId() > 0) {
			ps.setInt(20, bankAccess.getId());
		}
	}

	@Override
	public void setParamsDelete(BankAccess bankAccess, PreparedStatement ps) throws SQLException {
		ps.setInt(1, bankAccess.getId());
	}

	@Override
	public void mapDao(BankAccess access, ResultType resultType, ResultSet rs) throws SQLException {
		access.setBankName(rs.getString(SqlFields.BANKNAME));
		access.setCountry(rs.getString("country"));
		access.setBlz(rs.getString("blz"));
		access.setHbciURL(rs.getString("hbciURL"));
		access.setPort(rs.getInt("port"));
		access.setUserId(rs.getString("userId"));
		access.setCustomerId(rs.getString("customerId"));
		access.setSysId(rs.getString("sysId"));
		access.setTanProcedure(TanProcedure.forInt(rs.getInt("tanProcedure")));
		access.setAllowedTwostepMechanisms(TypeConverter.toList(rs.getString("allowedTwostepMechanisms")));
		access.setHbciVersion(rs.getString("hbciVersion"));
		access.setBpdVersion(rs.getString("bpdVersion"));
		access.setUpdVersion(rs.getString("updVersion"));
		access.setFilterType(HbciEncodingFilterType.forInt(rs.getInt("hbciFilterType")));
		access.setActive(rs.getBoolean("active"));
		access.setAccessType(BankAccessType.forInt(rs.getInt("accessType")));
		access.setPaypalApiUsername(rs.getString("paypalApiUsername"));
		access.setPaypalApiSignature(rs.getString("paypalApiSignature"));
	}

}
