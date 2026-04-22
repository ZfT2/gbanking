package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.util.TypeConverter;

public class MoneytransferMapper extends AbstractDaoMapper<MoneyTransfer, Void> {

	@Override
	public void setParamsFull(MoneyTransfer moneytransfer, PreparedStatement ps) throws SQLException {
		ps.setInt(1, moneytransfer.getAccountId());
		ps.setString(2, moneytransfer.getOrderType().name());
		ps.setInt(3, moneytransfer.getRecipientId());
		ps.setString(4, moneytransfer.getPurpose());
		ps.setDouble(5, moneytransfer.getAmount().doubleValue());
		ps.setString(6, TypeConverter.toDateStringShort(moneytransfer.getExecutionDate()));
		if (moneytransfer.getExecutionDay() != null) {
			ps.setInt(7, moneytransfer.getExecutionDay());
		} else {
			ps.setNull(7, java.sql.Types.INTEGER);
		}
		ps.setString(8, moneytransfer.getMoneytransferStatus().name());
		ps.setString(9, moneytransfer.getStandingorderMode() != null ? moneytransfer.getStandingorderMode().name() : null);
		if (moneytransfer.getHistoryorderId() != null) {
			ps.setInt(10, moneytransfer.getHistoryorderId());
		} else {
			ps.setNull(10, java.sql.Types.INTEGER);
		}
		ps.setDate(11, TypeConverter.toSqlDateNow());
		if (moneytransfer.getId() > 0)
			ps.setInt(12, moneytransfer.getId());
	}

	@Override
	public void mapDao(MoneyTransfer moneytransfer, ResultType resultType, ResultSet rs) throws SQLException {
		moneytransfer.setAccountId(rs.getInt(SqlFields.ACCOUNT_ACCOUNTID));
		moneytransfer.setOrderType(OrderType.valueOf(rs.getString("moneytransferType")));
		moneytransfer.setRecipientId(rs.getInt("recipient_id"));
		moneytransfer.setPurpose(rs.getString(SqlFields.BOOKING_PURPOSE));
		moneytransfer.setAmount(rs.getBigDecimal(SqlFields.BOOKING_AMOUNT));
		moneytransfer.setExecutionDate(TypeConverter.toLocalDateFromDateStrShort(rs.getString("executionDate")));
		int executionDay = rs.getInt("executionDay");
		moneytransfer.setExecutionDay(rs.wasNull() ? null : executionDay);
		moneytransfer.setMoneytransferStatus(MoneyTransferStatus.valueOf(rs.getString("moneytransferStatus")));
		moneytransfer.setStandingorderMode(rs.getString("standingorderMode") != null ? StandingorderMode.valueOf(rs.getString("standingorderMode")) : null);
		int historyOrderId = rs.getInt("historyorder_id");
		moneytransfer.setHistoryorderId(rs.wasNull() ? null : historyOrderId);

		Recipient recipient = new Recipient();
		recipient.setId(rs.getInt("r_id"));
		recipient.setName(rs.getString("name"));
		recipient.setIban(rs.getString("iban"));
		recipient.setBic(rs.getString("bic"));
		recipient.setBank(rs.getString("bank"));
		moneytransfer.setRecipient(recipient);
	}

}
