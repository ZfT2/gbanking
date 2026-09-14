package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.util.TypeConverter;

public class BankAccountStatementMapper extends AbstractDaoMapper<BankAccountStatement, Void> {

	public BankAccountStatementMapper() {
		super(BankAccountStatement::new);
	}

	@Override
	public void setParamsFull(BankAccountStatement statement, PreparedStatement ps) throws SQLException {
		int index = 1;
		ps.setInt(index++, statement.getAccountId());
		ps.setString(index++, statement.getAccountName());
		ps.setString(index++, statement.getFileName());
		ps.setString(index++, statement.getFormat());
		ps.setString(index++, TypeConverter.toDateTimeString(statement.getRetrievedAt()));
		ps.setDate(index++, TypeConverter.toSqlDate(statement.getStatementDate()));
		ps.setDate(index++, TypeConverter.toSqlDate(statement.getStartDate()));
		ps.setDate(index++, TypeConverter.toSqlDate(statement.getEndDate()));
		ps.setInt(index++, statement.getYear());
		ps.setInt(index++, statement.getNumber());
		ps.setLong(index++, statement.getSize());
		ps.setString(index++, statement.getIban());
		ps.setString(index++, statement.getBic());
		ps.setString(index++, statement.getSourceJob());
		ps.setBoolean(index++, statement.isReceiptAvailable());
		if (statement.getReceipt() != null) {
			ps.setBytes(index++, statement.getReceipt());
		} else {
			ps.setNull(index++, Types.BLOB);
		}
		ps.setBoolean(index++, statement.isAcknowledged());
		ps.setString(index++, TypeConverter.toDateTimeString(statement.getAcknowledgedAt()));
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (statement.getId() > 0) {
			ps.setInt(index, statement.getId());
		}
	}

	@Override
	void mapDao(BankAccountStatement statement, ResultType resultType, ResultSet rs) throws SQLException {
		statement.setAccountId(rs.getInt("account_id"));
		statement.setAccountName(rs.getString("accountName"));
		statement.setFileName(rs.getString("fileName"));
		statement.setFormat(rs.getString("format"));
		statement.setRetrievedAt(TypeConverter.toLocalDateTime(rs.getString("retrievedAt")));
		statement.setStatementDate(TypeConverter.toLocalDate(rs.getDate("statementDate")));
		statement.setStartDate(TypeConverter.toLocalDate(rs.getDate("startDate")));
		statement.setEndDate(TypeConverter.toLocalDate(rs.getDate("endDate")));
		statement.setYear(rs.getInt("year"));
		statement.setNumber(rs.getInt("number"));
		statement.setSize(rs.getLong("size"));
		statement.setIban(rs.getString("iban"));
		statement.setBic(rs.getString("bic"));
		statement.setSourceJob(rs.getString("sourceJob"));
		statement.setReceiptAvailable(rs.getBoolean("receiptAvailable"));
		statement.setReceipt(rs.getBytes("receipt"));
		statement.setAcknowledged(rs.getBoolean("acknowledged"));
		statement.setAcknowledgedAt(TypeConverter.toLocalDateTime(rs.getString("acknowledgedAt")));
	}
}
