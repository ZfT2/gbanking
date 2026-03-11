package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.SqlFields;
import de.gbanking.db.dao.CategoryRule;
import de.gbanking.db.dao.CategoryRule.JoinType;
import de.gbanking.util.TypeConverter;

public class CategoryRuleMapper extends AbstractDaoMapper<CategoryRule, Void> {

	@Override
	public void setParamsFull(CategoryRule categoryRule, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setInt(index++, categoryRule.getCategory().getId());
		ps.setDate(index++, TypeConverter.toSqlDateLong(categoryRule.getFilterDateFrom()));
		ps.setDate(index++, TypeConverter.toSqlDateLong(categoryRule.getFilterDateTo()));
		ps.setBigDecimal(index++, categoryRule.getFilterAmountFrom());
		ps.setBigDecimal(index++, categoryRule.getFilterAmountTo());
		ps.setString(index++, categoryRule.getFilterRecipient());
		ps.setString(index++, categoryRule.getFilterPurpose());
		ps.setBoolean(index++, categoryRule.isFilterRecipientIsRegex());
		ps.setBoolean(index++, categoryRule.isFilterPurposeIsRegex());
		ps.setString(index++, categoryRule.getJoinType().name());
		ps.setDate(index, TypeConverter.toSqlDateNow());
	}

	@Override
	public CategoryRule toDao(ResultSet rs) throws SQLException {
		CategoryRule categoryRule = new CategoryRule();
		categoryRule.setId(rs.getInt("id"));
		//categoryRule.setCategoryId(rs.getInt("category_id")); TODO Category..
		categoryRule.setFilterDateFrom((TypeConverter.toCalendarFromSqlDate(rs.getDate("filterDateFrom"))));
		categoryRule.setFilterDateTo((TypeConverter.toCalendarFromSqlDate(rs.getDate("filterDateTo"))));
		categoryRule.setFilterAmountFrom(rs.getBigDecimal("filterAmountFrom"));
		categoryRule.setFilterAmountTo(rs.getBigDecimal("filterAmountTo"));
		categoryRule.setFilterRecipient(rs.getString("filterRecipient"));
		categoryRule.setFilterPurpose(rs.getString("filterPurpose"));
		categoryRule.setFilterRecipientIsRegex(rs.getBoolean("filterRecipientIsRegex"));
		categoryRule.setFilterPurposeIsRegex(rs.getBoolean("filterPurposeIsRegex"));
		categoryRule.setJoinType(JoinType.valueOf(rs.getString("joinType")));
		categoryRule.setUpdatedAt((TypeConverter.toCalendarFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));

		return categoryRule;
	}

}
