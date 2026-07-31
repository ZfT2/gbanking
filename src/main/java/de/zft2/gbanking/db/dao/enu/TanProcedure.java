package de.zft2.gbanking.db.dao.enu;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.enu.LocalizedEnumValue;

public enum TanProcedure implements IdType, LocalizedEnumValue {
	
	UNKNOWN(List.of(), 2),
	I_TAN(List.of(900, 912, 996, 997), List.of("itan", "i tan", "indizierte tan"), 3),
	M_TAN(List.of(901, 930, 942, 996), List.of("mobiletan", "mobile tan", "mtan", "m tan"), 4),
	SMS_TAN(List.of(903, 920, 930), List.of("smstan", "sms tan", "sms"), 5),
	APP_TAN(List.of(931, 940, 997, 999), List.of("app tan", "app"), true, 6),
	APP_SECUREGO_PLUS(List.of(946), List.of("securego plus", "securego+"), 7),
	CHIP_TAN(List.of(901, 904, 910, 911, 912, 913, 921, 962, 972, 994, 995),
			List.of("chiptan", "chip tan", "smarttan", "smart tan"), true, 8),
	PHOTO_TAN(List.of(900, 902, 903, 932),
			List.of("phototan", "photo tan", "photo", "secureplus", "secure plus", "cronto", "apotan"), 9),
	QR_TAN(List.of(913), List.of("qr", "qr tan", "chiptan qr", "chip tan qr"), 10),
	CHIP_TAN_MANUAL(List.of(910, 911, 913, 962, 994), List.of("manuell", "manual"), 11),
	CHIP_TAN_OPTICAL(List.of(902, 910, 911, 912, 921, 972, 995),
			List.of("optisch", "optical", "flicker", "flickercode", "hhd", "optisch usb", "chiptan optisch usb", "chip tan optisch usb"), 12),
	CHIP_TAN_USB(List.of(912, 972),
			List.of("usb", "kartenleser", "card reader", "optisch usb", "chiptan optisch usb", "chip tan optisch usb"), true, false, 13),
	PUSH_TAN(List.of(904, 921, 931), List.of("pushtan", "push tan", "push"), 14),
	BESTSIGN(List.of(920, 921), List.of("bestsign", "best sign", "bestsign push", "best sign push"), 15),
	TAN2GO(List.of(921), List.of("tan2go", "tan 2 go"), 16),
	DECOUPLED(List.of(903, 940), List.of("decoupled", "freigabe", "app freigabe", "dkb app"), 17),
	APP_SECUREGO(List.of(944), List.of("securego"), 18),
	SMART_TAN_PHOTO(List.of(982), List.of("smart tan photo", "smarttan photo", "farbcode"), 19),
	LEGACY_PIN_TAN(List.of(999), List.of("pin tan", "ein schritt", "einstufig"), 20);

	public static TanProcedure forCode(int value) {
		for (TanProcedure x : values()) {
			if (x.hasCode(value))
				return x;
		}
		return null;
	}

	public static List<TanProcedure> forCodeAndDescription(int code, String description) {
		List<TanProcedure> candidates = Arrays.stream(values()).filter(procedure -> procedure.hasCode(code)).toList();
		if (candidates.isEmpty()) {
			return List.of();
		}

		int maxScore = candidates.stream().mapToInt(procedure -> procedure.descriptionMatchScore(description)).max().orElse(0);
		List<TanProcedure> matches = maxScore > 0 ? candidates.stream().filter(procedure -> procedure.descriptionMatchScore(description) == maxScore).toList()
				: List.of();
		if (matches.isEmpty()) {
			return candidates;
		}

		List<TanProcedure> specificMatches = matches.stream().filter(procedure -> !procedure.genericProcedure).toList();
		return specificMatches.isEmpty() ? matches : specificMatches;
	}

	private final List<Integer> codes;
	private final boolean requiresConfiguredCardReader;
	private final boolean genericProcedure;
	private final List<String> matchTerms;
	private final int dbStateId;

	private TanProcedure(List<Integer> codes, int dbStateId) {
		this(codes, List.of(), false, false, dbStateId);
	}

	private TanProcedure(List<Integer> codes, List<String> matchTerms, int dbStateId) {
		this(codes, matchTerms, false, false, dbStateId);
	}

	private TanProcedure(List<Integer> codes, List<String> matchTerms, boolean genericProcedure, int dbStateId) {
		this(codes, matchTerms, false, genericProcedure, dbStateId);
	}

	private TanProcedure(List<Integer> codes, List<String> matchTerms, boolean requiresConfiguredCardReader, boolean genericProcedure,
			int dbStateId) {
		this.codes = List.copyOf(codes);
		this.requiresConfiguredCardReader = requiresConfiguredCardReader;
		this.genericProcedure = genericProcedure;
		this.matchTerms = matchTerms.stream().map(value -> normalize(value)).toList();
		this.dbStateId = dbStateId;
	}

	public static TanProcedure forInt(int intValue) {
		return IdType.forId(TanProcedure.class, intValue);
	}

	public int getCode() {
		return codes.stream().findFirst().orElse(0);
	}

	public List<Integer> getCodes() {
		return codes;
	}

	public boolean hasCode(int code) {
		return codes.contains(code);
	}

	public boolean requiresConfiguredCardReader() {
		return requiresConfiguredCardReader;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		if (codes.isEmpty()) {
			return getDisplayName();
		}
		return getDisplayName() + " (" + codes.stream().map(value -> String.valueOf(value)).collect(Collectors.joining(", ")) + ")";
	}

	private int descriptionMatchScore(String value) {
		String normalizedValue = normalize(value);
		if (normalizedValue.isBlank()) {
			return 0;
		}
		int score = Math.max(localizedDescriptionMatchScore(normalizedValue, getGermanName()),
				localizedDescriptionMatchScore(normalizedValue, getEnglishName()));
		for (String matchTerm : matchTerms) {
			if (normalizedValue.contains(matchTerm)) {
				score = Math.max(score, matchTerm.length());
			}
		}
		return score;
	}

	private int localizedDescriptionMatchScore(String normalizedValue, String localizedDescription) {
		String normalizedDescription = normalize(localizedDescription);
		return normalizedValue.contains(normalizedDescription) ? normalizedDescription.length() : 0;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

}
