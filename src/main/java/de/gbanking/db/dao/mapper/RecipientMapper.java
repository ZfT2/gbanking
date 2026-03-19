package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.util.TypeConverter;

public class RecipientMapper extends AbstractDaoMapper<Recipient, Void> {

	@Override
	public void setParamsFull(Recipient recipient, PreparedStatement ps) throws SQLException {
		ps.setString(1, recipient.getName());
		ps.setString(2, recipient.getIban());
		ps.setString(3, recipient.getBic());
		ps.setString(4, recipient.getAccountNumber());
		ps.setString(5, recipient.getBlz());
		ps.setString(6, recipient.getBank());
		ps.setString(7, recipient.getSource().name());
		ps.setString(8, recipient.getNote());
		ps.setDate(9, TypeConverter.toSqlDateNow());
		if (recipient.getId() > 0)
			ps.setInt(10, recipient.getId());
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
		ps.setString(1, recipient.getName());
		ps.setString(2, recipient.getIban());
		ps.setString(3, recipient.getAccountNumber());
		ps.setString(4, recipient.getBlz());
		ps.setString(5, recipient.getBic());
	}

	@Override
	public void mapDao(Recipient recipient, ResultType resultType, ResultSet rs) throws SQLException {
		recipient.setName(rs.getString("name"));
		recipient.setIban(rs.getString("iban"));
		recipient.setAccountNumber(rs.getString("accountNumber"));
		recipient.setBlz(rs.getString("blz"));
		recipient.setBic(rs.getString("bic"));
		recipient.setBank(rs.getString("bank"));
		recipient.setSource(Source.valueOf(rs.getString("source")));
		recipient.setNote(rs.getString("note"));
	}

}
