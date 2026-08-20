package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.enu.Psd2ClientMode;
import de.zft2.gbanking.util.TypeConverter;

public class Psd2ClientConfigurationMapper extends AbstractDaoMapper<Psd2ClientConfiguration, Void> {

	@Override
	public void setParamsFull(Psd2ClientConfiguration configuration, PreparedStatement ps) throws SQLException {
		ps.setInt(1, configuration.getClientMode().getDbStateId());
		ps.setString(2, configuration.getApplicationId());
		ps.setBytes(3, configuration.getPrivateKeyPkcs8());
		ps.setString(4, configuration.getCallbackUrl());
		ps.setBytes(5, configuration.getCallbackPrivateKeyPkcs8());
		ps.setBytes(6, configuration.getCallbackCertificate());
		ps.setTimestamp(7, TypeConverter.toSqlTimestampNow());
		if (configuration.getId() > 0) {
			ps.setInt(8, configuration.getId());
		}
	}

	@Override
	public void mapDao(Psd2ClientConfiguration configuration, ResultType resultType, ResultSet rs) throws SQLException {
		configuration.setClientMode(Psd2ClientMode.forInt(rs.getInt("clientMode")));
		configuration.setApplicationId(rs.getString("applicationId"));
		configuration.setPrivateKeyPkcs8(rs.getBytes("privateKeyPkcs8"));
		configuration.setCallbackUrl(rs.getString("callbackUrl"));
		configuration.setCallbackPrivateKeyPkcs8(rs.getBytes("callbackPrivateKeyPkcs8"));
		configuration.setCallbackCertificate(rs.getBytes("callbackCertificate"));
	}
}
