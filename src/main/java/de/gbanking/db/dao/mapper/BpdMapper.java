package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.dao.Bpd;
import de.gbanking.db.dao.ParameterDataBankAccess;

public class BpdMapper extends ParameterDataBankAccessMapper {
	
	@Override
	public ParameterDataBankAccess toDao(ResultSet rs) throws SQLException {
		return toDao(rs, new Bpd());
	}

//	@Override
//	ParameterDataBankAccess toDao(ResultSet rs, ParameterDataBankAccess pdBankAccess) throws SQLException {
//		return toDao(rs, new Bpd());
//	}

}
