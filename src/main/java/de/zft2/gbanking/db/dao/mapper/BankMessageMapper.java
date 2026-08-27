package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.util.TypeConverter;

public class BankMessageMapper extends AbstractDaoMapper<BankMessage, Void> {

	public BankMessageMapper() {
		super(BankMessage::new);
	}

	@Override
	public void setParamsFull(BankMessage bankMessage, PreparedStatement ps) throws SQLException {
		int index = 1;
		ps.setInt(index++, bankMessage.getBankAccessId());
		ps.setString(index++, bankMessage.getBankName());
		ps.setString(index++, bankMessage.getMessageKey());
		ps.setString(index++, bankMessage.getCode());
		ps.setString(index++, bankMessage.getType());
		ps.setString(index++, bankMessage.getFormat());
		ps.setString(index++, bankMessage.getDescription());
		ps.setDate(index++, toSqlDate(bankMessage.getVersionDate()));
		ps.setString(index++, bankMessage.getComments());
		ps.setString(index++, bankMessage.getMessage());
		ps.setString(index++, formatDateTime(bankMessage.getRetrievedAt()));
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (bankMessage.getId() > 0) {
			ps.setInt(index, bankMessage.getId());
		}
	}

	@Override
	void mapDao(BankMessage bankMessage, ResultType resultType, ResultSet rs) throws SQLException {
		bankMessage.setBankAccessId(rs.getInt("bankAccess_id"));
		bankMessage.setBankName(rs.getString("bankName"));
		bankMessage.setMessageKey(rs.getString("messageKey"));
		bankMessage.setCode(rs.getString("code"));
		bankMessage.setType(rs.getString("type"));
		bankMessage.setFormat(rs.getString("format"));
		bankMessage.setDescription(rs.getString("description"));
		bankMessage.setVersionDate(toLocalDate(rs.getDate("versionDate")));
		bankMessage.setComments(rs.getString("comments"));
		bankMessage.setMessage(rs.getString("message"));
		bankMessage.setRetrievedAt(parseDateTime(rs.getString("retrievedAt")));
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
