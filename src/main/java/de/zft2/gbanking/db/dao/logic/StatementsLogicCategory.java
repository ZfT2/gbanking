package de.zft2.gbanking.db.dao.logic;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Category;

public class StatementsLogicCategory extends StatementsLogicDefault<Category> implements StatementsLogic<Category> {
	
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
		if (category.getId() > 0) {
			if (category.getName() == null && category.getFullName() != null) {
				category.setName(category.getFullName());
			}
			return executeInsertUpdateStatement(StatementType.UPDATE, category);
		}
		if (category.getFullName() != null && category.getFullName().contains(":")) {
			String[] subCategories = category.getFullName().split(":");
			Integer parentId = walkSubcategories(subCategories);
			category.setId(parentId);
		} else {
			if (category.getFullName() == null) {
				category.setFullName(category.getName());
			}
			String categoryName = category.getName() != null ? category.getName() : category.getFullName();
			Category existingCategory = find(Category.class, new Category(categoryName.trim(), category.getParentId()));
			if (existingCategory == null) {
				category.setId(insertCategory(new Category(categoryName.trim(), category.getParentId())).getId());
			} else {
				category.setId(existingCategory.getId());
			}
		}
		return category;
	}

	private Integer walkSubcategories(String[] subCategories) {
		Integer parentId = null;
		for (int i = 0; i < subCategories.length; i++) {
			Category existingCategory = find(Category.class, new Category(subCategories[i].trim(), parentId));
			if (existingCategory == null) {
				parentId = insertCategory(new Category(subCategories[i].trim(), parentId)).getId();
			} else {
				parentId = existingCategory.getId();
			}
		}
		return parentId;
	}
	
	private Category insertCategory(Category category) {

		category = executeInsertUpdateStatement(StatementType.INSERT, category);

		return category;
	}

}
