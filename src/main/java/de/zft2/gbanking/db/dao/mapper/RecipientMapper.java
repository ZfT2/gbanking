package de.zft2.gbanking.db.dao.mapper;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.TypeConverter;

public class RecipientMapper extends AbstractDaoMapper<Recipient, Void> {

	@Override
	public void setParamsFull(Recipient recipient, PreparedStatement ps) throws SQLException {
		ps.setString(1, recipient.getName());
		ps.setString(2, recipient.getIban());
		ps.setString(3, recipient.getBic());
		ps.setString(4, recipient.getAccountNumber());
		ps.setString(5, recipient.getBlz());
		ps.setString(6, recipient.getBank());
		ps.setInt(7, recipient.getSource().getDbStateId());
		ps.setString(8, recipient.getNote());
		ps.setBoolean(9, recipient.isDefault());
		ps.setTimestamp(10, TypeConverter.toSqlTimestampNow());
		if (recipient.getId() > 0)
			ps.setInt(11, recipient.getId());
	}

	@Override
	public <W> void setParamsForUpdateSimpleField(Recipient recipient, Class<W> typeToUpdate, PreparedStatement ps) throws SQLException {
		ps.setString(1, recipient.getNote());
		ps.setDate(2, TypeConverter.toSqlDateNow());
		if (recipient.getId() > 0)
			ps.setInt(3, recipient.getId());
	}

	@Override
	public void setParamsFind(Recipient recipient, PreparedStatement ps) throws SQLException {
		setFindText(ps, 1, recipient.getName());

		setFindText(ps, 4, recipient.getIban());

		setFindText(ps, 7, recipient.getAccountNumber());

		setFindText(ps, 10, recipient.getBlz());

		setFindText(ps, 13, recipient.getBic());

		setFindText(ps, 16, recipient.getBank());
	}

	@Override
	public void mapDao(Recipient recipient, ResultType resultType, ResultSet rs) throws SQLException {
		recipient.setName(rs.getString("name"));
		recipient.setIban(rs.getString("iban"));
		recipient.setAccountNumber(rs.getString("accountNumber"));
		recipient.setBlz(rs.getString("blz"));
		recipient.setBic(rs.getString("bic"));
		recipient.setBank(rs.getString("bank"));
		recipient.setSource(Source.forInt(rs.getInt("source")));
		recipient.setNote(rs.getString("note"));
		recipient.setDefault(rs.getBoolean("isDefault"));
	}

	private void setFindText(PreparedStatement ps, int firstIndex, String value) throws SQLException {
		String normalizedValue = trimToNull(value);
		ps.setString(firstIndex, normalizedValue);
		ps.setString(firstIndex + 1, normalizedValue);
		ps.setString(firstIndex + 2, normalizedValue);
	}

}
