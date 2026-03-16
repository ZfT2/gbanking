package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import de.gbanking.db.dao.BusinessCase;
import de.gbanking.util.TypeConverter;

public class BusinessCaseMapper extends AbstractDaoMapper<BusinessCase, Void> {

	@Override
	public void setParamsFull(Set<BusinessCase> businessCaseList, PreparedStatement ps) throws SQLException {
		// TODO document why this method is empty

		for (BusinessCase businessCaseNew : businessCaseList) {
			setParamsFull(businessCaseNew, ps);
			ps.addBatch();
		}
	}

	@Override
	public void setParamsFull(BusinessCase businessCase, PreparedStatement ps) throws SQLException {
		ps.setString(1, businessCase.getCaseValue());
		ps.setDate(2, TypeConverter.toSqlDateNow());
	}

	@Override
	public void mapDao(BusinessCase businessCase, ResultSet rs) throws SQLException {
		if (businessCase == null)
			businessCase = new BusinessCase();
		super.mapDao(businessCase, rs);
		businessCase.setCaseValue(rs.getString("caseValue"));
	}

}
