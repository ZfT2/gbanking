package de.zft2.gbanking.db.dao.mapper;

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
		ps.setString(2, recipient.getName());
		ps.setString(3, recipient.getName());

		ps.setString(4, recipient.getIban());
		ps.setString(5, recipient.getIban());
		ps.setString(6, recipient.getIban());

		ps.setString(7, recipient.getAccountNumber());
		ps.setString(8, recipient.getAccountNumber());
		ps.setString(9, recipient.getAccountNumber());

		ps.setString(10, recipient.getBlz());
		ps.setString(11, recipient.getBlz());
		ps.setString(12, recipient.getBlz());

		ps.setString(13, recipient.getBic());
		ps.setString(14, recipient.getBic());
		ps.setString(15, recipient.getBic());
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
