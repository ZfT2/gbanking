package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.dao.ParameterDataBankAccess;
import de.gbanking.db.dao.Upd;

public class UpdMapper extends ParameterDataBankAccessMapper {

	@Override
	public ParameterDataBankAccess toDao(ResultSet rs) throws SQLException {
		return toDao(rs, new Upd());
	}

}
