package de.zft2.gbanking.hbci;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.enu.TanProcedure;

public final class TanProcedureSupport {

	private static final Pattern TAN2STEP_PARAM_PATTERN = Pattern.compile("(.+\\.TAN2StepParams_\\d+)\\.(secfunc|name)$");
	private static final Pattern TAN_CODE_PATTERN = Pattern.compile("\\d+");

	private TanProcedureSupport() {
	}

	public static List<SupportedTanProcedure> determineSupportedProcedures(BankAccess access) {
		List<TanMechanism> mechanisms = collectMechanisms(access);
		return toSupportedProcedures(mechanisms, fallbackProcedures());
	}

	public static List<SupportedTanProcedure> determineSupportedProcedures(BankAccess access, List<? extends ParameterDataBankAccess> bpd,
			List<? extends ParameterDataBankAccess> upd) {
		List<TanMechanism> mechanisms = collectMechanisms(access, bpd, upd);
		return toSupportedProcedures(mechanisms, fallbackProcedures());
	}

	public static Optional<String> resolveTanMethodCode(BankAccess access) {
		if (access == null || access.getFints().getTanProcedure() == null || access.getFints().getTanProcedure() == TanProcedure.UNKNOWN) {
			return Optional.empty();
		}

		TanProcedure selectedProcedure = access.getFints().getTanProcedure();
		return determineSupportedProcedures(access).stream()
				.filter(supportedProcedure -> selectedProcedure == supportedProcedure.procedure())
				.map(SupportedTanProcedure::code)
				.findFirst()
				.or(() -> selectedProcedure.getCodes().stream().findFirst().map(String::valueOf));
	}

	public static Optional<TanProcedure> resolveProcedureForCode(String code, BankAccess access) {
		Optional<Integer> parsedCode = parseCode(code);
		if (parsedCode.isEmpty()) {
			return Optional.empty();
		}

		int tanCode = parsedCode.get();
		if (access != null && access.getFints().getTanProcedure() != null && access.getFints().getTanProcedure().hasCode(tanCode)) {
			return Optional.of(access.getFints().getTanProcedure());
		}

		return determineSupportedProcedures(access).stream()
				.filter(supportedProcedure -> supportedProcedure.codeAsInt() == tanCode)
				.map(SupportedTanProcedure::procedure)
				.findFirst()
				.or(() -> Optional.ofNullable(TanProcedure.forCode(tanCode)));
	}

	private static List<SupportedTanProcedure> toSupportedProcedures(List<TanMechanism> mechanisms, List<TanProcedure> fallbackProcedures) {
		if (mechanisms.isEmpty()) {
			return fallbackProcedures.stream()
					.map(procedure -> new SupportedTanProcedure(procedure, String.valueOf(procedure.getCode()), procedure.toString()))
					.toList();
		}

		Map<TanProcedure, SupportedTanProcedure> result = new LinkedHashMap<>();
		for (TanMechanism mechanism : mechanisms) {
			for (TanProcedure procedure : TanProcedure.forCodeAndDescription(mechanism.codeAsInt(), mechanism.description())) {
				result.putIfAbsent(procedure, new SupportedTanProcedure(procedure, mechanism.code(), mechanism.description()));
			}
		}
		return new ArrayList<>(result.values());
	}

	private static List<TanProcedure> fallbackProcedures() {
		return List.of(TanProcedure.values()).stream()
				.filter(procedure -> procedure != TanProcedure.UNKNOWN)
				.toList();
	}

	private static List<TanMechanism> collectMechanisms(BankAccess access) {
		return collectMechanisms(access, List.of(), List.of());
	}

	private static List<TanMechanism> collectMechanisms(BankAccess access, List<? extends ParameterDataBankAccess> bpd,
			List<? extends ParameterDataBankAccess> upd) {
		Map<String, TanMechanism> mechanismsByCode = new LinkedHashMap<>();
		Set<String> allowedCodes = collectAllowedCodes(access);

		for (TanMechanism mechanism : collectParameterMechanisms(access, bpd, upd)) {
			if (allowedCodes.isEmpty() || allowedCodes.contains(mechanism.code())) {
				mechanismsByCode.putIfAbsent(mechanism.code(), mechanism);
			}
		}

		if (access != null && access.getFints().getAllowedTwostepMechanisms() != null) {
			for (String allowedMechanism : access.getFints().getAllowedTwostepMechanisms()) {
				Optional<String> code = parseCode(allowedMechanism).map(String::valueOf);
				code.ifPresent(value -> mechanismsByCode.putIfAbsent(value, new TanMechanism(value, extractDescription(allowedMechanism))));
			}
		}

		return new ArrayList<>(mechanismsByCode.values());
	}

