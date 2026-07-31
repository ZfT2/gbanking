package de.zft2.gbanking.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Institute;

public final class InstituteLookupCache {

	public record InstituteLookupEntry(String bankName, String bic, int importNumber) {
	}

	private static final AtomicReference<Map<String, List<InstituteLookupEntry>>> ENTRIES_BY_BLZ = new AtomicReference<>();
	private static final AtomicReference<Map<String, List<InstituteLookupEntry>>> ENTRIES_BY_BIC = new AtomicReference<>();

	private InstituteLookupCache() {
	}

	public static List<InstituteLookupEntry> getEntriesForBlz(String blz) {
		if (blz == null || blz.isBlank()) {
			return List.of();
		}
		return getEntriesByBlz().getOrDefault(blz, List.of());
	}

	public static List<InstituteLookupEntry> getEntriesForBic(String bic) {
		String normalizedBic = normalizeBicKey(bic);
		if (normalizedBic == null) {
			return List.of();
		}
		return getEntriesByBic().getOrDefault(normalizedBic, List.of());
	}

	public static List<InstituteLookupEntry> getEntriesForBankCode(String bankCode) {
		String blz = normalizeBlzCandidate(bankCode);
		if (blz != null) {
			return getEntriesForBlz(blz);
		}

		String bic = normalizeBicCandidate(bankCode);
		if (bic != null) {
			return getEntriesForBic(bic);
		}

		return List.of();
	}

	public static Optional<String> findBankNameForBankData(String bic, String blz) {
		return findBankNameForBankCode(blz).or(() -> findBankNameForBankCode(bic));
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

	public static boolean isBlzCandidate(String value) {
		return normalizeBlzCandidate(value) != null;
	}

	public static boolean isBicCandidate(String value) {
		return normalizeBicCandidate(value) != null;
	}

	public static void clear() {
		ENTRIES_BY_BLZ.set(null);
		ENTRIES_BY_BIC.set(null);
	}

	public static String normalizeBlzCandidate(String value) {
		String trimmed = trimToNull(value);
		return trimmed != null && trimmed.matches("\\d{8}") ? trimmed : null;
	}

	public static String normalizeBicCandidate(String value) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			return null;
		}
		String normalizedBic = trimmed.replace(" ", "").toUpperCase(Locale.ROOT);
		return normalizedBic.matches("[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?") ? normalizedBic : null;
	}

	private static Map<String, List<InstituteLookupEntry>> getEntriesByBlz() {
		Map<String, List<InstituteLookupEntry>> cache = ENTRIES_BY_BLZ.get();
		if (cache != null) {
			return cache;
		}
		Map<String, List<InstituteLookupEntry>> loadedEntries = loadEntriesByBlz();
		return ENTRIES_BY_BLZ.compareAndExchange(null, loadedEntries) != null ? ENTRIES_BY_BLZ.get() : loadedEntries;
	}

	private static Map<String, List<InstituteLookupEntry>> getEntriesByBic() {
		Map<String, List<InstituteLookupEntry>> cache = ENTRIES_BY_BIC.get();
		if (cache != null) {
			return cache;
		}
		Map<String, List<InstituteLookupEntry>> loadedEntries = loadEntriesByBic();
		return ENTRIES_BY_BIC.compareAndExchange(null, loadedEntries) != null ? ENTRIES_BY_BIC.get() : loadedEntries;
	}

	private static Map<String, List<InstituteLookupEntry>> loadEntriesByBlz() {
		Map<String, List<Institute>> institutesByBlz = new LinkedHashMap<>();

		for (Institute institute : loadInstitutes()) {
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

	private static Map<String, List<InstituteLookupEntry>> loadEntriesByBic() {
		Map<String, List<Institute>> institutesByBic = new LinkedHashMap<>();

		for (Institute institute : loadInstitutes()) {
			String bic = normalizeBicKey(institute.getBic());
			if (bic == null) {
				continue;
			}
			institutesByBic.computeIfAbsent(bic, key -> new ArrayList<>()).add(institute);
		}

		Map<String, List<InstituteLookupEntry>> lookupByBic = new LinkedHashMap<>();
		for (Map.Entry<String, List<Institute>> entry : institutesByBic.entrySet()) {
			List<Institute> institutesForBic = new ArrayList<>(entry.getValue());
			institutesForBic.sort(Comparator.comparingInt(Institute::getImportNumber));
			lookupByBic.put(entry.getKey(), buildLookupEntries(institutesForBic));
		}

		return lookupByBic;
	}

	private static List<Institute> loadInstitutes() {
		return DBController.getInstance(".").getAll(Institute.class);
	}

	private static Optional<String> findBankNameForBankCode(String bankCode) {
		return getEntriesForBankCode(bankCode).stream()
				.map(InstituteLookupEntry::bankName)
				.map(InstituteLookupCache::trimToNull)
				.filter(Objects::nonNull)
				.findFirst();
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

	private static String normalizeBicKey(String bic) {
		String trimmed = trimToNull(bic);
		return trimmed != null ? trimmed.replace(" ", "").toUpperCase(Locale.ROOT) : null;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
