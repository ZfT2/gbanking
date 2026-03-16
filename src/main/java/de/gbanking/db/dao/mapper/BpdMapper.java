package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.ParameterDataBankAccess;

public class BpdMapper extends ParameterDataBankAccessMapper {

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultSet rs) throws SQLException {
		if (pdba == null)
			pdba = new Bpd();
		pdba = toDao(rs, pdba);
	}

//	@Override
//	ParameterDataBankAccess toDao(ResultSet rs, ParameterDataBankAccess pdBankAccess) throws SQLException {
//		return toDao(rs, new Bpd());
//	}

}
