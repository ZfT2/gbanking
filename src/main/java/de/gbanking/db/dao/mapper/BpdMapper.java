package de.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.ParameterDataBankAccess;

public class BpdMapper extends ParameterDataBankAccessMapper {

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultType resultType, ResultSet rs) throws SQLException {
		toDao(rs, pdba);
	}
}
