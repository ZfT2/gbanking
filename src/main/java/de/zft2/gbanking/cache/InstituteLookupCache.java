package de.zft2.gbanking.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.InstituteBankLookup;

public final class InstituteLookupCache {
	private static final int BIC8_LENGTH = 8;
	private static final int BIC11_LENGTH = 11;

	public record InstituteLookupEntry(String bankName, String bic) {
	}

	private record LookupIndex(Map<String, List<InstituteLookupEntry>> byBlz, Map<String, List<InstituteLookupEntry>> byBic) {
	}

	private static final AtomicReference<LookupIndex> LOOKUP_INDEX = new AtomicReference<>();

	private InstituteLookupCache() {
	}

	public static List<InstituteLookupEntry> getEntriesForBlz(String blz) {
		if (blz == null || blz.isBlank()) {
			return List.of();
		}
		return getLookupIndex().byBlz().getOrDefault(blz.trim(), List.of());
	}

	public static List<InstituteLookupEntry> getEntriesForBic(String bic) {
		String normalizedBic = normalizeBicKey(bic);
		if (normalizedBic == null) {
			return List.of();
		}
		return getLookupIndex().byBic().getOrDefault(normalizedBic, List.of());
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

	public static Optional<String> findBicForBlz(String blz) {
		return getEntriesForBlz(blz).stream()
				.map(entry -> entry.bic())
				.map(value -> trimToNull(value))
				.filter(Objects::nonNull)
				.findFirst();
	}

	public static String extractGermanBlzFromIban(String iban) {
		String normalizedIban = normalizeGermanIban(iban);
		return normalizedIban != null ? normalizedIban.substring(4, 12) : null;
	}

	public static String extractGermanAccountNumberFromIban(String iban) {
		String normalizedIban = normalizeGermanIban(iban);
		return normalizedIban != null ? normalizedIban.substring(12) : null;
	}

	public static boolean isBlzCandidate(String value) {
		return normalizeBlzCandidate(value) != null;
	}

	public static boolean isBicCandidate(String value) {
		return normalizeBicCandidate(value) != null;
	}

	public static void clear() {
		LOOKUP_INDEX.set(null);
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

	private static LookupIndex getLookupIndex() {
		LookupIndex cached = LOOKUP_INDEX.get();
		if (cached != null) {
			return cached;
		}
		List<InstituteBankLookup> banks = DBController.getInstance(".").getInstituteBankLookup();
		LookupIndex loaded = new LookupIndex(buildLookupEntries(banks, bank -> bank.blz()),
				buildLookupEntries(banks, bank -> normalizeBicKey(bank.bic())));
		LookupIndex previous = LOOKUP_INDEX.compareAndExchange(null, loaded);
		return previous != null ? previous : loaded;
	}

	private static Map<String, List<InstituteLookupEntry>> buildLookupEntries(List<InstituteBankLookup> banks,
			Function<InstituteBankLookup, String> keyFunction) {
		Map<String, Map<String, InstituteLookupEntry>> grouped = new LinkedHashMap<>();
		// Preserve the view query's source priority, including for BICs shared by several BLZ.
		for (InstituteBankLookup bank : banks) {
			String key = keyFunction.apply(bank);
			String bankName = trimToNull(bank.bankName());
			if (key != null && bankName != null) {
				grouped.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
						.putIfAbsent(bankName, new InstituteLookupEntry(bankName, bank.bic()));
			}
		}
		Map<String, List<InstituteLookupEntry>> entries = new LinkedHashMap<>();
		grouped.forEach((key, names) -> entries.put(key, List.copyOf(names.values())));
		return Map.copyOf(entries);
	}

	private static Optional<String> findBankNameForBankCode(String bankCode) {
		return getEntriesForBankCode(bankCode).stream()
				.map(InstituteLookupEntry::bankName)
				.map(InstituteLookupCache::trimToNull)
				.filter(Objects::nonNull)
				.findFirst();
	}

	private static String normalizeBicKey(String bic) {
		String trimmed = trimToNull(bic);
		if (trimmed == null) {
			return null;
		}
		String normalizedBic = trimmed.replace(" ", "").toUpperCase(Locale.ROOT);
		return normalizedBic.length() == BIC11_LENGTH && normalizedBic.endsWith("XXX")
				? normalizedBic.substring(0, BIC8_LENGTH) : normalizedBic;
	}

	private static String normalizeGermanIban(String iban) {
		String normalizedIban = trimToNull(iban);
		if (normalizedIban == null) {
			return null;
		}
		normalizedIban = normalizedIban.replace(" ", "").toUpperCase(Locale.ROOT);
		return normalizedIban.matches("DE\\d{20}") ? normalizedIban : null;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
