package de.zft2.gbanking.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Institute;

public final class InstituteLookupCache {

	public record InstituteLookupEntry(String bankName, String bic, int importNumber) {
	}

	private static final AtomicReference<Map<String, List<InstituteLookupEntry>>> ENTRIES_BY_BLZ = new AtomicReference<>();

	private InstituteLookupCache() {
	}

	public static List<InstituteLookupEntry> getEntriesForBlz(String blz) {
		if (blz == null || blz.isBlank()) {
			return List.of();
		}
		return getEntriesByBlz().getOrDefault(blz, List.of());
	}

	public static String extractGermanBlzFromIban(String iban) {
		if (iban == null) {
			return null;
		}

		String normalizedIban = iban.replace(" ", "").trim();
		if (normalizedIban.length() < 12 || !normalizedIban.regionMatches(true, 0, "DE", 0, 2)) {
			return null;
		}
		return normalizedIban.substring(4, 12);
	}

	public static void clear() {
		ENTRIES_BY_BLZ.set(null);
	}

	private static Map<String, List<InstituteLookupEntry>> getEntriesByBlz() {
		Map<String, List<InstituteLookupEntry>> cache = ENTRIES_BY_BLZ.get();
		if (cache != null) {
			return cache;
		}
		Map<String, List<InstituteLookupEntry>> loadedEntries = loadEntriesByBlz();
		return ENTRIES_BY_BLZ.compareAndExchange(null, loadedEntries) != null ? ENTRIES_BY_BLZ.get() : loadedEntries;
	}

	private static Map<String, List<InstituteLookupEntry>> loadEntriesByBlz() {
		List<Institute> institutes = DBController.getInstance(".").getAll(Institute.class);
		Map<String, List<Institute>> institutesByBlz = new LinkedHashMap<>();

		for (Institute institute : institutes) {
			String blz = trimToNull(institute.getBlz());
			if (blz == null) {
				continue;
			}
			institutesByBlz.computeIfAbsent(blz, key -> new ArrayList<>()).add(institute);
		}

		Map<String, List<InstituteLookupEntry>> lookupByBlz = new LinkedHashMap<>();
		for (Map.Entry<String, List<Institute>> entry : institutesByBlz.entrySet()) {
			List<Institute> institutesForBlz = new ArrayList<>(entry.getValue());
			institutesForBlz.sort(Comparator.comparingInt(Institute::getImportNumber));
			lookupByBlz.put(entry.getKey(), buildLookupEntries(institutesForBlz));
		}

		return lookupByBlz;
	}

	private static List<InstituteLookupEntry> buildLookupEntries(List<Institute> institutesForBlz) {
		Map<String, InstituteLookupEntry> uniqueEntriesByBankName = new LinkedHashMap<>();

		for (Institute institute : institutesForBlz) {
			String bankName = trimToNull(institute.getBankName());
			String bic = trimToNull(institute.getBic());
			String uniqueKey = Objects.toString(bankName, "");
			uniqueEntriesByBankName.computeIfAbsent(uniqueKey,
					key -> new InstituteLookupEntry(bankName, bic, institute.getImportNumber()));
		}

		return List.copyOf(uniqueEntriesByBankName.values());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
