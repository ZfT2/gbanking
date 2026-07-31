package de.zft2.gbanking.hbci;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kapott.hbci.status.HBCIExecStatus;

public final class HbciStatusMessageExtractor {

	private static final Pattern HBCI_FEEDBACK_PATTERN = Pattern.compile("\\+(\\d{4})::([^+']+)");
	private static final String HBCI_RECEIVE_FAILURE_TEXT = "fehler beim empfangen der daten vom hbci-server";
	private static final String HTTP_BAD_REQUEST_TEXT = "http response code: 400";
	private static final String WRONG_PIN_CODE = "9942";

	private HbciStatusMessageExtractor() {
	}

	public static String extractMessages(Object[] statusPayload) {
		return String.join(System.lineSeparator(), extractMessageLines(statusPayload));
	}

	public static String extractMessages(String rawMessage) {
		return String.join(System.lineSeparator(), extractMessageLines(rawMessage));
	}

	public static boolean containsWrongPinFeedback(String rawMessage) {
		if (rawMessage == null || rawMessage.isBlank()) {
			return false;
		}

		List<String> messageLines = extractMessageLines(rawMessage);
		if (messageLines.isEmpty()) {
			return isWrongPinFeedbackLine(rawMessage);
		}
		return messageLines.stream().anyMatch(HbciStatusMessageExtractor::isWrongPinFeedbackLine);
	}

	public static boolean containsWrongPinFeedback(Throwable throwable) {
		boolean hbciReceiveFailure = false;
		boolean httpBadRequest = false;
		Throwable current = throwable;
		while (current != null) {
			if (containsWrongPinFeedback(current.getMessage())) {
				return true;
			}
			String normalizedMessage = Objects.toString(current.getMessage(), "").toLowerCase(Locale.ROOT);
			hbciReceiveFailure |= normalizedMessage.contains(HBCI_RECEIVE_FAILURE_TEXT);
			httpBadRequest |= normalizedMessage.contains(HTTP_BAD_REQUEST_TEXT);
			current = current.getCause();
		}
		return hbciReceiveFailure && httpBadRequest;
	}

	public static boolean containsWrongPinFeedback(HBCIExecStatus status) {
		return status != null && (containsWrongPinFeedback(status.getErrorString())
				|| containsWrongPinFeedback(Objects.toString(status, "")));
	}

	public static List<String> extractMessageLines(Object[] statusPayload) {
		if (statusPayload == null || statusPayload.length == 0) {
			return List.of();
		}

		List<String> messages = new ArrayList<>();
		for (Object payloadEntry : statusPayload) {
			collectMessageLines(payloadEntry, messages);
		}
		return messages;
	}

	public static List<String> extractMessageLines(String rawMessage) {
		if (rawMessage == null || rawMessage.isBlank()) {
			return List.of();
		}

		List<String> messages = new ArrayList<>();
		collectMessageLines(rawMessage, messages);
		return messages;
	}

	public static String sanitizeForDetails(Object[] statusPayload) {
		if (statusPayload == null || statusPayload.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (Object payloadEntry : statusPayload) {
			appendDetails(payloadEntry, builder);
		}
		return builder.toString().trim();
	}

	private static void collectMessageLines(Object payloadEntry, List<String> messages) {
		if (payloadEntry == null) {
			return;
		}
		if (payloadEntry instanceof Object[] nestedPayload) {
			for (Object nestedEntry : nestedPayload) {
				collectMessageLines(nestedEntry, messages);
			}
			return;
		}

		String rawText = Objects.toString(payloadEntry, "").trim();
		if (rawText.isEmpty()) {
			return;
		}

		Matcher matcher = HBCI_FEEDBACK_PATTERN.matcher(rawText);
		while (matcher.find()) {
			String code = matcher.group(1);
			String text = normalizeText(matcher.group(2));
			if (!text.isBlank()) {
				messages.add(code + ": " + text);
			}
		}
	}

	private static void appendDetails(Object payloadEntry, StringBuilder builder) {
		if (payloadEntry == null) {
			return;
		}
		if (payloadEntry instanceof Object[] nestedPayload) {
			for (Object nestedEntry : nestedPayload) {
				appendDetails(nestedEntry, builder);
			}
			return;
		}

		String rawText = Objects.toString(payloadEntry, "").trim();
		if (!rawText.isEmpty()) {
			if (!builder.isEmpty()) {
				builder.append(System.lineSeparator()).append(System.lineSeparator());
			}
			builder.append(rawText);
		}
	}

	private static String normalizeText(String text) {
		return text == null ? "" : text.replace("?:?", "").replace("''", "").trim();
	}

	private static boolean isWrongPinFeedbackLine(String text) {
		String normalizedText = normalizeForDetection(text);
		if (normalizedText.startsWith(WRONG_PIN_CODE + ":") || normalizedText.contains("+" + WRONG_PIN_CODE + "::")) {
			return true;
		}
		return hasCredentialTerm(normalizedText) && hasInvalidCredentialTerm(normalizedText);
	}

	private static boolean hasCredentialTerm(String text) {
		return text.contains("pin") || text.contains("anmeldedaten") || text.contains("zugangsdaten") || text.contains("login");
	}

	private static boolean hasInvalidCredentialTerm(String text) {
		return text.contains("falsch") || text.contains("ungültig") || text.contains("ungueltig") || text.contains("ungultig")
				|| text.contains("gesperrt") || text.contains("incorrect") || text.contains("invalid") || text.contains("locked");
	}

	private static String normalizeForDetection(String text) {
		return normalizeText(text).toLowerCase(Locale.ROOT);
	}
}
