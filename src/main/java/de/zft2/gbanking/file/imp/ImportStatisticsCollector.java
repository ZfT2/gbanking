package de.zft2.gbanking.file.imp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ImportStatisticsCollector {

	private final Map<String, FileImportBean.ImportAccountStatistics> statisticsByAccount = new LinkedHashMap<>();

	void clear() {
		statisticsByAccount.clear();
	}

	boolean isEmpty() {
		return statisticsByAccount.isEmpty();
	}

	FileImportBean.ImportAccountStatistics forAccount(String accountName, int existingBookings) {
		return statisticsByAccount.computeIfAbsent(accountName, key -> new FileImportBean.ImportAccountStatistics(accountName, existingBookings));
	}

	List<FileImportBean.ImportAccountStatistics> asList() {
		return List.copyOf(statisticsByAccount.values());
	}

	String summary() {
		int accounts = statisticsByAccount.size();
		int added = 0;
		int updated = 0;
		int skipped = 0;
		for (FileImportBean.ImportAccountStatistics statistics : statisticsByAccount.values()) {
			added += statistics.getAddedBookings();
			updated += statistics.getUpdatedBookings();
			skipped += statistics.getSkippedBookings();
		}
		return "accounts=" + accounts + ", added=" + added + ", updated=" + updated + ", skipped=" + skipped;
	}
}
