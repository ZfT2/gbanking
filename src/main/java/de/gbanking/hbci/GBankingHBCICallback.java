package de.gbanking.hbci;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRVoP;
import org.kapott.hbci.GV_Result.GVRVoP.VoPResult;
import org.kapott.hbci.GV_Result.GVRVoP.VoPResultItem;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportInternal;
import org.kapott.hbci.passport.AbstractHBCIPassport;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.gui.dialog.DialogWindowSupport;
import de.gbanking.gui.dialog.HbciCallbackMessageDialog;
import de.gbanking.messages.Messages;

public class GBankingHBCICallback extends AbstractHBCICallback {

	private static final Logger log = LogManager.getLogger(GBankingHBCICallback.class);
	private static final int ESTIMATED_STATUS_MESSAGE_COUNT = 20;

	private final BankAccess bankAccess;
	private final HbciCallbackMessageDialog statusDialog;
	private final Messages messages = Messages.getInstance();

	private int receivedMessageCount;
	private String lastMessageBlock;
	private String lastDetailsBlock;
	private boolean successful = true;
	private String pendingRecipientCheckMessage;
	private String pendingRecipientCheckDetails;

	public GBankingHBCICallback(BankAccess bankAccess) {
		this.bankAccess = bankAccess;
		this.statusDialog = new HbciCallbackMessageDialog(DialogWindowSupport.findBestOwnerWindow().orElse(null));
	}

	@Override
	public void log(String msg, int level, Date date, StackTraceElement trace) {
		log.log(Level.DEBUG, msg, trace);
	}

	@Override
	public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
		log.info("HBCI callback reason={}, retData={}", reason, retData);

