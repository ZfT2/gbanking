package de.zft2.gbanking.db.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;

public class BpdMapper extends ParameterDataBankAccessMapper {

	public BpdMapper() {
		super(Bpd::new);
	}

	@Override
	public void mapDao(ParameterDataBankAccess pdba, ResultType resultType, ResultSet rs) throws SQLException {
		toDao(rs, pdba);
	}
}
