package de.zft2.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;

public class UpdMapper extends ParameterDataBankAccessMapper {

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultType rsResultType, ResultSet rs) throws SQLException {
		toDao(rs, pdba);
	}

}
