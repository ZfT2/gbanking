package de.zft2.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;

public class UpdMapper extends ParameterDataBankAccessMapper {

	public UpdMapper() {
		super(Upd::new);
	}

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultType rsResultType, ResultSet rs) throws SQLException {
		toDao(rs, pdba);
	}

}
