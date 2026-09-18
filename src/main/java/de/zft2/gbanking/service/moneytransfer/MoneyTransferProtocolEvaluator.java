package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kapott.hbci.GV_Result.GVRVoP.VoPStatus;

import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.MoneyTransferProtocolResultStatus;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.VopResult;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;

public final class MoneyTransferProtocolEvaluator {

	private static final Pattern RETURN_VALUE_PATTERN = Pattern.compile("\\b([039]\\d{3}):+\\s*(?:\\?:\\?)?([^\\r\\n+']+)");
	private static final Set<MoneyTransferStatus> SUCCESSFUL_TRANSFER_STATES = Set.of(MoneyTransferStatus.SENT,
			MoneyTransferStatus.INVENTORY, MoneyTransferStatus.DELETED);
	private static final Set<String> SCA_REQUIRED_CODES = Set.of("0030", "3075", "3956", "9075");

	private MoneyTransferProtocolEvaluator() {
	}

	public static Evaluation evaluate(boolean successful, String protocolText, boolean vopRequired, VoPStatus vopStatus,
			boolean recipientNameCorrected) {
		return evaluate(successful, protocolText, vopRequired, mapVopResult(vopStatus, protocolText), recipientNameCorrected);
	}

	public static Evaluation evaluateUncertain(String protocolText, boolean vopRequired, VoPStatus vopStatus,
			boolean recipientNameCorrected) {
		Evaluation evaluation = evaluate(false, protocolText, vopRequired, mapVopResult(vopStatus, protocolText), recipientNameCorrected);
		return new Evaluation(MoneyTransferProtocolResultStatus.UNKNOWN, evaluation.pinOk(), evaluation.scaRequired(),
				evaluation.vopRequired(), evaluation.vopResult(), evaluation.recipientNameCorrected(), evaluation.bankResponse());
	}

	public static Evaluation evaluateLegacy(MoneyTransferStatus transferStatus, String protocolText) {
		VopResult vopResult = mapVopResult(null, protocolText);
		boolean successful = SUCCESSFUL_TRANSFER_STATES.contains(transferStatus);
		boolean vopRequired = vopResult != null && vopResult != VopResult.OPT_OUT;
		return evaluate(successful, protocolText, vopRequired, vopResult, false);
	}

	private static Evaluation evaluate(boolean successful, String protocolText, boolean vopRequired, VopResult vopResult,
			boolean recipientNameCorrected) {
		List<Feedback> feedback = extractFeedback(protocolText);
		MoneyTransferProtocolResultStatus resultStatus = resolveResultStatus(successful, protocolText, feedback);
		boolean pinOk = resultStatus != MoneyTransferProtocolResultStatus.ERROR_INVALID_PIN
				&& (successful || confirmsAuthenticatedDialog(resultStatus) || containsCode(feedback, "0901"));
		boolean scaRequired = resolveScaRequired(normalize(protocolText), feedback);
		boolean effectiveVopRequired = vopResult != VopResult.OPT_OUT && vopRequired;
		boolean effectiveNameCorrected = vopResult == VopResult.CLOSE_MATCH && recipientNameCorrected;
		return new Evaluation(resultStatus, pinOk, scaRequired, effectiveVopRequired, vopResult, effectiveNameCorrected,
				extractUnmappedResponse(protocolText, feedback));
	}

	private static MoneyTransferProtocolResultStatus resolveResultStatus(boolean successful, String protocolText, List<Feedback> feedback) {
		String normalized = normalize(protocolText);
		MoneyTransferProtocolResultStatus businessError = resolveBusinessError(normalized, feedback);
		return businessError != null ? businessError : resolveProcessingResult(successful, normalized, feedback);
	}

	private static MoneyTransferProtocolResultStatus resolveBusinessError(String normalized, List<Feedback> feedback) {
		if (isWrongPin(normalized, feedback)) {
			return MoneyTransferProtocolResultStatus.ERROR_INVALID_PIN;
		}
		if (containsCode(feedback, "9255") || containsAny(normalized, "limit überschritten", "limit nicht ausreichend", "limit exceeded")) {
			return MoneyTransferProtocolResultStatus.ERROR_LIMIT_INSUFFICIENT;
		}
		if (containsCode(feedback, "9230") || containsAny(normalized, "unzureichendes guthaben", "mangelnde deckung", "insufficient funds")) {
			return MoneyTransferProtocolResultStatus.ERROR_INSUFFICIENT_FUNDS;
		}
		if (containsCode(feedback, "9370", "9380") || containsAny(normalized, "kompetenz nicht ausreichend", "keine auftragsberechtigung",
				"berechtigung reicht nicht", "not authorized")) {
			return MoneyTransferProtocolResultStatus.ERROR_NOT_AUTHORIZED;
		}
		if (containsCode(feedback, "9390") || containsAny(normalized, "doppeleinreichung", "duplicate submission")) {
			return MoneyTransferProtocolResultStatus.ERROR_DUPLICATE_ORDER;
		}
		if (containsCode(feedback, "9075", "9420", "9941", "9943", "9951")
				|| containsAny(normalized, "tan ungültig", "tan bereits verbraucht", "authentifizierung erforderlich", "authentication failed")) {
			return MoneyTransferProtocolResultStatus.ERROR_AUTHENTICATION;
		}
		if (isInvalidOrderData(normalized, feedback)) {
			return MoneyTransferProtocolResultStatus.ERROR_INVALID_ORDER_DATA;
		}
		return null;
	}

