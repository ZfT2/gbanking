package de.zft2.gbanking.logging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HbciLogMessageSanitizer {

	private static final List<String> RAW_MESSAGE_PREFIXES = List.of(
			"received message after decryption: ",
			"decrypted message after rewriting: ",
			"encrypted message to be sent: ",
			"message to pe parsed: ",
			"sending message: ",
			"decrypted message: ",
			"received message: ",
			"socket log: ",
			"RWrongStatusSegOrder: new message after reordering: ",
			"new message after removing: ",
			"new message after replacing: ",
			"removing invalid segment '",
			"calculating hash for jobsegment: ");
	private static final Pattern FINTS_START_PATTERN = Pattern.compile("(?:^|')HNHBK:\\d+:\\d+\\+");
	private static final Pattern FINTS_SEGMENT_PATTERN = Pattern.compile("(?:^|')([A-Z][A-Z0-9]{4,5}):\\d+:\\d+(?=[:+'])");
	private static final Pattern RESPONSE_CODE_PATTERN = Pattern.compile("\\+(\\d{4})::");
	private static final Pattern XML_START_PATTERN = Pattern.compile("<\\?xml\\b|<Document\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern IBAN_PATTERN = Pattern.compile("(?i)(?<![A-Z0-9])[A-Z]{2}\\d{2}[A-Z0-9]{11,30}(?![A-Z0-9])");
	private static final Pattern VOP_ID_PATTERN = Pattern.compile("(?i)(\\b(?:vop|polling)[-_ ]?id\\s*[:=]\\s*)([^\\s\\],+'\"]+)");

	private HbciLogMessageSanitizer() {
	}

	public static String sanitize(String message) {
		return sanitize(message, LoggingSettings.isSensitiveDataMaskingEnabled());
	}

	static String sanitize(String message, boolean maskingEnabled) {
		if (!maskingEnabled || message == null || message.isBlank()) {
			return message;
		}

		int rawPayloadStart = findRawPayloadStart(message);
		if (rawPayloadStart >= 0) {
			return message.substring(0, rawPayloadStart) + summarizeFinTs(message.substring(rawPayloadStart));
		}

		Matcher fintsMatcher = FINTS_START_PATTERN.matcher(message);
		if (fintsMatcher.find()) {
			int messageStart = message.charAt(fintsMatcher.start()) == '\'' ? fintsMatcher.start() + 1 : fintsMatcher.start();
			return message.substring(0, messageStart) + summarizeFinTs(message.substring(messageStart));
		}

		Matcher xmlMatcher = XML_START_PATTERN.matcher(message);
		if (xmlMatcher.find()) {
			return message.substring(0, xmlMatcher.start()) + summarizeXml(message.substring(xmlMatcher.start()));
		}

		String maskedParameter = maskParameterValue(message);
		if (maskedParameter != null) {
			return maskedParameter;
		}

		return maskIdentifiers(message);
	}

	private static int findRawPayloadStart(String message) {
		int offset = 0;
		while (offset < message.length() && Character.isWhitespace(message.charAt(offset))) {
			offset++;
		}
		for (String prefix : RAW_MESSAGE_PREFIXES) {
			if (message.startsWith(prefix, offset)) {
				return offset + prefix.length();
			}
		}
		return -1;
	}

	private static String summarizeFinTs(String payload) {
		Set<String> segmentNames = collectMatches(FINTS_SEGMENT_PATTERN, payload, 1);
		Set<String> responseCodes = collectMatches(RESPONSE_CODE_PATTERN, payload, 1);
		StringBuilder summary = new StringBuilder("[vertrauliche FinTS-Nachricht maskiert; Zeichen=").append(payload.length());
		appendValues(summary, "Segmente", segmentNames);
		appendValues(summary, "Rückmeldungscodes", responseCodes);
		return summary.append(']').toString();
	}

	private static String summarizeXml(String payload) {
		return "[vertrauliche XML-Nutzdaten maskiert; Zeichen=" + payload.length() + ']';
	}

	private static String maskParameterValue(String message) {
		if (message.startsWith("setting SEPA param ") || message.startsWith("setting lowlevel parameter ")) {
			return maskAfterDelimiter(message, " = ");
		}
		if (message.startsWith("setting raw property ")) {
			return maskAfterDelimiter(message, " to ");
		}
		if (message.startsWith("could not insert the following user-defined data into message: ")
				|| message.startsWith("adding challenge parameter ")) {
			return maskAfterDelimiter(message, "=");
		}
		if (message.startsWith("setting ")) {
			return maskAfterDelimiter(message, " to ");
		}
		String multipleValuesMarker = " mode ambiguous, found multiple values: ";
		int markerStart = message.indexOf(multipleValuesMarker);
		return markerStart >= 0 ? maskAfterDelimiter(message, multipleValuesMarker) : null;
	}

	private static String maskAfterDelimiter(String message, String delimiter) {
		int delimiterStart = message.indexOf(delimiter);
		if (delimiterStart < 0) {
			return null;
		}
		int valueStart = delimiterStart + delimiter.length();
		return message.substring(0, valueStart) + "[vertraulicher Wert maskiert; Zeichen=" + (message.length() - valueStart) + ']';
	}

	private static Set<String> collectMatches(Pattern pattern, String value, int group) {
		Set<String> matches = new LinkedHashSet<>();
		Matcher matcher = pattern.matcher(value);
		while (matcher.find()) {
			matches.add(matcher.group(group));
		}
		return matches;
	}

	private static void appendValues(StringBuilder summary, String label, Set<String> values) {
		if (!values.isEmpty()) {
			summary.append("; ").append(label).append('=').append(String.join(",", values));
		}
	}

	private static String maskIdentifiers(String message) {
		String maskedVopIds = replaceMatches(message, VOP_ID_PATTERN, 2, value -> SensitiveDataMasker.maskIdentifier(value));
		return replaceMatches(maskedVopIds, IBAN_PATTERN, 0, value -> SensitiveDataMasker.maskIban(value));
	}

	private static String replaceMatches(String value, Pattern pattern, int valueGroup, UnaryOperator<String> masker) {
		Matcher matcher = pattern.matcher(value);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String replacement = valueGroup == 0 ? masker.apply(matcher.group()) : matcher.group(1) + masker.apply(matcher.group(valueGroup));
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
