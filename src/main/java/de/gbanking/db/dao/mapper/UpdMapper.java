package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.dao.ParameterDataBankAccess;
import de.gbanking.db.dao.Upd;

public class UpdMapper extends ParameterDataBankAccessMapper {

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultSet rs) throws SQLException {
		if (pdba == null)
			pdba = new Upd();
		pdba = toDao(rs, pdba);
	}

}
