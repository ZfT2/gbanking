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

	public CategoryRuleMapper() {
		super(CategoryRule::new);
	}

	@Override
	public void setParamsFull(CategoryRule categoryRule, PreparedStatement ps) throws SQLException {
		int index = 1;

		ps.setString(index++, categoryRule.getName());
		ps.setInt(index++, categoryRule.getCategory().getId());
		ps.setDate(index++, TypeConverter.toSqlDateLong(categoryRule.getFilterDateFrom()));
		ps.setDate(index++, TypeConverter.toSqlDateLong(categoryRule.getFilterDateTo()));
		ps.setBigDecimal(index++, categoryRule.getFilterAmountFrom());
		ps.setBigDecimal(index++, categoryRule.getFilterAmountTo());
		ps.setString(index++, categoryRule.getFilterRecipientName());
		ps.setString(index++, categoryRule.getFilterRecipientIban());
		ps.setString(index++, categoryRule.getFilterRecipientAccountNumber());
		ps.setString(index++, categoryRule.getFilterPurpose());
		ps.setBoolean(index++, categoryRule.isFilterRecipientIsRegex());
		ps.setBoolean(index++, categoryRule.isFilterPurposeIsRegex());
		ps.setInt(index++, categoryRule.getJoinType().getDbStateId());
		ps.setTimestamp(index++, TypeConverter.toSqlTimestampNow());
		if (categoryRule.getId() > 0) {
			ps.setInt(index, categoryRule.getId());
		}
	}

	@Override
	public void mapDao(CategoryRule categoryRule, ResultType resultType, ResultSet rs) throws SQLException {
		categoryRule.setName(rs.getString("name"));
		int categoryId = rs.getInt("category_id");
		if (categoryId > 0) {
			Category category = new Category(categoryId, rs.getString("fullName"));
			category.setName(rs.getString("categoryName"));
			categoryRule.setCategory(category);
		}
		categoryRule.setFilterDateFrom((TypeConverter.toLocalDateFromSqlDate(rs.getDate("filterDateFrom"))));
		categoryRule.setFilterDateTo((TypeConverter.toLocalDateFromSqlDate(rs.getDate("filterDateTo"))));
		categoryRule.setFilterAmountFrom(rs.getBigDecimal("filterAmountFrom"));
		categoryRule.setFilterAmountTo(rs.getBigDecimal("filterAmountTo"));
		categoryRule.setFilterRecipientName(rs.getString("filterRecipientName"));
		categoryRule.setFilterRecipientIban(rs.getString("filterRecipientIban"));
		categoryRule.setFilterRecipientAccountNumber(rs.getString("filterRecipientAccountNumber"));
		categoryRule.setFilterPurpose(rs.getString("filterPurpose"));
		categoryRule.setFilterRecipientIsRegex(rs.getBoolean("filterRecipientIsRegex"));
		categoryRule.setFilterPurposeIsRegex(rs.getBoolean("filterPurposeIsRegex"));
		categoryRule.setJoinType(JoinType.forInt(rs.getInt("joinType")));
	}
}
