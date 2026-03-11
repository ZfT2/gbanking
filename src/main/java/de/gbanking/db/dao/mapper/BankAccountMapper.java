package de.gbanking.db.dao.mapper;

import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import de.gbanking.db.SqlFields;
import de.gbanking.db.StatementsConfig;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.util.TypeConverter;

public class BankAccountMapper extends AbstractDaoMapper<BankAccount, Void> {
	
	//private AbstractDaoMapper<?, ?> bookingMapper = StatementsConfig.getMapperForDaoType(Booking.class);

	@Override
	public void setParamsFull(BankAccount bankAccount, PreparedStatement ps) throws SQLException {
		if (bankAccount.getBankAccessId() != null) {
			ps.setInt(1, bankAccount.getBankAccessId());
		}
		if (bankAccount.getAccountName() != null) {
			ps.setString(2, bankAccount.getAccountName());
		} else {
			ps.setString(2, bankAccount.getDefaultAccountName());
		}
		ps.setString(3, bankAccount.getCurrency());
		ps.setString(4, bankAccount.getAccountType().name());
		ps.setString(5, bankAccount.getSource().name());
		ps.setString(6, bankAccount.getIban());
		ps.setString(7, bankAccount.getBic());
		ps.setString(8, bankAccount.getNumber());
		ps.setString(9, bankAccount.getSubnumber());
		ps.setString(10, bankAccount.getBankName());
		ps.setString(11, bankAccount.getBlz());
		ps.setInt(12, bankAccount.getHbciAccountType());
		ps.setString(13, bankAccount.getLimit());
		ps.setString(14, bankAccount.getCustomerid());
		ps.setString(15, bankAccount.getOwnerName());
		ps.setString(16, bankAccount.getOwnerName2());
		ps.setString(17, bankAccount.getCountry());
		ps.setString(18, bankAccount.getCreditorid());
		ps.setBoolean(19, bankAccount.isSEPAAccount());
		ps.setBoolean(20, bankAccount.isOfflineAccount());
		ps.setString(21, bankAccount.getAccountState().name());

		if (bankAccount.getBalance() != null)
			ps.setDouble(22, bankAccount.getBalance().doubleValue());
		ps.setString(23, TypeConverter.toTimestampStringNow());
		if (bankAccount.getId() > 0)
			ps.setInt(24, bankAccount.getId());
	}
	
	@Override
	public <W> void setParamsForUpdateSimpleField(List<BankAccount> entitySet, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		for (BankAccount bankAccount : entitySet) {
			setParamsForUpdateSimpleField(bankAccount, typeToUpdate, ps);
		}
	}
	
	@Override
	public <V> void setParamsForUpdateSimpleField(BankAccount bankAccount, Class<V> typeToUpdate, PreparedStatement ps) throws SQLException {

		if (typeToUpdate != null) {
			if (BankAccount.class.equals(typeToUpdate)) {
				setParamsForUpdateSource(bankAccount, ps);
			} else if (Booking.class.equals(typeToUpdate)) {
				for (Booking booking : bankAccount.getBookings()) {
					// StatementsQueryMapper.setBookingParamsSource(booking, ps);
					//StatementsQueryMapper.setParamsForUpdateSimpleField(booking, typeToUpdate, ps);
					AbstractDaoMapper<Booking, ?> mapper = StatementsConfig.getMapperForDaoType(booking.getClass());
					mapper.setParamsForUpdateSimpleField(booking, typeToUpdate, ps);
				}
			}
		}
	}
	
	@Override
	void setParamsForUpdateSource(BankAccount bankAccount, PreparedStatement ps) throws SQLException {
		ps.setString(1, bankAccount.getSource().name());
		ps.setString(2, TypeConverter.toTimestampStringNow());
		if (bankAccount.getId() > 0)
			ps.setInt(3, bankAccount.getId());
	}

	@Override
	public BankAccount toDao(ResultSet rs) throws SQLException {
		BankAccount account = new BankAccount();
		account.setId(rs.getInt("id"));
		account.setBankAccessId(rs.getInt("bankAccess_id"));
		account.setAccountName(rs.getString(SqlFields.ACCOUNT_ACCOUNTNAME));
		account.setCurrency(rs.getString("currency"));
		account.setAccountType(AccountType.valueOf(rs.getString("accountType")));
		account.setSource(Source.valueOf(rs.getString("accountSource")));
		account.setIban(rs.getString("iban"));
		account.setBic(rs.getString("bic"));
		account.setNumber(rs.getString(SqlFields.ACCOUNT_NUMBER));
		account.setSubnumber(rs.getString("subNumber"));
		account.setBankName(rs.getString(SqlFields.BANKNAME));
		account.setBlz(rs.getString("blz"));
		account.setHbciAccountType(0);
		account.setLimit(rs.getString("accountLimit"));
		account.setCustomerid(rs.getString("customerId"));
		account.setOwnerName(rs.getString("ownerName"));
		account.setOwnerName2(rs.getString("ownerName2"));
		account.setCountry(rs.getString("country"));
		account.setCreditorid(rs.getString("creditorId"));
		account.setSEPAAccount(rs.getBoolean("isSepaAccount"));
		account.setOfflineAccount(rs.getBoolean("isOfflineAccount"));
		account.setAccountState(AccountState.valueOf(rs.getString("accountState")));

		if (rs.getBigDecimal(SqlFields.BALANCE) != null)
			account.setBalance(rs.getBigDecimal(SqlFields.BALANCE).setScale(2, RoundingMode.HALF_UP));
		account.setUpdatedAt((TypeConverter.toCalendarFromTimestampStr(rs.getString(SqlFields.DAO_UPDATEDAT))));
		return account;
	}

}
