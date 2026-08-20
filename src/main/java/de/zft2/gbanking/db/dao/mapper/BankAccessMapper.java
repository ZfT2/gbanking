package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.BankAccessPaypal;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.util.TypeConverter;

public class BankAccessMapper extends AbstractDaoMapper<BankAccess, Void> {

	@Override
	public void setParamsFull(BankAccess bankAccess, PreparedStatement ps) throws SQLException {
		ps.setString(1, bankAccess.getBankName());
		ps.setBoolean(2, bankAccess.isActive());
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
		ps.setInt(4, bankAccess.getAccessType().getDbStateId());
		if (bankAccess.getId() > 0) {
			ps.setInt(5, bankAccess.getId());
		}
	}

	@Override
	public void setParamsDelete(BankAccess bankAccess, PreparedStatement ps) throws SQLException {
		ps.setInt(1, bankAccess.getId());
	}

	@Override
	public void mapDao(BankAccess access, ResultType resultType, ResultSet rs) throws SQLException {
		access.setBankName(rs.getString(SqlFields.BANKNAME));
		access.setActive(rs.getBoolean("active"));
		BankAccessType accessType = BankAccessType.forInt(rs.getInt("accessType"));
		access.setAccessType(accessType);
		switch (accessType) {
		case HBCI -> access.setFints(mapFints(rs));
		case PAYPAL -> access.setPaypal(mapPaypal(rs));
		case ENABLEBANKING -> access.setEnablebanking(mapEnablebanking(rs));
		}
	}

	private BankAccessFints mapFints(ResultSet rs) throws SQLException {
		BankAccessFints fints = new BankAccessFints();
		fints.setCountry(rs.getString("fintsCountry"));
		fints.setBlz(rs.getString("fintsBlz"));
		fints.setHbciURL(rs.getString("hbciURL"));
		fints.setPort(getIntegerNullable("port", rs));
		fints.setUserId(rs.getString("fintsUserId"));
		fints.setCustomerId(rs.getString("customerId"));
		fints.setSysId(rs.getString("sysId"));
		fints.setTanProcedure(TanProcedure.forInt(rs.getInt("tanProcedure")));
		fints.setAllowedTwostepMechanisms(TypeConverter.toList(rs.getString("allowedTwostepMechanisms")));
		fints.setHbciVersion(rs.getString("hbciVersion"));
		fints.setBpdVersion(rs.getString("bpdVersion"));
		fints.setUpdVersion(rs.getString("updVersion"));
		fints.setFilterType(HbciEncodingFilterType.forInt(rs.getInt("hbciFilterType")));
		return fints;
	}

	private BankAccessPaypal mapPaypal(ResultSet rs) throws SQLException {
		BankAccessPaypal paypal = new BankAccessPaypal();
		paypal.setUserId(rs.getString("paypalUserId"));
		paypal.setApiUsername(rs.getString("paypalApiUsername"));
		paypal.setApiSignature(rs.getString("paypalApiSignature"));
		return paypal;
	}

	private BankAccessEnablebanking mapEnablebanking(ResultSet rs) throws SQLException {
		BankAccessEnablebanking enablebanking = new BankAccessEnablebanking();
		enablebanking.setPsd2ClientConfigurationId(rs.getInt("psd2ClientConfiguration_id"));
		enablebanking.setAspspName(rs.getString("aspspName"));
		enablebanking.setAspspCountry(rs.getString("aspspCountry"));
		enablebanking.setPsuType(rs.getString("psuType"));
		enablebanking.setAuthMethod(rs.getString("authMethod"));
		enablebanking.setSessionId(rs.getString("sessionId"));
		enablebanking.setValidUntil(parseDateTime(rs.getString("validUntil")));
		enablebanking.setRateLimitUntil(parseDateTime(rs.getString("rateLimitUntil")));
		return enablebanking;
	}

	private OffsetDateTime parseDateTime(String value) {
		return value != null ? OffsetDateTime.parse(value) : null;
	}

}
