package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.BookingCategory;
import de.zft2.gbanking.db.dao.enu.CategoryRuleMode;
import de.zft2.gbanking.util.TypeConverter;

public class BookingCategoryMapper extends AbstractDaoMapper<BookingCategory, Void> {

	@Override
	public void setParamsFull(BookingCategory bookingCategory, PreparedStatement ps) throws SQLException {
		int index = 1;
		ps.setInt(index++, bookingCategory.getmTableId());
		ps.setInt(index++, bookingCategory.getnTableId());
		ps.setDate(index++, TypeConverter.toSqlDateNow());
		if (bookingCategory.getId() > 0)
			ps.setInt(index, bookingCategory.getId());
	}

	@Override
	public void mapDao(BookingCategory bookingCategory, ResultType resultType, ResultSet rs) throws SQLException {
		bookingCategory.setmTableId(rs.getInt("booking_id"));
		bookingCategory.setnTableId(rs.getInt("category_id"));
		bookingCategory.setCategoryRuleMode((CategoryRuleMode) CategoryRuleMode.forInt(rs.getInt("categoryRuleMode")));
	}

}