	private static MoneyTransferProtocolResultStatus resolveProcessingResult(boolean successful, String normalized, List<Feedback> feedback) {
		if (containsAny(normalized, "nicht verfügbar", "nicht verfuegbar", "temporarily unavailable", "system gestört", "system gestoert")
				|| containsCode(feedback, "9311")) {
			return MoneyTransferProtocolResultStatus.ERROR_SERVICE_UNAVAILABLE;
		}
		if (containsCode(feedback, "9030", "9040", "9340") || containsAny(normalized, "auftrag abgelehnt", "order rejected")) {
			return MoneyTransferProtocolResultStatus.ERROR_REJECTED;
		}
		if (containsCode(feedback, "9800") || containsAny(normalized, "abgebrochen", "cancelled", "canceled")) {
			return MoneyTransferProtocolResultStatus.ERROR_CANCELLED;
		}
		if (containsCode(feedback, "9000", "9010", "9020", "9050") || containsAny(normalized, "exception", "technischer fehler")) {
			return MoneyTransferProtocolResultStatus.ERROR_TECHNICAL;
		}
		if (successful || hasSuccessWithoutErrors(feedback)) {
			return MoneyTransferProtocolResultStatus.SUCCESS;
		}
		return MoneyTransferProtocolResultStatus.ERROR_UNKNOWN;
	}

	private static boolean isInvalidOrderData(String normalized, List<Feedback> feedback) {
		return containsCode(feedback, "9021", "9110", "9130", "9140", "9145", "9150", "9160", "9170", "9180", "9185", "9210",
				"9212", "9215", "9220")
				|| containsAny(normalized, "auftragsdaten inkonsistent", "inhaltlich ungültig", "inhaltlich ungueltig", "betrag zu groß",
						"betrag zu gross", "iban hat", "invalid order data");
	}

	private static boolean isWrongPin(String normalized, List<Feedback> feedback) {
		return containsCode(feedback, "9930", "9931", "9942") || HbciStatusMessageExtractor.containsWrongPinFeedback(normalized)
				|| containsAny(normalized, "pin ungültig", "pin ungueltig", "pin ist gesperrt", "invalid pin");
	}

	private static boolean confirmsAuthenticatedDialog(MoneyTransferProtocolResultStatus status) {
		return status == MoneyTransferProtocolResultStatus.ERROR_LIMIT_INSUFFICIENT
				|| status == MoneyTransferProtocolResultStatus.ERROR_INSUFFICIENT_FUNDS
				|| status == MoneyTransferProtocolResultStatus.ERROR_INVALID_ORDER_DATA
				|| status == MoneyTransferProtocolResultStatus.ERROR_NOT_AUTHORIZED
				|| status == MoneyTransferProtocolResultStatus.ERROR_DUPLICATE_ORDER
				|| status == MoneyTransferProtocolResultStatus.ERROR_AUTHENTICATION
				|| status == MoneyTransferProtocolResultStatus.ERROR_REJECTED;
	}

	private static VopResult mapVopResult(VoPStatus status, String protocolText) {
		if (status != null) {
			return switch (status) {
			case MATCH -> VopResult.MATCH;
			case CLOSE_MATCH -> VopResult.CLOSE_MATCH;
			case NO_MATCH -> VopResult.NO_MATCH;
			case NOT_APPLICABLE, PENDING -> null;
			};
		}

		String normalized = normalize(protocolText);
		if (containsAny(normalized, "opt-out", "opt out", "hkvoo")) {
			return VopResult.OPT_OUT;
		}
		if (containsAny(normalized, "close-match", "close match", "beinahe übereinstimmung", "beinahe uebereinstimmung", "rvmc")) {
			return VopResult.CLOSE_MATCH;
		}
		if (containsAny(normalized, "no-match", "no match", "keine übereinstimmung", "keine uebereinstimmung", "rvnm")) {
			return VopResult.NO_MATCH;
		}
		if (containsAny(normalized, "match", "übereinstimmung", "uebereinstimmung", "rcvc")) {
			return VopResult.MATCH;
		}
		return null;
	}

	private static List<Feedback> extractFeedback(String protocolText) {
		if (protocolText == null || protocolText.isBlank()) {
			return List.of();
		}

		Set<Feedback> feedback = new LinkedHashSet<>();
		Matcher matcher = RETURN_VALUE_PATTERN.matcher(protocolText);
		while (matcher.find()) {
			feedback.add(new Feedback(matcher.group(1), cleanFeedbackText(matcher.group(2))));
		}
		return new ArrayList<>(feedback);
	}