		switch (reason) {
		case NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE, NEED_PT_PIN -> retData.replace(0, retData.length(), new String(bankAccess.getPin()));
		case NEED_BLZ -> retData.replace(0, retData.length(), bankAccess.getBlz());
		case NEED_USERID, NEED_CUSTOMERID -> retData.replace(0, retData.length(), bankAccess.getUserId());
		case HAVE_VOP_RESULT -> handleVoPResult(passport, msg, retData);
		case NEED_PT_PHOTOTAN, NEED_PT_QRTAN, NEED_PT_TAN -> {
			confirmRecipientCheckIfNeeded();
			retData.replace(0, retData.length(), requestTan(msg, retData.toString()));
		}
		case NEED_PT_SECMECH -> retData.replace(0, retData.length(), requestSelection(msg, retData.toString(), "UI_DIALOG_HBCI_FEEDBACK_SELECT_SECMECH"));
		case NEED_PT_TANMEDIA -> {
			String selectedTanMedia = requestSelection(msg, retData.toString(), "UI_DIALOG_HBCI_FEEDBACK_SELECT_TANMEDIA");
			if (selectedTanMedia != null) {
				retData.replace(0, retData.length(), selectedTanMedia);
			}
		}
		case NEED_PT_DECOUPLED, NEED_PT_DECOUPLED_RETRY -> {
			confirmRecipientCheckIfNeeded();
			boolean continueProcess = statusDialog.requestConfirmation(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_DECOUPLED"), msg,
					messages.getMessage("UI_BUTTON_OK"), messages.getMessage("UI_BUTTON_CANCEL"));
			if (!continueProcess) {
				throw new HBCI_Exception("User aborted decoupled authorization");
			}
		}
		case HAVE_INST_MSG -> appendFeedback(HbciStatusMessageExtractor.extractMessageLines(msg), msg);
		case HAVE_ERROR -> {
			log.error(msg);
			appendFeedback(HbciStatusMessageExtractor.extractMessageLines(msg), msg);
			successful = false;
		}
		default -> {
			// not needed here
		}
		}
	}

	@Override
	public void status(HBCIPassport passport, int statusTag, Object[] statusPayload) {
		log.info("HBCI status: {} {} {}", statusTag, passport, statusPayload);
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(statusPayload), HbciStatusMessageExtractor.sanitizeForDetails(statusPayload));
	}

	public void startStatusDialog() {
		statusDialog.showDialog();
		statusDialog.updateProgress(0d);
	}

	public void finishStatusDialog() {
		statusDialog.markFinished(successful);
	}

	public void handleException(Exception exception) {
		if (exception == null) {
			return;
		}
		successful = false;
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(exception.getMessage()), buildExceptionDetails(exception));
	}

	public void handleFailure(String failureMessage) {
		if (failureMessage == null || failureMessage.isBlank()) {
			return;
		}
		successful = false;
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(failureMessage), failureMessage);
	}

	private void appendFeedback(List<String> messageLines, String details) {
		String messageBlock = String.join(System.lineSeparator(), messageLines);
		if (!messageBlock.isBlank() && !messageBlock.equals(lastMessageBlock)) {
			lastMessageBlock = messageBlock;
			receivedMessageCount += messageLines.size();
			statusDialog.appendMessages(messageBlock);
			statusDialog.updateProgress(calculateProgress());
			storeRecipientCheckIfNeeded(messageBlock, details);
		}
		if (details != null && !details.isBlank() && !details.equals(lastDetailsBlock)) {
			lastDetailsBlock = details;
			statusDialog.appendDetails(details);
			storeRecipientCheckIfNeeded(messageBlock, details);
		}
	}

	private double calculateProgress() {
		if (receivedMessageCount <= 0) {
			return 0d;
		}
		return Math.min(0.95d, (double) receivedMessageCount / ESTIMATED_STATUS_MESSAGE_COUNT);
	}

	private String buildExceptionDetails(Exception exception) {
		StringWriter stringWriter = new StringWriter();
		exception.printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.toString().trim();
	}

	private String requestTan(String message, String details) {
		String tan = statusDialog.requestSecretInput(message, details, messages.getMessage("UI_BUTTON_OK"), messages.getMessage("UI_BUTTON_CANCEL"));
		if (tan == null || tan.isBlank()) {
			throw new HBCI_Exception("No TAN entered");
		}
		return tan.trim();
	}

	private String requestSelection(String message, String rawOptions, String emptyOptionsMessageKey) {
		List<HbciCallbackMessageDialog.DialogOption> options = parseOptions(rawOptions);
		if (options.isEmpty()) {
			if (rawOptions != null && !rawOptions.isBlank()) {
				return rawOptions;
			}
			appendFeedback(List.of(messages.getMessage(emptyOptionsMessageKey)), rawOptions);
			return "";
		}
		if (options.size() == 1) {
			return options.get(0).value();
		}
		return statusDialog.requestSelection(message, rawOptions, options, messages.getMessage("UI_BUTTON_OK"), messages.getMessage("UI_BUTTON_CANCEL"));
	}

	private List<HbciCallbackMessageDialog.DialogOption> parseOptions(String rawOptions) {
		if (rawOptions == null || rawOptions.isBlank()) {
			return List.of();
		}

		return Arrays.stream(rawOptions.split("\\|")).map(String::trim).filter(option -> !option.isBlank()).map(option -> {
			int separator = option.indexOf(':');
			if (separator > 0) {
				String value = option.substring(0, separator).trim();
				String label = option.substring(separator + 1).trim();
				return new HbciCallbackMessageDialog.DialogOption(value, value + " - " + label);
			}
			return new HbciCallbackMessageDialog.DialogOption(option, option);
		}).toList();
	}

	private void storeRecipientCheckIfNeeded(String message, String details) {
		String combined = (message == null ? "" : message) + System.lineSeparator() + (details == null ? "" : details);
		String normalized = combined.toLowerCase(Locale.ROOT);
		if (normalized.contains("confirmation of payee") || normalized.contains("zahlungsempf") || normalized.contains("empfängerprüfung")
				|| normalized.contains("empfaengerpruefung") || normalized.contains("namensprüfung") || normalized.contains("namenspruefung")
				|| normalized.contains("iban-name")) {
			pendingRecipientCheckMessage = message;
			pendingRecipientCheckDetails = details;
		}
	}

	private void confirmRecipientCheckIfNeeded() {
		if (pendingRecipientCheckMessage == null && pendingRecipientCheckDetails == null) {
			return;
		}

		String feedbackDetails = String.join(System.lineSeparator(), pendingRecipientCheckMessage == null ? "" : pendingRecipientCheckMessage,
				pendingRecipientCheckDetails == null ? "" : pendingRecipientCheckDetails).trim();
		pendingRecipientCheckMessage = null;
		pendingRecipientCheckDetails = null;

		boolean continueTransfer = statusDialog.requestConfirmation(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_RECIPIENT_CHECK"), feedbackDetails,
				messages.getMessage("UI_BUTTON_CONTINUE"), messages.getMessage("UI_BUTTON_CANCEL"));
		if (!continueTransfer) {
			throw new HBCI_Exception("User aborted after recipient check feedback");
		}
	}

	private void handleVoPResult(HBCIPassport passport, String message, StringBuffer retData) {
		VoPResult voPResult = extractVoPResult(passport);
		String details = formatVoPDetails(message, voPResult);
		boolean continueTransfer = statusDialog.requestConfirmation(messages.getMessage("UI_DIALOG_HBCI_FEEDBACK_RECIPIENT_CHECK"), details,
				messages.getMessage("UI_BUTTON_CONTINUE"), messages.getMessage("UI_BUTTON_CANCEL"));
		retData.replace(0, retData.length(), continueTransfer ? "true" : "false");
	}

	private VoPResult extractVoPResult(HBCIPassport passport) {
		if (passport instanceof HBCIPassportInternal internalPassport) {
			Object data = internalPassport.getPersistentData(AbstractHBCIPassport.KEY_VOP_RESULT);
			if (data instanceof GVRVoP.VoPResult voPResult) {
				return voPResult;
			}
		}
		return null;
	}

	private String formatVoPDetails(String message, VoPResult voPResult) {
		StringBuilder builder = new StringBuilder();
		if (message != null && !message.isBlank()) {
			builder.append(message.trim());
		}
		if (voPResult == null) {
			return builder.toString();
		}

		appendSection(builder, voPResult.getText());
		for (VoPResultItem item : voPResult.getItems()) {
			if (item == null) {
				continue;
			}

			appendSection(builder, item.getStatus() != null ? item.getStatus().toString() : null);
			appendSection(builder, formatVoPLabelValue("Auftrag", item.getOriginal()));
			appendSection(builder, formatVoPLabelValue("Bankvorschlag", item.getName()));
			appendSection(builder, formatVoPLabelValue("IBAN", item.getIban()));
			appendSection(builder, formatVoPLabelValue("Betrag", item.getAmount() != null ? item.getAmount().toPlainString() + " EUR" : null));
			appendSection(builder, formatVoPLabelValue("Verwendungszweck", item.getUsage()));
			appendSection(builder, item.getText());
		}

		return builder.toString().trim();
	}

	private void appendSection(StringBuilder builder, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		if (!builder.isEmpty()) {
			builder.append(System.lineSeparator()).append(System.lineSeparator());
		}
		builder.append(text.trim());
	}

	private String formatVoPLabelValue(String label, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return label + ": " + value.trim();
	}
}
