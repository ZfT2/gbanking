package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.SqlFields;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.StandingorderMode;
import de.gbanking.util.TypeConverter;

public class MoneytransferMapper extends AbstractDaoMapper<MoneyTransfer, Void> {

	@Override
	public void setParamsFull(MoneyTransfer moneytransfer, PreparedStatement ps) throws SQLException {
		ps.setInt(1, moneytransfer.getAccountId());
		ps.setString(2, moneytransfer.getOrderType().name());
		ps.setInt(3, moneytransfer.getRecipientId());
		ps.setString(4, moneytransfer.getPurpose());
		ps.setDouble(5, moneytransfer.getAmount().doubleValue());
		ps.setString(6, TypeConverter.toDateStringShort(moneytransfer.getExecutionDate()));
		ps.setString(7, moneytransfer.getMoneytransferStatus().name());
		ps.setString(8, moneytransfer.getStandingorderMode() != null ? moneytransfer.getStandingorderMode().name() : null);
		if (moneytransfer.getHistoryorderId() != null) {
			ps.setInt(9, moneytransfer.getHistoryorderId());
		}
		ps.setDate(10, TypeConverter.toSqlDateNow());
		if (moneytransfer.getId() > 0)
			ps.setInt(11, moneytransfer.getId());
	}

	@Override
	public void mapDao(MoneyTransfer moneytransfer, ResultSet rs) throws SQLException {
		if (moneytransfer == null)
			moneytransfer = new MoneyTransfer();
		super.mapDao(moneytransfer, rs);
		moneytransfer.setAccountId(rs.getInt(SqlFields.ACCOUNT_ACCOUNTID));
		moneytransfer.setOrderType(OrderType.valueOf(rs.getString("moneytransferType")));
		moneytransfer.setRecipientId(rs.getInt("recipient_id"));
		moneytransfer.setPurpose(rs.getString(SqlFields.BOOKING_PURPOSE));
		moneytransfer.setAmount(rs.getBigDecimal(SqlFields.BOOKING_AMOUNT));
		moneytransfer.setExecutionDate(TypeConverter.toCalendarFromTimestampStr(rs.getString("executionDate")));
		moneytransfer.setMoneytransferStatus(MoneyTransferStatus.valueOf(rs.getString("moneytransferStatus")));
		moneytransfer.setStandingorderMode(rs.getString("standingorderMode") != null ? StandingorderMode.valueOf(rs.getString("standingorderMode")) : null);
		moneytransfer.setHistoryorderId(rs.getInt("historyorder_id"));

		Recipient recipient = new Recipient();
		recipient.setId(rs.getInt("r_id"));
		recipient.setName(rs.getString("name"));
		recipient.setIban(rs.getString("iban"));
		recipient.setBic(rs.getString("bic"));
		recipient.setBank(rs.getString("bank"));
		moneytransfer.setRecipient(recipient);
	}

}