	private static String extractUnmappedResponse(String protocolText, List<Feedback> feedback) {
		if (!feedback.isEmpty()) {
			String response = feedback.stream().filter(value -> !isRecognized(value)).map(value -> value.displayText()).distinct()
					.reduce((first, second) -> first + System.lineSeparator() + second).orElse(null);
			return trimToNull(response);
		}

		String value = trimToNull(protocolText);
		if (value == null || containsAny(normalize(value), "hbci execution status", "hbci job status")
				|| isRecognizedInformation(normalize(value)) || "ok".equalsIgnoreCase(value)) {
			return null;
		}
		return value;
	}

	private static boolean isRecognized(Feedback feedback) {
		String normalized = normalize(feedback.text());
		return feedback.code().startsWith("0") || containsCode(List.of(feedback), "9000", "9010", "9020", "9021", "9030", "9040", "9050",
				"9075", "9110", "9130", "9140", "9145", "9150", "9160", "9170", "9180", "9185", "9210", "9212", "9215", "9220",
				"9230", "9255", "9311", "9340", "9370", "9380", "9390", "9420", "9800", "9930", "9931", "9941", "9942", "9943", "9951",
				"3060", "3075", "3076", "3905", "3920", "3956")
				|| isRecognizedInformation(normalized)
				|| containsAny(normalized, "opt-out", "close match", "no match", "übereinstimmung", "uebereinstimmung");
	}

	private static boolean resolveScaRequired(String normalized, List<Feedback> feedback) {
		Boolean result = null;
		for (Feedback value : feedback) {
			String feedbackText = normalize(value.text());
			if ("3076".equals(value.code()) || isScaNotRequiredText(feedbackText)) {
				result = Boolean.FALSE;
			} else if (SCA_REQUIRED_CODES.contains(value.code()) || isScaRequiredText(feedbackText)) {
				result = Boolean.TRUE;
			}
		}
		if (result != null) {
			return result;
		}
		return !isScaNotRequiredText(normalized) && isScaRequiredText(normalized);
	}

	private static boolean isScaNotRequiredText(String normalized) {
		return containsAny(normalized, "starke kundenauthentifizierung nicht notwendig",
				"starke kundenauthentifizierung nicht erforderlich", "keine starke kundenauthentifizierung erforderlich",
				"keine starke kundenauthentifizierung notwendig", "keine starke authentifizierung erforderlich",
				"keine starke authentifizierung notwendig", "starke kundenauthentifizierung entfällt", "sca nicht erforderlich",
				"sca nicht notwendig", "strong customer authentication not required", "strong customer authentication not necessary",
				"strong customer authentication is not required", "no strong customer authentication required", "sca not required",
				"sca not necessary");
	}

	private static boolean isScaRequiredText(String normalized) {
		return !isScaNotRequiredText(normalized) && containsAny(normalized, "starke kundenauthentifizierung erforderlich",
				"starke kundenauthentifizierung notwendig", "starke authentifizierung erforderlich", "starke authentifizierung notwendig",
				"starke kundenauthentifizierung noch ausstehend", "sicherheitsfreigabe erforderlich", "sca erforderlich", "sca notwendig",
				"strong customer authentication required", "strong customer authentication necessary",
				"strong customer authentication is required", "sca required", "sca necessary");
	}

	private static boolean isRecognizedInformation(String normalized) {
		return isScaNotRequiredText(normalized) || isScaRequiredText(normalized) || containsAny(normalized, "bitte beachten sie die hinweise",
				"hinweise beachten", "zugelassene tan-verfahren", "zugelassene tan verfahren", "zulässige tan-verfahren",
				"zulaessige tan-verfahren", "keine challenge erzeugt", "keine challenge wurde erzeugt",
				"please observe the information", "permitted tan procedures", "no challenge was generated");
	}

	private static boolean hasSuccessWithoutErrors(List<Feedback> feedback) {
		return feedback.stream().anyMatch(value -> value.code().startsWith("0"))
				&& feedback.stream().noneMatch(value -> value.code().startsWith("9"));
	}

	private static boolean containsCode(List<Feedback> feedback, String... codes) {
		for (Feedback value : feedback) {
			for (String code : codes) {
				if (code.equals(value.code())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsAny(String value, String... terms) {
		for (String term : terms) {
			if (value.contains(term)) {
				return true;
			}
		}
		return false;
	}

	private static String cleanFeedbackText(String value) {
		return value.replace("?:?", "").trim();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	public record Evaluation(MoneyTransferProtocolResultStatus resultStatus, boolean pinOk, boolean scaRequired, boolean vopRequired,
			VopResult vopResult, boolean recipientNameCorrected, String bankResponse) {

		public void applyTo(MoneyTransferProtocol protocol) {
			protocol.setResultStatus(resultStatus);
			protocol.setPinOk(pinOk);
			protocol.setScaRequired(scaRequired);
			protocol.setVopRequired(vopRequired);
			protocol.setVopResult(vopResult);
			protocol.setRecipientNameCorrected(recipientNameCorrected);
			protocol.setProtocolText(bankResponse);
		}
	}

	private record Feedback(String code, String text) {

		private String displayText() {
			return code + ": " + text;
		}
	}
}
