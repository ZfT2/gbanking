package de.zft2.gbanking.db.dao.logic;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.mapper.AbstractDaoMapper;
import de.zft2.gbanking.exception.GBankingException;

public class StatementsLogicCategory extends StatementsLogicDefault<Category> implements StatementsLogic<Category> {
	
	private static final Logger log = LogManager.getLogger(StatementsLogicCategory.class);

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
		if (categoryList == null || categoryList.isEmpty()) {
			return Set.of();
		}

		Map<CategoryKey, Category> persistedCategories = loadCategoryIndex();
		AbstractDaoMapper<Category, Void> mapper = StatementsConfig.getMapperForDaoType(Category.class);
		String updateSql = StatementsConfig.getSqlStatement(Category.class, StatementType.UPDATE);
		List<Category> updates = new ArrayList<>();
		try {
			categoryList.stream()
					.sorted(Comparator.comparingInt(StatementsLogicCategory::pathDepth)
							.thenComparing(StatementsLogicCategory::pathValue, String.CASE_INSENSITIVE_ORDER))
					.forEach(category -> persistCategory(category, persistedCategories, updates));
			if (!updates.isEmpty()) {
				executeCachedBatchExactlyOnce(updateSql, updates,
						(category, statement) -> mapper.setParamsFull(category, statement));
			}
			return categoryList;
		} catch (SQLException exception) {
			throw new GBankingException("Error saving category batch", exception);
		}
	}

	private Map<CategoryKey, Category> loadCategoryIndex() {
		Map<CategoryKey, Category> categories = new HashMap<>();
		for (Category category : getAll(Category.class)) {
			categories.put(categoryKey(category.getParentId(), category.getName()), category);
		}
		return categories;
	}

	private void persistCategory(Category category, Map<CategoryKey, Category> persistedCategories,
			List<Category> updates) {
		if (category.getId() > 0) {
			normalizePersistedCategory(category);
			updates.add(category);
			persistedCategories.put(categoryKey(category.getParentId(), category.getName()), category);
			return;
		}

		if (category.getFullName() == null && category.getName() != null) {
			category.setFullName(category.getName());
		}
		Integer parentId = null;
		StringBuilder fullName = new StringBuilder();
		String[] path = pathValue(category).split(":");
		for (String rawName : path) {
			String name = rawName.trim();
			if (name.isEmpty()) {
				continue;
			}
			if (!fullName.isEmpty()) {
				fullName.append(':');
			}
			fullName.append(name);
			CategoryKey key = categoryKey(parentId, name);
			Category persisted = persistedCategories.get(key);
			if (persisted == null) {
				persisted = new Category(name, parentId);
				persisted.setFullName(fullName.toString());
				executeInsertUpdateStatement(StatementType.INSERT, persisted);
				persistedCategories.put(key, persisted);
			}
			parentId = persisted.getId();
		}
		if (parentId == null) {
			throw new GBankingException("Category name must not be empty");
		}
		category.setId(parentId);
	}

	private static void normalizePersistedCategory(Category category) {
		if (category.getName() == null && category.getFullName() != null) {
			category.setName(category.getFullName());
		}
	}

	private static String pathValue(Category category) {
		String path = category.getFullName() != null ? category.getFullName() : category.getName();
		return path != null ? path.trim() : "";
	}

	private static int pathDepth(Category category) {
		String path = pathValue(category);
		return path.isEmpty() ? 0 : path.split(":").length;
	}

	private static CategoryKey categoryKey(Integer parentId, String name) {
		String normalizedName = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
		return new CategoryKey(parentId, normalizedName);
	}

	private record CategoryKey(Integer parentId, String normalizedName) {
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
