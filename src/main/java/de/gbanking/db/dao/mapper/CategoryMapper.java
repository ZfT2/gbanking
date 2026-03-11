package de.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.gbanking.db.SqlFields;
import de.gbanking.db.dao.Category;
import de.gbanking.util.TypeConverter;

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
	public Category toDao(ResultSet rs) throws SQLException {
		Category category = new Category(rs.getString("name"));
		category.setId(rs.getInt("id"));
		if (rs.getInt("parent_id") > 0) {
			category.setParentId(rs.getInt("parent_id"));
		}
		if (rs.getString("name") != null) {
			category.setName(rs.getString("name"));
		}
		if (rs.getString("fullName") != null) {
			category.setFullName(rs.getString("fullName"));
		}
		category.setUpdatedAt((TypeConverter.toCalendarFromSqlDate(rs.getDate(SqlFields.DAO_UPDATEDAT))));
		return category;
	}

}