	private static List<TanMechanism> collectParameterMechanisms(BankAccess access, List<? extends ParameterDataBankAccess> bpd,
			List<? extends ParameterDataBankAccess> upd) {
		Map<String, TanMechanismBuilder> builders = new LinkedHashMap<>();
		collectParameterMechanisms(builders, access != null ? access.getFints().getBpd() : null);
		collectParameterMechanisms(builders, access != null ? access.getFints().getUpd() : null);
		collectParameterMechanisms(builders, bpd);
		collectParameterMechanisms(builders, upd);

		List<TanMechanism> mechanisms = new ArrayList<>();
		for (TanMechanismBuilder builder : builders.values()) {
			builder.build().ifPresent(mechanisms::add);
		}
		return mechanisms;
	}

	private static void collectParameterMechanisms(Map<String, TanMechanismBuilder> builders, Properties properties) {
		if (properties == null) {
			return;
		}
		for (String key : properties.stringPropertyNames()) {
			collectParameterMechanism(builders, key, properties.getProperty(key));
		}
	}

	private static void collectParameterMechanisms(Map<String, TanMechanismBuilder> builders, List<? extends ParameterDataBankAccess> parameterData) {
		if (parameterData == null) {
			return;
		}
		for (ParameterDataBankAccess data : parameterData) {
			if (data != null) {
				collectParameterMechanism(builders, data.getPdKey(), data.getPdValue());
			}
		}
	}

	private static void collectParameterMechanism(Map<String, TanMechanismBuilder> builders, String key, String value) {
		if (key == null || value == null || value.isBlank()) {
			return;
		}

		Matcher matcher = TAN2STEP_PARAM_PATTERN.matcher(key);
		if (!matcher.matches()) {
			return;
		}

		TanMechanismBuilder builder = builders.computeIfAbsent(matcher.group(1), ignored -> new TanMechanismBuilder());
		String field = matcher.group(2);
		if ("secfunc".equals(field)) {
			parseCode(value).ifPresent(code -> builder.code = String.valueOf(code));
		} else if ("name".equals(field)) {
			builder.description = value.trim();
		}
	}

	private static Set<String> collectAllowedCodes(BankAccess access) {
		Set<String> allowedCodes = new LinkedHashSet<>();
		if (access == null || access.getFints().getAllowedTwostepMechanisms() == null) {
			return allowedCodes;
		}

		for (String allowedMechanism : access.getFints().getAllowedTwostepMechanisms()) {
			parseCode(allowedMechanism).map(String::valueOf).ifPresent(allowedCodes::add);
		}
		return allowedCodes;
	}

	private static Optional<Integer> parseCode(String value) {
		if (value == null) {
			return Optional.empty();
		}

		Matcher matcher = TAN_CODE_PATTERN.matcher(value);
		if (!matcher.find()) {
			return Optional.empty();
		}

		try {
			return Optional.of(Integer.parseInt(matcher.group()));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static String extractDescription(String value) {
		if (value == null) {
			return "";
		}
		int separator = value.indexOf(':');
		if (separator >= 0 && separator + 1 < value.length()) {
			return value.substring(separator + 1).trim();
		}
		return value.trim();
	}

	public record SupportedTanProcedure(TanProcedure procedure, String code, String description) {

		private int codeAsInt() {
			return parseCode(code).orElse(0);
		}

		public boolean requiresConfiguredCardReader() {
			return procedure != null && procedure.requiresConfiguredCardReader();
		}
	}

	private static final class TanMechanismBuilder {

		private String code;
		private String description;

		private Optional<TanMechanism> build() {
			if (code == null || code.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new TanMechanism(code, description));
		}
	}

	private record TanMechanism(String code, String description) {

		private int codeAsInt() {
			return parseCode(code).orElse(0);
		}
	}
}
