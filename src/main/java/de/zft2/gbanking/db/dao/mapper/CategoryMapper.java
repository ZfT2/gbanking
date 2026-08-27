package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.util.TypeConverter;

public class CategoryMapper extends AbstractDaoMapper<Category, Void> {

	public CategoryMapper() {
		super(() -> new Category((String) null));
	}

	@Override
	public void setParamsFull(Category category, PreparedStatement ps) throws SQLException {
		ps.setString(1, category.getName());
		if (category.getParentId() != null) {
			ps.setInt(2, category.getParentId());
		} else {
			ps.setNull(2, java.sql.Types.INTEGER);
		}
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
		if (category.getId() > 0)
			ps.setInt(4, category.getId());
	}

	@Override
	public void setParamsFind(Category category, PreparedStatement ps) throws SQLException {
		ps.setString(1, category.getName());
		if (category.getParentId() != null) {
			ps.setInt(2, category.getParentId());
		} else {
			ps.setNull(2, java.sql.Types.INTEGER);
		}
	}

	@Override
	void mapDao(Category category, ResultType resultType, ResultSet rs) throws SQLException {

		if (rs.getInt("parent_id") > 0) {
			category.setParentId(rs.getInt("parent_id"));
		}
		String name = rs.getString("name");
		if (name != null) {
			category.setName(name);
		}
		String fullName = rs.getString("fullName");
		category.setFullName(fullName != null ? fullName : name);
	}

}
