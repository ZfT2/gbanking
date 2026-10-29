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
		setIntegerNullable(2, category.getParentId(), ps);
		ps.setTimestamp(3, TypeConverter.toSqlTimestampNow());
		if (category.getId() > 0)
			ps.setInt(4, category.getId());
	}

	@Override
	public void setParamsFind(Category category, PreparedStatement ps) throws SQLException {
		ps.setString(1, category.getName());
		setIntegerNullable(2, category.getParentId(), ps);
	}

	@Override
	void mapDao(Category category, ResultType resultType, ResultSet rs) throws SQLException {

		int parentId = rs.getInt("parent_id");
		if (parentId > 0) {
			category.setParentId(parentId);
		}
		String name = rs.getString("name");
		if (name != null) {
			category.setName(name);
		}
		String fullName = rs.getString("fullName");
		category.setFullName(fullName != null ? fullName : name);
	}

}
