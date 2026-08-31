package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.util.TypeConverter;

public class MoneyTransferProtocolMapper extends AbstractDaoMapper<MoneyTransferProtocol, Void> {

	@Override
	public void setParamsFull(MoneyTransferProtocol protocol, PreparedStatement ps) throws SQLException {
		ps.setInt(1, protocol.getMoneyTransferId());
		ps.setInt(2, protocol.getMoneytransferStatus().getDbStateId());
		ps.setString(3, format(protocol.getTimeStart()));
		if (protocol.getTimeFinish() != null) {
			ps.setString(4, format(protocol.getTimeFinish()));
		} else {
			ps.setNull(4, Types.VARCHAR);
		}
		ps.setString(5, protocol.getBankOrderId());
		setEnumNullable(6, protocol.getSepaOrderStatus(), ps);
		setEnumNullable(7, protocol.getSepaCancellationCode(), ps);
		ps.setString(8, protocol.getProtocolText());
		ps.setTimestamp(9, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(MoneyTransferProtocol protocol, ResultType resultType, ResultSet rs) throws SQLException {
		protocol.setMoneyTransferId(rs.getInt("moneytransfer_id"));
		protocol.setMoneytransferStatus(MoneyTransferStatus.forInt(rs.getInt("moneytransferStatus")));
		protocol.setTimeStart(parse(rs.getString("timeStart")));
		protocol.setTimeFinish(parse(rs.getString("timeFinish")));
		protocol.setBankOrderId(rs.getString("bankOrderId"));
		protocol.setSepaOrderStatus(getEnumNullable("sepaOrderStatus", SepaOrderStatus.class, rs));
		protocol.setSepaCancellationCode(getEnumNullable("sepaCancellationCode", SepaCancellationCode.class, rs));
		protocol.setProtocolText(rs.getString("protocolText"));
	}

	private String format(LocalDateTime value) {
		return value != null ? value.toString() : null;
	}

	private LocalDateTime parse(String value) {
		return value != null && !value.isBlank() ? LocalDateTime.parse(value) : null;
	}
}
