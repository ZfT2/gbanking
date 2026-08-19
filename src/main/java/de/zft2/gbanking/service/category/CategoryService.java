package de.zft2.gbanking.service.category;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.booking.BookingCategoryService;

public class CategoryService extends AbstractDbService {

	private static final Logger log = LogManager.getLogger(CategoryService.class);

	private final BookingCategoryService bookingCategoryService;

	public CategoryService() {
		this.bookingCategoryService = ServiceRegistry.getService(BookingCategoryService.class);
	}

	public record CategoryDeleteImpact(int bookingCount, int categoryRuleCount) {
	}

	public void saveCategoryToDB(Category category) {
		dbController.insertOrUpdate(category);
	}

	public CategoryDeleteImpact getCategoryDeleteImpact(Category category) {
		Set<Integer> categoryIds = collectCategoryTreeIds(category);
		if (categoryIds.isEmpty()) {
			return new CategoryDeleteImpact(0, 0);
		}

		int bookingCount = collectBookingIdsForCategories(categoryIds).size();
		int categoryRuleCount = Math.toIntExact(dbController.getAllFull(CategoryRule.class).stream()
				.filter(categoryRule -> categoryRule.getCategory() != null && categoryIds.contains(categoryRule.getCategory().getId())).count());
		return new CategoryDeleteImpact(bookingCount, categoryRuleCount);
	}

	public boolean deleteCategoryFromDB(Category category) {
		log.debug("Category to delete: {}", category.getName());
		Set<Integer> categoryIds = collectCategoryTreeIds(category);
		if (categoryIds.isEmpty()) {
			return false;
		}

		Set<Integer> bookingIds = collectBookingIdsForCategories(categoryIds);
		if (!bookingIds.isEmpty() && dbController.clearBookingCategories(bookingIds) < 0) {
			return false;
		}
		return dbController.delete(category, null);
	}

	private Set<Integer> collectCategoryTreeIds(Category category) {
		if (category == null || category.getId() <= 0) {
			return Set.of();
		}

		Set<Integer> categoryIds = new HashSet<>();
		categoryIds.add(category.getId());
		List<Category> allCategories = dbController.getAll(Category.class);
		boolean foundChild;
		do {
			foundChild = false;
			for (Category candidate : allCategories) {
				if (candidate.getParentId() != null && categoryIds.contains(candidate.getParentId()) && categoryIds.add(candidate.getId())) {
					foundChild = true;
				}
			}
		} while (foundChild);
		return categoryIds;
	}

	private Set<Integer> collectBookingIdsForCategories(Set<Integer> categoryIds) {
		if (categoryIds == null || categoryIds.isEmpty()) {
			return Set.of();
		}
		return dbController.getAllFull(Booking.class).stream().filter(booking -> categoryIds.contains(bookingCategoryService.getBookingCategoryId(booking)))
				.map(Booking::getId).collect(java.util.stream.Collectors.toSet());
	}

}
