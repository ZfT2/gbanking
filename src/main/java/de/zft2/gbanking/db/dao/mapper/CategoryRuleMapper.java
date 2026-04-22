package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.CategoryRule.JoinType;
import de.zft2.gbanking.util.TypeConverter;

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
	public void mapDao(CategoryRule categoryRule, ResultType resultType, ResultSet rs) throws SQLException {
		int categoryId = rs.getInt("category_id");
		if (categoryId > 0) {
			String fullName = hasColumn(rs, "fullName") ? rs.getString("fullName") : null;
			Category category = new Category(categoryId, fullName);
			if (hasColumn(rs, "name")) {
				category.setName(rs.getString("name"));
			}
			categoryRule.setCategory(category);
		}
		categoryRule.setFilterDateFrom((TypeConverter.toLocalDateFromSqlDate(rs.getDate("filterDateFrom"))));
		categoryRule.setFilterDateTo((TypeConverter.toLocalDateFromSqlDate(rs.getDate("filterDateTo"))));
		categoryRule.setFilterAmountFrom(rs.getBigDecimal("filterAmountFrom"));
		categoryRule.setFilterAmountTo(rs.getBigDecimal("filterAmountTo"));
		categoryRule.setFilterRecipient(rs.getString("filterRecipient"));
		categoryRule.setFilterPurpose(rs.getString("filterPurpose"));
		categoryRule.setFilterRecipientIsRegex(rs.getBoolean("filterRecipientIsRegex"));
		categoryRule.setFilterPurposeIsRegex(rs.getBoolean("filterPurposeIsRegex"));
		categoryRule.setJoinType(JoinType.valueOf(rs.getString("joinType")));
	}
}
