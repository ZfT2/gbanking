package de.zft2.gbanking.db.dao;

import de.zft2.gbanking.db.dao.enu.CategoryRuleMode;

public class BookingCategory extends MnFieldDao<CategoryRuleMode> {

	public BookingCategory(int bookingId, int categoryId, CategoryRuleMode categoryRuleMode) {
		super(bookingId, categoryId);
		this.mnField = categoryRuleMode;
	}

	public BookingCategory() {
		// TODO Auto-generated constructor stub
	}

	public CategoryRuleMode getCategoryRuleMode() {
		return mnField;
	}

	public void setCategoryRuleMode(CategoryRuleMode categoryRuleMode) {
		this.mnField = categoryRuleMode;
	}

}
