package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.util.TypeConverter;

public class CategoryMapper extends AbstractDaoMapper<Category, Void> {

	@Override
	public void setParamsFull(Category category, PreparedStatement ps) throws SQLException {
		ps.setString(1, category.getName());
		if (category.getParentId() != null) {
			ps.setInt(2, category.getParentId());
		}
		ps.setDate(3, TypeConverter.toSqlDateNow());
		if (category.getId() > 0)
			ps.setInt(4, category.getId());
	}

	@Override
	public void setParamsFind(Category category, PreparedStatement ps) throws SQLException {
		ps.setString(1, category.getName());
		if (category.getParentId() != null)
			ps.setInt(2, category.getParentId());
	}

	@Override
	Category initResultDao(Class<Category> type, ResultSet rs) throws SQLException {
		Category category = new Category(rs.getString("name"));
		initDefaultFields(category, rs);
		return category;
	}

	@Override
	void mapDao(Category category, ResultType resultType, ResultSet rs) throws SQLException {

		if (rs.getInt("parent_id") > 0) {
			category.setParentId(rs.getInt("parent_id"));
		}
		if (rs.getString("name") != null) {
			category.setName(rs.getString("name"));
		}
		if (rs.getString("fullName") != null) {
			category.setFullName(rs.getString("fullName"));
		}
	}

}
