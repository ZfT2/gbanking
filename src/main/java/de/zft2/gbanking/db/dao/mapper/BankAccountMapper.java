package de.zft2.gbanking.db.dao.mapper;

import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.TypeConverter;

public class BankAccountMapper extends AbstractDaoMapper<BankAccount, Void> {

	@Override
	public void setParamsFull(BankAccount bankAccount, PreparedStatement ps) throws SQLException {
		int index = 1;

		index = setIntegerNullable(index, bankAccount.getBankAccessId(), ps);
		index = setIntegerNullable(index, bankAccount.getParentAccountId(), ps);
		ps.setString(index++, bankAccount.getProviderAccountId());
		if (bankAccount.getAccountName() != null) {
			ps.setString(index++, bankAccount.getAccountName());
		} else {
			ps.setString(index++, bankAccount.getDefaultAccountName());
		}
		ps.setInt(index++, bankAccount.getBaseCurrency().getDbStateId());
		ps.setInt(index++, bankAccount.getAccountType().getDbStateId());
		ps.setInt(index++, bankAccount.getSource().getDbStateId());
		ps.setString(index++, bankAccount.getIban());
		ps.setString(index++, bankAccount.getBic());
		ps.setString(index++, bankAccount.getNumber());
		ps.setString(index++, bankAccount.getSubnumber());
		ps.setString(index++, bankAccount.getBankName());
		ps.setString(index++, bankAccount.getBlz());
		ps.setInt(index++, bankAccount.getHbciAccountType());
		ps.setString(index++, bankAccount.getLimit());
		ps.setString(index++, bankAccount.getCustomerid());
		ps.setString(index++, bankAccount.getOwnerName());
		ps.setString(index++, bankAccount.getOwnerName2());
		ps.setString(index++, bankAccount.getCountry());
		ps.setString(index++, bankAccount.getCreditorid());
		ps.setBoolean(index++, bankAccount.isSEPAAccount());
		ps.setBoolean(index++, bankAccount.isOfflineAccount());
		ps.setInt(index++, bankAccount.getAccountState().getDbStateId());
		index = setBigDecimalNullable(index, bankAccount.getBalance(), ps);
		if (bankAccount.getId() <= 0) {
			ps.setTimestamp(index++, getCreatedAtTimestamp(bankAccount));
		}
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (bankAccount.getId() > 0)
			ps.setInt(index, bankAccount.getId());
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
					AbstractDaoMapper<Booking, ?> mapper = StatementsConfig.getMapperForDaoType(booking.getClass());
					mapper.setParamsForUpdateSimpleField(booking, typeToUpdate, ps);
				}
			}
		}
	}

	@Override
	void setParamsForUpdateSource(BankAccount bankAccount, PreparedStatement ps) throws SQLException {
		ps.setInt(1, bankAccount.getSource().getDbStateId());
		ps.setDate(2, TypeConverter.toSqlDateNow());
		if (bankAccount.getId() > 0)
			ps.setInt(3, bankAccount.getId());
	}

	@Override
	public void mapDao(BankAccount account, ResultType resultType, ResultSet rs) throws SQLException {
		account.setBankAccessId(rs.getInt("bankAccess_id"));
		account.setParentAccountId(rs.getInt("parentAccount_id"));
		account.setProviderAccountId(rs.getString("providerAccountId"));
		account.setAccountName(rs.getString(SqlFields.ACCOUNT_ACCOUNTNAME));
		account.setBaseCurrency(Currency.forInt(rs.getInt("baseCurrency")));
		account.setAccountType(AccountType.forInt(rs.getInt("accountType")));
		account.setSource(Source.forInt(rs.getInt("accountSource")));
		account.setIban(rs.getString("iban"));
		account.setBic(rs.getString("bic"));
		account.setNumber(rs.getString(SqlFields.ACCOUNT_NUMBER));
		account.setSubnumber(rs.getString("subNumber"));
		account.setBankName(rs.getString(SqlFields.BANKNAME));
		account.setBlz(rs.getString("blz"));
		account.setHbciAccountType(rs.getInt("hbciAccountType"));
		account.setLimit(rs.getString("accountLimit"));
		account.setCustomerid(rs.getString("customerId"));
		account.setOwnerName(rs.getString("ownerName"));
		account.setOwnerName2(rs.getString("ownerName2"));
		account.setCountry(rs.getString("country"));
		account.setCreditorid(rs.getString("creditorId"));
		account.setSEPAAccount(rs.getBoolean("isSepaAccount"));
		account.setOfflineAccount(rs.getBoolean("isOfflineAccount"));
		account.setAccountState(AccountState.forInt(rs.getInt("accountState")));

		if (rs.getBigDecimal(SqlFields.BALANCE) != null)
			account.setBalance(rs.getBigDecimal(SqlFields.BALANCE).setScale(2, RoundingMode.HALF_UP));
		account.setCreatedAt(TypeConverter.toLocalDateFromSqlDate(rs.getDate(SqlFields.DAO_CREATEDAT)));
	}

	private Timestamp getCreatedAtTimestamp(BankAccount bankAccount) {
		LocalDate createdAt = bankAccount.getCreatedAt();
		if (createdAt != null) {
			return Timestamp.valueOf(createdAt.atStartOfDay());
		}

		Timestamp createdAtTimestamp = TypeConverter.toSqlTimestampNow();
		bankAccount.setCreatedAt(createdAtTimestamp.toLocalDateTime().toLocalDate());
		return createdAtTimestamp;
	}

}
