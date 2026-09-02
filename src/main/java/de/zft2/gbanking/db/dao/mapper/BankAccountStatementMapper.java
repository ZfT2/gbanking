package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
		ps.setString(index++, formatDateTime(statement.getRetrievedAt()));
		ps.setDate(index++, toSqlDate(statement.getStatementDate()));
		ps.setDate(index++, toSqlDate(statement.getStartDate()));
		ps.setDate(index++, toSqlDate(statement.getEndDate()));
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
		ps.setString(index++, formatDateTime(statement.getAcknowledgedAt()));
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
		statement.setRetrievedAt(parseDateTime(rs.getString("retrievedAt")));
		statement.setStatementDate(toLocalDate(rs.getDate("statementDate")));
		statement.setStartDate(toLocalDate(rs.getDate("startDate")));
		statement.setEndDate(toLocalDate(rs.getDate("endDate")));
		statement.setYear(rs.getInt("year"));
		statement.setNumber(rs.getInt("number"));
		statement.setSize(rs.getLong("size"));
		statement.setIban(rs.getString("iban"));
		statement.setBic(rs.getString("bic"));
		statement.setSourceJob(rs.getString("sourceJob"));
		statement.setReceiptAvailable(rs.getBoolean("receiptAvailable"));
		statement.setReceipt(rs.getBytes("receipt"));
		statement.setAcknowledged(rs.getBoolean("acknowledged"));
		statement.setAcknowledgedAt(parseDateTime(rs.getString("acknowledgedAt")));
	}

	private java.sql.Date toSqlDate(LocalDate date) {
		return date != null ? java.sql.Date.valueOf(date) : null;
	}

	private LocalDate toLocalDate(java.sql.Date date) {
		return date != null ? date.toLocalDate() : null;
	}

	private String formatDateTime(LocalDateTime dateTime) {
		return dateTime != null ? dateTime.toString() : null;
	}

	private LocalDateTime parseDateTime(String value) {
		return value != null && !value.isBlank() ? LocalDateTime.parse(value) : null;
	}
}
