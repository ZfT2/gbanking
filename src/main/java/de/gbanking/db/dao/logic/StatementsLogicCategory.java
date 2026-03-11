package de.gbanking.db.dao.logic;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.Category;

public class StatementsLogicCategory extends StatementsLogicDefault<Category, Void> implements StatementsLogic<Category, Void> {
	
	private static Logger log = LogManager.getLogger(StatementsLogicCategory.class);

	@Override
	public SqlParameter getSqlParameter(Category cg) {
		return new SqlParameter(cg.getFullName(), null);
	}
	
	@Override
	public Category insertOrUpdateSingle(Category category) {
		log.debug("insertOrUpdate()");
		return insertSubCategory(category);
	}
	
	@Override
	public Set<Category> insertAll(Set<Category> categoryList) {
		for (Category entity : categoryList) {
				insertSubCategory(entity);
			}
		return categoryList;
		}
	
	private Category insertSubCategory(Category category) {
		if (category.getFullName() != null && category.getFullName().contains(":")) {
			String[] subCategories = category.getFullName().split(":");
			Integer parentId = null;
			for (int i = 0; i < subCategories.length; i++) {
				Category existingCategory = find(Category.class, new Category(subCategories[i], parentId));
				if (existingCategory == null) {
					parentId = insertCategory(new Category(subCategories[i], parentId)).getId();
				} else {
					parentId = existingCategory.getId();
				}
			}
			category.setId(parentId);
		} else {
			if (category.getFullName() == null)
				category.setFullName(category.getName());
			int id = insertCategory(new Category(category.getFullName(), null)).getId();
			category.setId(id);
		}
		return category;
	}
	
	private Category insertCategory(Category category) {

		category = executeInsertUpdateStatement(StatementType.INSERT, category);

		return category;
	}

}
