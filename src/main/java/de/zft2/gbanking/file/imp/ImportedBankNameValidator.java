package de.zft2.gbanking.file.imp;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

final class ImportedBankNameValidator {

	private static final Pattern PARENTHESIZED_TEXT = Pattern.compile("\\(([^)]+)\\)");
	private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^A-Z0-9]+");
	private static final Pattern DIACRITIC_MARKS = Pattern.compile("\\p{M}+");
	private static final Set<String> LEGAL_FORM_TOKENS = Set.of("AG", "AKTIENGESELLSCHAFT", "BA", "BV", "CO", "EG", "GMBH", "KG",
			"KGAA", "LIMITED", "LTD", "MBH", "NV", "PLC", "SA", "SCA", "SE");
	private static final Set<String> REGIONAL_BANK_TYPES = Set.of("RAIFFEISENBANK", "SPARKASSE", "VOLKSBANK");
	private static final Set<String> REGIONAL_GENERIC_TOKENS = Set.of("AM", "AN", "BANCA", "BANCO", "BANK", "BANKEN", "DER", "DIE",
			"FUR", "IM", "IN", "KREIS", "MEINE", "PUR", "REGION", "STADT", "THE", "UND", "VOM", "VON", "ZU");
	private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator.comparingInt(Candidate::sourcePriority)
			.thenComparingLong(Candidate::dateDistance).thenComparing(Candidate::sameRecipient, Comparator.reverseOrder())
			.thenComparing(Candidate::activeInstitute, Comparator.reverseOrder()).thenComparing(Candidate::occurrences, Comparator.reverseOrder())
			.thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER);

	private final DBController dbController;

	ImportedBankNameValidator(DBController dbController) {
		this.dbController = dbController;
	}

	List<ImportedBankNameFinding> validate(Collection<Booking> importedBookings) {
		if (importedBookings == null || importedBookings.isEmpty()) {
			return List.of();
		}
		Set<Integer> importedIds = importedBookings.stream().map(Booking::getId).collect(Collectors.toSet());
		List<Booking> existingBookings = dbController.getAllFull(Booking.class).stream()
				.filter(booking -> !importedIds.contains(booking.getId())).toList();
		return validate(importedBookings, existingBookings, dbController.getAll(Institute.class));
	}

	List<ImportedBankNameFinding> validate(Collection<Booking> importedBookings, Collection<Booking> existingBookings,
			Collection<Institute> institutes) {
		InstituteIndex instituteIndex = new InstituteIndex(institutes);
		List<Evidence> existingEvidence = evidenceFrom(existingBookings, EvidenceSource.EXISTING, instituteIndex);
		List<Evidence> importEvidence = evidenceFrom(importedBookings, EvidenceSource.CURRENT_IMPORT, instituteIndex);
		Map<BankIdentifier, List<Evidence>> trustedEvidence = trustedEvidence(existingEvidence, importEvidence, instituteIndex).stream()
				.collect(Collectors.groupingBy(Evidence::identifier));
		List<ImportedBankNameFinding> findings = new ArrayList<>();

		for (Booking booking : importedBookings) {
			Recipient recipient = booking.getRecipient();
			BankIdentifier identifier = instituteIndex.identifierFor(recipient);
			String currentBankName = recipient != null ? trimToNull(recipient.getBank()) : null;
			if (identifier == null || currentBankName == null) {
				continue;
			}
			List<Evidence> relevantEvidence = trustedEvidence.getOrDefault(identifier, List.of());
			if (instituteIndex.matchesExpected(identifier, currentBankName) || matchesEvidence(currentBankName, relevantEvidence)) {
				continue;
			}
			if (!instituteIndex.isConfidentMismatch(identifier, currentBankName)
					|| !hasAlternativeEvidence(currentBankName, relevantEvidence)) {
				continue;
			}

			List<String> candidates = candidatesFor(booking, identifier, relevantEvidence, instituteIndex);
			if (!candidates.isEmpty()) {
				findings.add(toFinding(booking, recipient, candidates));
			}
		}
		return List.copyOf(findings);
	}

	private List<Evidence> evidenceFrom(Collection<Booking> bookings, EvidenceSource source, InstituteIndex instituteIndex) {
		if (bookings == null) {
			return List.of();
		}
		List<Evidence> evidence = new ArrayList<>();
		for (Booking booking : bookings) {
			Recipient recipient = booking.getRecipient();
			BankIdentifier identifier = instituteIndex.identifierFor(recipient);
			String bankName = recipient != null ? trimToNull(recipient.getBank()) : null;
			if (identifier != null && bankName != null) {
				evidence.add(new Evidence(identifier, RecipientKey.from(recipient), bankName, booking.getDate(), source));
			}
		}
		return evidence;
	}

	private List<Evidence> trustedEvidence(List<Evidence> existingEvidence, List<Evidence> importEvidence,
			InstituteIndex instituteIndex) {
		List<Evidence> allEvidence = new ArrayList<>(existingEvidence);
		allEvidence.addAll(importEvidence);
		Map<EvidenceKey, Long> occurrences = allEvidence.stream().map(EvidenceKey::from)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		return allEvidence.stream().filter(evidence -> instituteIndex.matchesExpected(evidence.identifier(), evidence.bankName())
				|| (occurrences.getOrDefault(EvidenceKey.from(evidence), 0L) > 1
						&& !instituteIndex.isConfidentMismatch(evidence.identifier(), evidence.bankName())))
				.toList();
	}

	private boolean matchesEvidence(String bankName, List<Evidence> evidence) {
		return evidence.stream().anyMatch(item -> bankNamesMatch(bankName, item.bankName()));
	}

	private boolean hasAlternativeEvidence(String bankName, List<Evidence> evidence) {
		return evidence.stream().anyMatch(item -> !bankNamesMatch(bankName, item.bankName()));
	}

	private List<String> candidatesFor(Booking booking, BankIdentifier identifier, List<Evidence> evidence,
			InstituteIndex instituteIndex) {
		Map<String, Candidate> candidates = new HashMap<>();
		RecipientKey recipientKey = RecipientKey.from(booking.getRecipient());
		addEvidence(candidates, evidence, recipientKey, booking.getDate());
		for (OfficialName officialName : instituteIndex.candidateNamesFor(identifier)) {
			addCandidate(candidates, officialName.name(), Long.MAX_VALUE, EvidenceSource.INSTITUTE.priority(), false,
					officialName.active());
		}
		return candidates.values().stream().sorted(CANDIDATE_ORDER).map(Candidate::name).toList();
	}

	private void addEvidence(Map<String, Candidate> candidates, List<Evidence> evidence, RecipientKey recipientKey,
			LocalDate bookingDate) {
		for (Evidence item : evidence) {
			addCandidate(candidates, item.bankName(), dateDistance(bookingDate, item.date()), item.source().priority(),
					recipientKey.equals(item.recipientKey()), false);
		}
	}

	private void addCandidate(Map<String, Candidate> candidates, String name, long dateDistance, int sourcePriority,
			boolean sameRecipient, boolean activeInstitute) {
		String key = primaryAlias(name);
		if (key != null) {
			candidates.computeIfAbsent(key, ignored -> new Candidate(name)).add(name, dateDistance, sourcePriority, sameRecipient,
					activeInstitute);
		}
	}

	private ImportedBankNameFinding toFinding(Booking booking, Recipient recipient, List<String> candidates) {
		return new ImportedBankNameFinding(booking.getId(), booking.getDate(), booking.getAccountName(), booking.getPurpose(), booking.getAmount(),
				recipient.getName(), firstNonBlank(recipient.getIban(), recipient.getAccountNumber()),
				firstNonBlank(recipient.getBlz(), recipient.getBic()), recipient.getBank(), candidates, candidates.get(0));
	}

	private static long dateDistance(LocalDate first, LocalDate second) {
		return first != null && second != null ? Math.abs(ChronoUnit.DAYS.between(first, second)) : Long.MAX_VALUE;
	}

	private static String firstNonBlank(String first, String second) {
		String firstValue = trimToNull(first);
		return firstValue != null ? firstValue : trimToNull(second);
	}

	private static String normalizedText(String value) {
		String text = trimToNull(value);
		return text != null ? text.toUpperCase(Locale.ROOT) : null;
	}

	private enum EvidenceSource {
		EXISTING(0), CURRENT_IMPORT(1), INSTITUTE(2);

		private final int priority;

		EvidenceSource(int priority) {
			this.priority = priority;
		}

		private int priority() {
			return priority;
		}
	}

	private enum BankIdentifierType {
		BLZ, BIC
	}

	private record BankIdentifier(BankIdentifierType type, String value) {

		private static BankIdentifier fromBlz(Counterpart counterpart) {
			if (counterpart == null) {
				return null;
			}
			String blz = InstituteLookupCache.normalizeBlzCandidate(counterpart.getBlz());
			return blz != null ? new BankIdentifier(BankIdentifierType.BLZ, blz) : null;
		}

		private static BankIdentifier fromBic(Counterpart counterpart) {
			if (counterpart == null) {
				return null;
			}
			String bic = InstituteLookupCache.normalizeBicCandidate(counterpart.getBic());
			return bic != null ? new BankIdentifier(BankIdentifierType.BIC, normalizeBic(bic)) : null;
		}
	}

	private record RecipientKey(String name, String iban, String bic, String accountNumber, String blz) {

		private static RecipientKey from(Counterpart counterpart) {
			if (counterpart == null) {
				return new RecipientKey(null, null, null, null, null);
			}
			return new RecipientKey(normalizedText(counterpart.getName()), normalizedText(counterpart.getIban()),
					normalizeBic(counterpart.getBic()), trimToNull(counterpart.getAccountNumber()), trimToNull(counterpart.getBlz()));
		}
	}

	private record Evidence(BankIdentifier identifier, RecipientKey recipientKey, String bankName, LocalDate date,
			EvidenceSource source) {
	}

	private record EvidenceKey(BankIdentifier identifier, String bankName) {

		private static EvidenceKey from(Evidence evidence) {
			return new EvidenceKey(evidence.identifier(), primaryAlias(evidence.bankName()));
		}
	}

	private record OfficialName(String name, boolean active, boolean candidate) {
	}

	private static final class Candidate {

		private String name;
		private long dateDistance = Long.MAX_VALUE;
		private int sourcePriority = Integer.MAX_VALUE;
		private boolean sameRecipient;
		private boolean activeInstitute;
		private int occurrences;

		private Candidate(String name) {
			this.name = name;
		}

		private void add(String candidateName, long distance, int priority, boolean sameRecipient, boolean activeInstitute) {
			if (readabilityScore(candidateName) > readabilityScore(name)) {
				name = candidateName;
			}
			dateDistance = Math.min(dateDistance, distance);
			sourcePriority = Math.min(sourcePriority, priority);
			this.sameRecipient |= sameRecipient;
			this.activeInstitute |= activeInstitute;
			occurrences++;
		}

		private String name() {
			return name;
		}

		private long dateDistance() {
			return dateDistance;
		}

		private int sourcePriority() {
			return sourcePriority;
		}

		private boolean sameRecipient() {
			return sameRecipient;
		}

		private boolean activeInstitute() {
			return activeInstitute;
		}

		private int occurrences() {
			return occurrences;
		}
	}

	private static final class InstituteIndex {

		private final Map<BankIdentifier, List<OfficialName>> namesByIdentifier = new HashMap<>();
		private final Map<BankIdentifierType, Map<String, Set<BankIdentifier>>> identifiersByPrimaryAlias = new HashMap<>();
		private final Map<BankIdentifier, Set<BankIdentifier>> bicIdentifiersByBlz = new HashMap<>();

		private InstituteIndex(Collection<Institute> institutes) {
			if (institutes != null) {
				institutes.stream().filter(java.util.Objects::nonNull).forEach(this::add);
			}
		}

		private void add(Institute institute) {
			List<String> names = officialNames(institute);
			boolean active = institute.getStateType() == InstituteStatus.ACTIVE;
			boolean candidate = active || institute.getStateType() == InstituteStatus.ARCHIVED;
			BankIdentifier blz = new BankIdentifier(BankIdentifierType.BLZ,
					InstituteLookupCache.normalizeBlzCandidate(institute.getBlz()));
			String bic = InstituteLookupCache.normalizeBicCandidate(institute.getBic());
			BankIdentifier bicIdentifier = new BankIdentifier(BankIdentifierType.BIC, normalizeBic(bic));
			add(blz, names, active, candidate);
			add(bicIdentifier, names, active, candidate);
			if (blz.value() != null && bicIdentifier.value() != null) {
				bicIdentifiersByBlz.computeIfAbsent(blz, ignored -> new HashSet<>()).add(bicIdentifier);
			}
		}

		private void add(BankIdentifier identifier, List<String> names, boolean active, boolean candidate) {
			if (identifier.value() == null) {
				return;
			}
			for (String name : names) {
				OfficialName officialName = new OfficialName(name, active, candidate);
				namesByIdentifier.computeIfAbsent(identifier, ignored -> new ArrayList<>()).add(officialName);
				if (active) {
					String alias = primaryAlias(name);
					if (alias != null) {
						identifiersByPrimaryAlias.computeIfAbsent(identifier.type(), ignored -> new HashMap<>())
								.computeIfAbsent(alias, ignored -> new HashSet<>()).add(identifier);
					}
				}
			}
		}

		private List<OfficialName> namesFor(BankIdentifier identifier) {
			return namesByIdentifier.getOrDefault(identifier, List.of());
		}

		private List<OfficialName> candidateNamesFor(BankIdentifier identifier) {
			return namesFor(identifier).stream().filter(OfficialName::candidate).toList();
		}

		private BankIdentifier identifierFor(Counterpart counterpart) {
			BankIdentifier blz = BankIdentifier.fromBlz(counterpart);
			if (blz != null && !namesFor(blz).isEmpty()) {
				return blz;
			}
			BankIdentifier bic = BankIdentifier.fromBic(counterpart);
			return bic != null && !namesFor(bic).isEmpty() ? bic : blz;
		}

		private boolean isConfidentMismatch(BankIdentifier identifier, String bankName) {
			List<OfficialName> expectedNames = namesFor(identifier);
			if (expectedNames.isEmpty() || matchesExpected(identifier, bankName)) {
				return false;
			}
			String alias = primaryAlias(bankName);
			return alias != null && identifiersByPrimaryAlias.getOrDefault(identifier.type(), Map.of())
					.getOrDefault(alias, Set.of()).stream().anyMatch(knownIdentifier -> !knownIdentifier.equals(identifier));
		}

		private boolean matchesAny(String bankName, List<OfficialName> officialNames) {
			return officialNames.stream().anyMatch(officialName -> bankNamesMatch(bankName, officialName.name())
					|| regionalBankNamesMatch(bankName, officialName.name()));
		}

		private boolean matchesExpected(BankIdentifier identifier, String bankName) {
			return matchesAny(bankName, namesFor(identifier)) || bicIdentifiersByBlz.getOrDefault(identifier, Set.of()).stream()
					.anyMatch(bic -> matchesAny(bankName, namesFor(bic)));
		}

		private static List<String> officialNames(Institute institute) {
			Set<String> names = new LinkedHashSet<>();
			addNameAndPlace(names, institute.getBankName(), institute.getPlace());
			addNameAndPlace(names, institute.getBankNameShort(), institute.getPlace());
			addNameAndPlace(names, institute.getAdditionalBankNameShort(), institute.getPlace());
			return List.copyOf(names);
		}

		private static void addNameAndPlace(Set<String> names, String name, String place) {
			String bankName = trimToNull(name);
			if (bankName == null) {
				return;
			}
			names.add(bankName);
			String bankPlace = trimToNull(place);
			if (bankPlace != null) {
				names.add(bankName + ", " + bankPlace);
			}
		}

	}

	private static boolean bankNamesMatch(String first, String second) {
		Set<String> firstAliases = aliases(first);
		Set<String> secondAliases = aliases(second);
		return !firstAliases.isEmpty() && firstAliases.stream().anyMatch(secondAliases::contains);
	}

	private static Set<String> aliases(String value) {
		String normalized = normalizeBankName(value);
		if (normalized == null) {
			return Set.of();
		}
		Set<String> aliases = new HashSet<>();
		Matcher matcher = PARENTHESIZED_TEXT.matcher(normalized);
		while (matcher.find()) {
			addAlias(aliases, matcher.group(1));
		}
		String withoutParentheses = matcher.replaceAll(" ");
		addAlias(aliases, withoutParentheses);
		int comma = withoutParentheses.indexOf(',');
		if (comma > 0) {
			addAlias(aliases, withoutParentheses.substring(0, comma));
		}
		return aliases;
	}

	private static String primaryAlias(String value) {
		return aliases(value).stream().max(Comparator.comparingInt(String::length)).orElse(null);
	}

	private static void addAlias(Set<String> aliases, String value) {
		List<String> tokens = bankNameTokens(value);
		if (tokens.isEmpty()) {
			return;
		}
		aliases.add(String.join("", tokens));
		addShortNameAlias(aliases, tokens.get(0));
		addBankNamePrefixAliases(aliases, tokens);
		addCompoundBankAbbreviations(aliases, tokens);
		for (int tokenCount = tokens.size(); tokenCount > 0; tokenCount--) {
			String acronym = acronym(tokens.subList(0, tokenCount));
			if (acronym.length() >= 3) {
				aliases.add(acronym);
			}
		}
	}

	private static void addShortNameAlias(Set<String> aliases, String token) {
		if (token.length() >= 3 && token.length() <= 5 && !REGIONAL_GENERIC_TOKENS.contains(token)) {
			aliases.add(token);
		}
	}

	private static void addCompoundBankAbbreviations(Set<String> aliases, List<String> tokens) {
		for (int index = 0; index + 1 < tokens.size(); index++) {
			String first = tokens.get(index);
			String second = tokens.get(index + 1);
			if (first.length() >= 2 && second.length() > "BANK".length() && second.endsWith("BANK")) {
				aliases.add(first.substring(0, 2) + second.substring(0, 2) + "BANK");
			}
		}
	}

	private static boolean regionalBankNamesMatch(String first, String second) {
		List<String> firstTokens = bankNameTokens(first);
		List<String> secondTokens = bankNameTokens(second);
		String bankType = regionalBankType(firstTokens);
		if (bankType == null || !bankType.equals(regionalBankType(secondTokens))) {
			return false;
		}
		Set<String> locations = regionalLocationTokens(firstTokens, bankType);
		return secondTokens.stream().filter(token -> isRegionalLocationToken(token, bankType)).anyMatch(locations::contains);
	}

	private static Set<String> regionalLocationTokens(List<String> tokens, String bankType) {
		return tokens.stream().filter(token -> isRegionalLocationToken(token, bankType)).collect(Collectors.toSet());
	}

	private static boolean isRegionalLocationToken(String token, String bankType) {
		return !token.endsWith(bankType) && !REGIONAL_GENERIC_TOKENS.contains(token);
	}

	private static List<String> bankNameTokens(String value) {
		String normalized = normalizeBankName(value);
		return normalized == null ? List.of()
				: TOKEN_SEPARATOR.splitAsStream(normalized.replace(".", ""))
						.filter(token -> !token.isBlank() && !LEGAL_FORM_TOKENS.contains(token) && !token.endsWith("CARD"))
						.map(ImportedBankNameValidator::expandBankTypeAbbreviation).toList();
	}

	private static String expandBankTypeAbbreviation(String token) {
		return switch (token) {
		case "KSK" -> "KREISSPARKASSE";
		case "SPK" -> "SPARKASSE";
		case "SSK" -> "STADTSPARKASSE";
		default -> token;
		};
	}

	private static String regionalBankType(List<String> tokens) {
		return tokens.stream().flatMap(token -> REGIONAL_BANK_TYPES.stream().filter(token::endsWith)).findFirst().orElse(null);
	}

	private static void addBankNamePrefixAliases(Set<String> aliases, List<String> tokens) {
		for (int index = 0; index < tokens.size(); index++) {
			String token = tokens.get(index);
			String regionalBankType = REGIONAL_BANK_TYPES.stream().filter(token::endsWith).findFirst().orElse(null);
			if (regionalBankType != null && index + 1 < tokens.size()) {
				aliases.add(regionalBankType + tokens.get(index + 1));
			} else if ((index > 0 && "BANK".equals(token))
					|| (token.length() > "BANK".length() && token.endsWith("BANK") && regionalBankType == null)) {
				aliases.add(String.join("", tokens.subList(0, index + 1)));
			}
		}
	}

	private static String acronym(List<String> tokens) {
		StringBuilder acronym = new StringBuilder();
		for (String token : tokens) {
			acronym.append(token.charAt(0));
			if (token.length() > 4 && token.endsWith("BANK")) {
				acronym.append('B');
			}
		}
		return acronym.toString();
	}

	private static String normalizeBankName(String value) {
		String text = trimToNull(value);
		if (text == null) {
			return null;
		}
		String decomposed = Normalizer.normalize(text.toUpperCase(Locale.ROOT), Normalizer.Form.NFD);
		return DIACRITIC_MARKS.matcher(decomposed).replaceAll("").replace("&", " UND ");
	}

	private static String normalizeBic(String bic) {
		String normalized = InstituteLookupCache.normalizeBicCandidate(bic);
		return normalized != null && normalized.length() == 11 ? normalized.substring(0, 8) : normalized;
	}

	private static int readabilityScore(String value) {
		if (value == null) {
			return 0;
		}
		boolean upper = value.chars().anyMatch(Character::isUpperCase);
		boolean lower = value.chars().anyMatch(Character::isLowerCase);
		return upper && lower ? 2 : lower ? 1 : 0;
	}
}
