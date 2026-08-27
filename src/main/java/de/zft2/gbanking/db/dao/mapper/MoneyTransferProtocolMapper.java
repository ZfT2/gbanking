package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.util.TypeConverter;

public class MoneyTransferProtocolMapper extends AbstractDaoMapper<MoneyTransferProtocol, Void> {

	public MoneyTransferProtocolMapper() {
		super(MoneyTransferProtocol::new);
	}

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
		ps.setString(5, protocol.getProtocolText());
		ps.setTimestamp(6, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(MoneyTransferProtocol protocol, ResultType resultType, ResultSet rs) throws SQLException {
		protocol.setMoneyTransferId(rs.getInt("moneytransfer_id"));
		protocol.setMoneytransferStatus(MoneyTransferStatus.forInt(rs.getInt("moneytransferStatus")));
		protocol.setTimeStart(parse(rs.getString("timeStart")));
		protocol.setTimeFinish(parse(rs.getString("timeFinish")));
		protocol.setProtocolText(rs.getString("protocolText"));
	}

	private String format(LocalDateTime value) {
		return value != null ? value.toString() : null;
	}

	private LocalDateTime parse(String value) {
		return value != null && !value.isBlank() ? LocalDateTime.parse(value) : null;
	}
}
