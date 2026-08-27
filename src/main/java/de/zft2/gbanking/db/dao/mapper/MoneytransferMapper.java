package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.util.TypeConverter;

public class MoneytransferMapper extends AbstractDaoMapper<MoneyTransfer, Void> {

	public MoneytransferMapper() {
		super(MoneyTransfer::new);
	}

	@Override
	public void setParamsFull(MoneyTransfer moneytransfer, PreparedStatement ps) throws SQLException {
		ps.setInt(1, moneytransfer.getAccountId());
		ps.setInt(2, moneytransfer.getOrderType().getDbStateId());
		ps.setInt(3, moneytransfer.getRecipientId());
		ps.setString(4, moneytransfer.getPurpose());
		ps.setString(5, moneytransfer.getPurposeCode());
		ps.setString(6, moneytransfer.getEndToEndId());
		ps.setBigDecimal(7, moneytransfer.getAmount());
		ps.setString(8, TypeConverter.toDateStringShort(moneytransfer.getExecutionDate()));
		if (moneytransfer.getExecutionDay() != null) {
			ps.setInt(9, moneytransfer.getExecutionDay());
		} else {
			ps.setNull(9, java.sql.Types.INTEGER);
		}
		ps.setInt(10, moneytransfer.getMoneytransferStatus().getDbStateId());
		setEnumNullable(11, moneytransfer.getStandingorderMode(), ps);
		ps.setString(12, moneytransfer.getBankOrderId());
		if (moneytransfer.getHistoryorderId() != null) {
			ps.setInt(13, moneytransfer.getHistoryorderId());
		} else {
			ps.setNull(13, java.sql.Types.INTEGER);
		}
		ps.setTimestamp(14, TypeConverter.toSqlTimestampNow());
		if (moneytransfer.getId() > 0)
			ps.setInt(15, moneytransfer.getId());
	}

	@Override
	public void mapDao(MoneyTransfer moneytransfer, ResultType resultType, ResultSet rs) throws SQLException {
		moneytransfer.setAccountId(rs.getInt(SqlFields.ACCOUNT_ACCOUNTID));
		moneytransfer.setOrderType(OrderType.forInt(rs.getInt("moneytransferType")));
		moneytransfer.setRecipientId(rs.getInt("recipient_id"));
		moneytransfer.setPurpose(rs.getString(SqlFields.BOOKING_PURPOSE));
		moneytransfer.setPurposeCode(rs.getString("purposeCode"));
		moneytransfer.setEndToEndId(rs.getString("endToEndId"));
		moneytransfer.setAmount(rs.getBigDecimal(SqlFields.BOOKING_AMOUNT));
		moneytransfer.setCurrency(rs.getString("currency"));
		moneytransfer.setExecutionDate(TypeConverter.toLocalDateFromDateStrShort(rs.getString("executionDate")));
		int executionDay = rs.getInt("executionDay");
		moneytransfer.setExecutionDay(rs.wasNull() ? null : executionDay);
		moneytransfer.setMoneytransferStatus(MoneyTransferStatus.forInt(rs.getInt("moneytransferStatus")));
		moneytransfer.setStandingorderMode(getEnumNullable("standingorderMode", StandingorderMode.class, rs));
		moneytransfer.setBankOrderId(rs.getString("bankOrderId"));
		int historyOrderId = rs.getInt("historyorder_id");
		moneytransfer.setHistoryorderId(rs.wasNull() ? null : historyOrderId);

		Recipient recipient = new Recipient();
		recipient.setId(rs.getInt("r_id"));
		recipient.setName(rs.getString("name"));
		recipient.setIban(rs.getString("iban"));
		recipient.setBic(rs.getString("bic"));
		recipient.setAccountNumber(rs.getString("accountnumber"));
		recipient.setBlz(rs.getString("blz"));
		recipient.setBank(rs.getString("bank"));
		moneytransfer.setRecipient(recipient);

		MoneyTransferForeign foreignTransfer = mapForeignTransfer(rs);
		if (foreignTransfer != null) {
			moneytransfer.setForeignTransfer(foreignTransfer);
		}
	}

	private MoneyTransferForeign mapForeignTransfer(ResultSet rs) throws SQLException {
		int foreignId = rs.getInt("foreign_id");
		if (rs.wasNull()) {
			return null;
		}

		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setId(foreignId);
		foreignTransfer.setMoneyTransferId(rs.getInt("foreign_moneytransfer_id"));
		foreignTransfer.setCurrency(rs.getString("foreign_currency"));
		foreignTransfer.setRecipientCountry(rs.getString("recipientCountry"));
		foreignTransfer.setRecipientAccountNumber(rs.getString("recipientAccountNumber"));
		foreignTransfer.setRecipientBankCode(rs.getString("recipientBankCode"));
		foreignTransfer.setRecipientSubAccount(rs.getString("recipientSubAccount"));
		foreignTransfer.setRecipientAddressLine1(rs.getString("recipientAddressLine1"));
		foreignTransfer.setRecipientAddressLine2(rs.getString("recipientAddressLine2"));
		foreignTransfer.setRecipientBankCountry(rs.getString("recipientBankCountry"));
		foreignTransfer.setRecipientBankAddressLine1(rs.getString("recipientBankAddressLine1"));
		foreignTransfer.setRecipientBankAddressLine2(rs.getString("recipientBankAddressLine2"));
		foreignTransfer.setChargeBearer(getEnumNullable("chargeBearer", ForeignChargeBearer.class, rs));
		foreignTransfer.setRegulatoryReporting(rs.getString("regulatoryReporting"));
		foreignTransfer.setEndToEndReference(rs.getString("endToEndReference"));
		return foreignTransfer;
	}

}
