package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.enu.ForeignChargeBearer;
import de.zft2.gbanking.util.TypeConverter;

public class MoneyTransferForeignMapper extends AbstractDaoMapper<MoneyTransferForeign, Void> {

	public MoneyTransferForeignMapper() {
		super(MoneyTransferForeign::new);
	}

	@Override
	public void setParamsFull(MoneyTransferForeign foreignTransfer, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, foreignTransfer.getMoneyTransferId());
		ps.setString(index++, foreignTransfer.getCurrency());
		ps.setString(index++, foreignTransfer.getRecipientCountry());
		ps.setString(index++, foreignTransfer.getRecipientAccountNumber());
		ps.setString(index++, foreignTransfer.getRecipientBankCode());
		ps.setString(index++, foreignTransfer.getRecipientSubAccount());
		ps.setString(index++, foreignTransfer.getRecipientAddressLine1());
		ps.setString(index++, foreignTransfer.getRecipientAddressLine2());
		ps.setString(index++, foreignTransfer.getRecipientBankCountry());
		ps.setString(index++, foreignTransfer.getRecipientBankAddressLine1());
		ps.setString(index++, foreignTransfer.getRecipientBankAddressLine2());
		index = setEnumNullable(index, foreignTransfer.getChargeBearer(), ps);
		ps.setString(index++, foreignTransfer.getRegulatoryReporting());
		ps.setString(index++, foreignTransfer.getEndToEndReference());
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (foreignTransfer.getId() > 0) {
			ps.setInt(index, foreignTransfer.getId());
		}
	}

	public void setParamsForUpsert(MoneyTransferForeign foreignTransfer, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, foreignTransfer.getMoneyTransferId());
		ps.setString(index++, foreignTransfer.getCurrency());
		ps.setString(index++, foreignTransfer.getRecipientCountry());
		ps.setString(index++, foreignTransfer.getRecipientAccountNumber());
		ps.setString(index++, foreignTransfer.getRecipientBankCode());
		ps.setString(index++, foreignTransfer.getRecipientSubAccount());
		ps.setString(index++, foreignTransfer.getRecipientAddressLine1());
		ps.setString(index++, foreignTransfer.getRecipientAddressLine2());
		ps.setString(index++, foreignTransfer.getRecipientBankCountry());
		ps.setString(index++, foreignTransfer.getRecipientBankAddressLine1());
		ps.setString(index++, foreignTransfer.getRecipientBankAddressLine2());
		index = setEnumNullable(index, foreignTransfer.getChargeBearer(), ps);
		ps.setString(index++, foreignTransfer.getRegulatoryReporting());
		ps.setString(index++, foreignTransfer.getEndToEndReference());
		ps.setTimestamp(index, TypeConverter.toSqlTimestampNow());
	}

	@Override
	public void mapDao(MoneyTransferForeign foreignTransfer, ResultType resultType, ResultSet rs) throws SQLException {
		foreignTransfer.setMoneyTransferId(rs.getInt("moneytransfer_id"));
		foreignTransfer.setCurrency(rs.getString("currency"));
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
	}
}
