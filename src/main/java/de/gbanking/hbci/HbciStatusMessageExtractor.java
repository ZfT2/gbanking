package de.gbanking.hbci;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HbciStatusMessageExtractor {

	private static final Pattern HBCI_FEEDBACK_PATTERN = Pattern.compile("\\+(\\d{4})::([^+']+)");

	private HbciStatusMessageExtractor() {
	}

	public static String extractMessages(Object[] statusPayload) {
		return String.join(System.lineSeparator(), extractMessageLines(statusPayload));
	}

	public static String extractMessages(String rawMessage) {
		return String.join(System.lineSeparator(), extractMessageLines(rawMessage));
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
			if (builder.length() > 0) {
				builder.append(System.lineSeparator()).append(System.lineSeparator());
			}
			builder.append(rawText);
		}
	}

	private static String normalizeText(String text) {
		return text == null ? "" : text.replace("?:?", "").replace("''", "").trim();
	}
}
