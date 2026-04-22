package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.util.TypeConverter;

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
