package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.StatementsConfig.ResultType;
import de.gbanking.db.dao.BusinessCase;
import de.gbanking.util.TypeConverter;

public class BusinessCaseMapper extends AbstractDaoMapper<BusinessCase, Void> {

	@Override
	public void setParamsFull(BusinessCase businessCase, PreparedStatement ps) throws SQLException {
		ps.setString(1, businessCase.getCaseValue());
		ps.setDate(2, TypeConverter.toSqlDateNow());
	}

	@Override
	public void mapDao(BusinessCase businessCase, ResultType resultType, ResultSet rs) throws SQLException {
		businessCase.setCaseValue(rs.getString("caseValue"));
	}

}
