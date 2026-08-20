package de.zft2.gbanking.hbci;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRVoP;
import org.kapott.hbci.GV_Result.GVRVoP.VoPResult;
import org.kapott.hbci.GV_Result.GVRVoP.VoPResultItem;
import org.kapott.hbci.GV_Result.GVRVoP.VoPStatus;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.MatrixCode;
import org.kapott.hbci.manager.QRCode;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportInternal;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.TanProcedure;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.RecipientCheckDecision;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.RecipientCheckRequest;
import de.zft2.gbanking.gui.dialog.hbci.HbciCallbackMessageDialog.TanChallenge;
import de.zft2.gbanking.logging.LoggingSettings;
import de.zft2.gbanking.util.TextValues;

public class GBankingHBCICallback extends AbstractHBCICallback implements BaseMessages {

	private static final Logger log = LogManager.getLogger(GBankingHBCICallback.class);
	private static final Logger hbci4javaLog = LogManager.getLogger(LoggingSettings.HBCI4JAVA_LOGGER_NAME);

	private static final String UI_BUTTON_OK = "UI_BUTTON_OK";
	private static final String UI_BUTTON_CANCEL = "UI_BUTTON_CANCEL";
	private static final String UI_BUTTON_CONTINUE = "UI_BUTTON_CONTINUE";

	private static final int ESTIMATED_STATUS_MESSAGE_COUNT = 20;

	private final BankAccess bankAccess;
	private final HbciCallbackMessageDialog statusDialog;
	private final HbciStatusDescriptionProvider statusDescriptionProvider = new HbciStatusDescriptionProvider();
	private final List<String> institutionMessages = new ArrayList<>();

	private int receivedMessageCount;
	private String lastMessageBlock;
	private String lastDetailsBlock;
	private String lastErrorDescription;
	private boolean successful = true;
	private String pendingRecipientCheckMessage;
	private String pendingRecipientCheckDetails;
	private String confirmedRecipientName;
	private MoneyTransfer currentMoneyTransfer;

	public GBankingHBCICallback(BankAccess bankAccess) {
		this.bankAccess = bankAccess;
		this.statusDialog = new HbciCallbackMessageDialog(DialogWindowSupport.findBestOwnerWindow().orElse(null));
	}

	@Override
	public void log(String msg, int level, java.util.Date date, StackTraceElement trace) {
		hbci4javaLog.log(toLog4jLevel(level), "[hbci4java] {}", msg);
	}

	@Override
	public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
		log.info("HBCI callback reason={}, datatype={}", reason, datatype);
		StringBuilder retDataBuilder = new StringBuilder(retData.toString());

		switch (reason) {
		case NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE, NEED_PT_PIN -> retDataBuilder.replace(0, retDataBuilder.length(), readPin());
		case NEED_BLZ -> retDataBuilder.replace(0, retDataBuilder.length(), bankAccess.getFints().getBlz());
		case NEED_USERID -> retDataBuilder.replace(0, retDataBuilder.length(), trimToBlank(bankAccess.getFints().getUserId()));
		case NEED_CUSTOMERID -> retDataBuilder.replace(0, retDataBuilder.length(),
				firstNonBlank(bankAccess.getFints().getCustomerId(), bankAccess.getFints().getUserId()));
		case HAVE_VOP_RESULT -> handleVoPResult(passport, msg, retDataBuilder);
		case NEED_PT_PHOTOTAN, NEED_PT_QRTAN, NEED_PT_TAN -> {
			confirmRecipientCheckIfNeeded();
			retDataBuilder.replace(0, retDataBuilder.length(), requestTan(reason, msg, retDataBuilder.toString()));
		}
		case NEED_PT_SECMECH -> {
			String selectedSecurityMechanism = requestSelection(msg, retDataBuilder.toString(), "UI_DIALOG_HBCI_FEEDBACK_SELECT_SECMECH");
			if (selectedSecurityMechanism == null || selectedSecurityMechanism.isBlank()) {
				throw new HBCI_Exception("No TAN security mechanism selected");
			}
			retDataBuilder.replace(0, retDataBuilder.length(), selectedSecurityMechanism);
		}
		case NEED_PT_TANMEDIA -> {
			String selectedTanMedia = requestSelection(msg, retDataBuilder.toString(), "UI_DIALOG_HBCI_FEEDBACK_SELECT_TANMEDIA");
			if (selectedTanMedia != null) {
				retDataBuilder.replace(0, retDataBuilder.length(), selectedTanMedia);
			}
		}
		case NEED_PT_DECOUPLED, NEED_PT_DECOUPLED_RETRY -> {
			confirmRecipientCheckIfNeeded();
			boolean continueProcess = statusDialog.requestConfirmation(getText("UI_DIALOG_HBCI_FEEDBACK_DECOUPLED"), msg, getText(UI_BUTTON_OK),
					getText(UI_BUTTON_CANCEL));
			if (!continueProcess) {
				throw new HBCI_Exception("User aborted decoupled authorization");
			}
		}
		case HAVE_INST_MSG -> handleInstitutionMessage(msg);
		case HAVE_ERROR -> {
			log.error(msg);
			updateErrorAction(statusDescriptionProvider.describeFailure(msg));
			appendFeedback(HbciStatusMessageExtractor.extractMessageLines(msg), msg);
			successful = false;
		}
		default -> {
			// not needed here
		}
		}
		retData.replace(0, retData.length(), retDataBuilder.toString());
	}

	@Override
	public void status(HBCIPassport passport, int statusTag, Object[] statusPayload) {
		log.info("HBCI status: {}", statusTag);
		statusDialog.updateCurrentAction(statusDescriptionProvider.describeStatus(statusTag, statusPayload));
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(statusPayload), HbciStatusMessageExtractor.sanitizeForDetails(statusPayload));
	}

	private Level toLog4jLevel(int hbciLevel) {
		return switch (hbciLevel) {
		case HBCIUtils.LOG_ERR -> Level.ERROR;
		case HBCIUtils.LOG_WARN -> Level.WARN;
		case HBCIUtils.LOG_INFO -> Level.INFO;
		case HBCIUtils.LOG_DEBUG -> Level.DEBUG;
		case HBCIUtils.LOG_DEBUG2, HBCIUtils.LOG_INTERN -> Level.TRACE;
		default -> Level.DEBUG;
		};
	}

	public void startStatusDialog() {
		statusDialog.showDialog();
		statusDialog.updateCurrentAction(getText("UI_DIALOG_HBCI_STATUS_CONNECTING"));
		statusDialog.updateProgress(0d);
	}

	public void finishStatusDialog() {
		if (!successful && lastErrorDescription != null) {
			statusDialog.updateCurrentAction(lastErrorDescription);
		}
		statusDialog.markFinished(successful);
	}

	public void handleException(Exception exception) {
		if (exception == null) {
			return;
		}
		successful = false;
		updateErrorAction(statusDescriptionProvider.describeException(exception));
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(exception.getMessage()), buildExceptionDetails(exception));
	}

	public void handleFailure(String failureMessage) {
		if (failureMessage == null || failureMessage.isBlank()) {
			return;
		}
		successful = false;
		updateErrorAction(statusDescriptionProvider.describeFailure(failureMessage));
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(failureMessage), failureMessage);
	}

	public void registerJobDescription(HBCIJob<?> job, String description) {
		statusDescriptionProvider.registerJobDescription(job, description);
	}

	public List<String> drainInstitutionMessages() {
		List<String> messages = List.copyOf(institutionMessages);
		institutionMessages.clear();
		return messages;
	}

	public String getAccountDescription(BankAccount bankAccount) {
		return statusDescriptionProvider.getAccountDescription(bankAccount);
	}

	private void updateErrorAction(String errorDescription) {
		if (errorDescription == null || errorDescription.isBlank()) {
			return;
		}
		lastErrorDescription = errorDescription;
		statusDialog.updateCurrentAction(errorDescription);
	}

	private void handleInstitutionMessage(String message) {
		if (message != null && !message.isBlank() && !institutionMessages.contains(message.trim())) {
			institutionMessages.add(message.trim());
		}
		appendFeedback(HbciStatusMessageExtractor.extractMessageLines(message), message);
	}

	public String getConfirmedRecipientName() {
		return confirmedRecipientName;
	}

	public void setCurrentMoneyTransfer(MoneyTransfer currentMoneyTransfer) {
		this.currentMoneyTransfer = currentMoneyTransfer;
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

	private String requestTan(int reason, String message, String details) {
		String chipTan = tryChipTanReader(details);
		if (chipTan != null && !chipTan.isBlank()) {
			return chipTan.trim();
		}

		TanChallenge challenge = createTanChallenge(reason, message, details);
		String prompt = challenge != null && challenge.message() != null && !challenge.message().isBlank() ? challenge.message() : message;
		String tan = challenge != null
				? statusDialog.requestSecretInput(prompt, details, challenge, getText(UI_BUTTON_OK), getText(UI_BUTTON_CANCEL))
				: statusDialog.requestSecretInput(prompt, details, getText(UI_BUTTON_OK), getText(UI_BUTTON_CANCEL));
		if (tan == null || tan.isBlank()) {
			throw new HBCI_Exception("No TAN entered");
		}
		return tan.trim();
	}

	private TanChallenge createTanChallenge(int reason, String message, String payload) {
		if (payload == null || payload.isBlank()) {
			return null;
		}
		return switch (reason) {
		case NEED_PT_PHOTOTAN -> createPhotoTanChallenge(payload);
		case NEED_PT_QRTAN -> createQrTanChallenge(payload, message);
		case NEED_PT_TAN -> createFlickerTanChallenge(payload);
		default -> null;
		};
	}

	private TanChallenge createPhotoTanChallenge(String payload) {
		MatrixCode matrixCode = MatrixCode.tryParse(payload);
		if (matrixCode == null || matrixCode.getImage() == null || matrixCode.getImage().length == 0) {
			return null;
		}
		return TanChallenge.image(getText("UI_DIALOG_HBCI_TAN_PHOTO"), matrixCode.getImage(), null);
	}

	private TanChallenge createQrTanChallenge(String payload, String message) {
		QRCode qrCode = QRCode.tryParse(payload, message);
		if (qrCode == null || qrCode.getImage() == null || qrCode.getImage().length == 0) {
			return null;
		}
		return TanChallenge.image(getText("UI_DIALOG_HBCI_TAN_QR"), qrCode.getImage(), qrCode.getMessage());
	}

	private TanChallenge createFlickerTanChallenge(String payload) {
		if (!isFlickerTanProcedure() || !ChipTanUsbSupport.isChipTanPayload(payload)) {
			return null;
		}
		return TanChallenge.flicker(getText("UI_DIALOG_HBCI_TAN_FLICKER"), payload);
	}

	private boolean isFlickerTanProcedure() {
		TanProcedure procedure = bankAccess != null ? bankAccess.getFints().getTanProcedure() : null;
		return procedure == TanProcedure.CHIP_TAN || procedure == TanProcedure.CHIP_TAN_OPTICAL;
	}

	private String readPin() {
		String pin = bankAccess.getPin() != null ? new String(bankAccess.getPin()).replace("\0", "") : null;
		if (pin == null || pin.isBlank()) {
			throw new HBCI_Exception("No PIN available");
		}
		return pin;
	}

	private String tryChipTanReader(String payload) {
		if (!ChipTanUsbSupport.isEnabled() || !ChipTanUsbSupport.isChipTanPayload(payload)) {
			return null;
		}

		String readerName = ChipTanUsbSupport.getConfiguredReaderName();
		String resolvedReader = readerName.isBlank() ? getText("UI_DIALOG_HBCI_FEEDBACK_CHIPTAN_DEFAULT_READER") : readerName;
		appendFeedback(List.of(getText("UI_DIALOG_HBCI_FEEDBACK_CHIPTAN_WAIT", resolvedReader)), payload);
		try {
			String tan = ChipTanUsbSupport.requestTan(payload);
			if (tan == null || tan.isBlank()) {
				appendFeedback(List.of(getText("UI_DIALOG_HBCI_FEEDBACK_CHIPTAN_CANCELLED")), payload);
				return null;
			}
			appendFeedback(List.of(getText("UI_DIALOG_HBCI_FEEDBACK_CHIPTAN_SUCCESS")), null);
			return tan;
		} catch (Exception ex) {
			log.warn("chipTAN reader TAN acquisition failed, falling back to manual entry", ex);
			appendFeedback(List.of(getText("UI_DIALOG_HBCI_FEEDBACK_CHIPTAN_FALLBACK", ex.getMessage())), payload);
			return null;
		}
	}

	private String requestSelection(String message, String rawOptions, String emptyOptionsMessageKey) {
		List<HbciCallbackMessageDialog.DialogOption> options = parseOptions(rawOptions);
		if (options.isEmpty()) {
			if (rawOptions != null && !rawOptions.isBlank()) {
				return rawOptions;
			}
			appendFeedback(List.of(getText(emptyOptionsMessageKey)), rawOptions);
			return "Alle Geräte"; /** https://homebanking-hilfe.de/forum/topic.php?t=27003 **/
		}
		if (options.size() == 1) {
			return options.get(0).value();
		}
		return statusDialog.requestSelection(message, rawOptions, options, getText(UI_BUTTON_OK), getText(UI_BUTTON_CANCEL));
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

		boolean continueTransfer = statusDialog.requestConfirmation(getText("UI_DIALOG_HBCI_FEEDBACK_RECIPIENT_CHECK"), feedbackDetails,
				getText(UI_BUTTON_CONTINUE), getText(UI_BUTTON_CANCEL));
		if (!continueTransfer) {
			throw new HBCI_Exception("User aborted after recipient check feedback");
		}
	}

	private void handleVoPResult(HBCIPassport passport, String message, StringBuilder retData) {
		confirmedRecipientName = null;
		VoPResult voPResult = extractVoPResult(passport);
		String details = formatVoPDetails(message, voPResult);
		if (voPResult == null) {
			boolean continueTransfer = statusDialog.requestConfirmation(getText("UI_DIALOG_HBCI_FEEDBACK_RECIPIENT_CHECK"), details,
					getText(UI_BUTTON_CONTINUE), getText(UI_BUTTON_CANCEL));
			retData.replace(0, retData.length(), continueTransfer ? "true" : "false");
			return;
		}

		VoPStatus status = resolvePrimaryVoPStatus(voPResult);
		if (status == VoPStatus.MATCH) {
			pendingRecipientCheckMessage = null;
			pendingRecipientCheckDetails = null;
			retData.replace(0, retData.length(), "true");
			return;
		}

		RecipientCheckDecision decision = statusDialog.requestRecipientCheckDecision(createRecipientCheckRequest(voPResult, status, details),
				getText(requiresExplicitConfirmation(voPResult) ? "UI_BUTTON_VOP_APPROVE_PAYMENT" : UI_BUTTON_CONTINUE), getText(UI_BUTTON_CANCEL));
		boolean continueTransfer = decision != null && decision.continueTransfer();
		if (continueTransfer) {
			confirmedRecipientName = TextValues.trimToNull(decision.recipientName());
		}
		pendingRecipientCheckMessage = null;
		pendingRecipientCheckDetails = null;
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

			appendSection(builder, getVoPStatusText(item.getStatus()));
			appendSection(builder, formatVoPLabelValue(getText("UI_DIALOG_HBCI_VOP_ORIGINAL_NAME"), item.getOriginal()));
			appendSection(builder, formatVoPLabelValue(getText("UI_DIALOG_HBCI_VOP_BANK_NAME"), item.getName()));
			appendSection(builder, formatVoPLabelValue(getText("UI_TABLE_IBAN"), item.getIban()));
			appendSection(builder, formatVoPLabelValue(getText("UI_TABLE_AMOUNT"), formatAmount(item.getAmount(), getCurrentCurrency())));
			appendSection(builder, formatVoPLabelValue(getText("UI_TABLE_PURPOSE"), item.getUsage()));
			appendSection(builder, item.getText());
		}

		return builder.toString().trim();
	}

	private RecipientCheckRequest createRecipientCheckRequest(VoPResult voPResult, VoPStatus status, String details) {
		VoPResultItem item = resolvePrimaryVoPItem(voPResult);
		String originalName = firstNonBlank(item != null ? item.getOriginal() : null, getCurrentRecipientName());
		String bankName = trimToBlank(item != null ? item.getName() : null);
		boolean closeMatch = status == VoPStatus.CLOSE_MATCH;
		boolean noMatch = status == VoPStatus.NO_MATCH;
		List<HbciCallbackMessageDialog.DialogOption> nameOptions = closeMatch ? createRecipientNameOptions(originalName, bankName) : List.of();
		String initialRecipientName = firstNonBlank(originalName, bankName);

		return new RecipientCheckRequest(getVoPPrompt(status), details, getOrderTypeText(), getCurrentAmount(item),
				firstNonBlank(getCurrentRecipientName(), originalName),
				firstNonBlank(item != null ? item.getIban() : null, getCurrentRecipientIban()), getCurrentRecipientBic(), getCurrentRecipientBank(),
				firstNonBlank(getCurrentPurpose(), item != null ? item.getUsage() : null), getVoPStatusText(status), originalName, bankName,
				initialRecipientName, noMatch, nameOptions);
	}

	private boolean requiresExplicitConfirmation(VoPResult voPResult) {
		return voPResult.getItems().stream()
				.map(VoPResultItem::getStatus)
				.anyMatch(status -> status == VoPStatus.CLOSE_MATCH || status == VoPStatus.NO_MATCH);
	}

	private VoPStatus resolvePrimaryVoPStatus(VoPResult voPResult) {
		return voPResult.getItems().stream()
				.map(VoPResultItem::getStatus)
				.filter(status -> status == VoPStatus.NO_MATCH || status == VoPStatus.CLOSE_MATCH)
				.findFirst()
				.orElseGet(() -> voPResult.getItems().stream().map(VoPResultItem::getStatus).filter(Objects::nonNull).findFirst().orElse(null));
	}

	private VoPResultItem resolvePrimaryVoPItem(VoPResult voPResult) {
		return voPResult.getItems().stream()
				.filter(item -> item != null && (item.getStatus() == VoPStatus.NO_MATCH || item.getStatus() == VoPStatus.CLOSE_MATCH))
				.findFirst()
				.orElseGet(() -> voPResult.getItems().stream().filter(Objects::nonNull).findFirst().orElse(null));
	}

	private List<HbciCallbackMessageDialog.DialogOption> createRecipientNameOptions(String originalName, String bankName) {
		return java.util.stream.Stream.of(originalName, bankName)
				.map(TextValues::trimToNull)
				.filter(Objects::nonNull)
				.distinct()
				.map(name -> new HbciCallbackMessageDialog.DialogOption(name, name))
				.toList();
	}

	private String getVoPPrompt(VoPStatus status) {
		if (status == null) {
			return getText("UI_DIALOG_HBCI_VOP_REVIEW_OTHER");
		}
		return switch (status) {
		case CLOSE_MATCH -> getText("UI_DIALOG_HBCI_VOP_REVIEW_CLOSE_MATCH");
		case NO_MATCH -> getText("UI_DIALOG_HBCI_VOP_REVIEW_NO_MATCH");
		default -> getText("UI_DIALOG_HBCI_VOP_REVIEW_OTHER");
		};
	}

	private String getOrderTypeText() {
		OrderType orderType = currentMoneyTransfer != null ? currentMoneyTransfer.getOrderType() : null;
		if (orderType == null) {
			return "";
		}
		return switch (orderType) {
		case TRANSFER -> getText("UI_DIALOG_HBCI_VOP_ORDER_TRANSFER");
		case REALTIME_TRANSFER -> getText("UI_DIALOG_HBCI_VOP_ORDER_REALTIME_TRANSFER");
		case URGENT_TRANSFER -> getText("UI_DIALOG_HBCI_VOP_ORDER_URGENT_TRANSFER");
		case SCHEDULED_TRANSFER -> getText("UI_DIALOG_HBCI_VOP_ORDER_SCHEDULED_TRANSFER");
		case STANDING_ORDER -> getText("UI_DIALOG_HBCI_VOP_ORDER_STANDING_ORDER");
		case FOREIGN_TRANSFER -> getText("UI_DIALOG_HBCI_VOP_ORDER_FOREIGN_TRANSFER");
		};
	}

	private String getCurrentRecipientName() {
		Recipient recipient = currentMoneyTransfer != null ? currentMoneyTransfer.getRecipient() : null;
		return recipient != null ? recipient.getName() : null;
	}

	private String getCurrentRecipientIban() {
		Recipient recipient = currentMoneyTransfer != null ? currentMoneyTransfer.getRecipient() : null;
		return recipient != null ? recipient.getIban() : null;
	}

	private String getCurrentRecipientBic() {
		Recipient recipient = currentMoneyTransfer != null ? currentMoneyTransfer.getRecipient() : null;
		return recipient != null ? recipient.getBic() : null;
	}

	private String getCurrentRecipientBank() {
		Recipient recipient = currentMoneyTransfer != null ? currentMoneyTransfer.getRecipient() : null;
		return recipient != null ? recipient.getBank() : null;
	}

	private String getCurrentPurpose() {
		return currentMoneyTransfer != null ? currentMoneyTransfer.getPurpose() : null;
	}

	private String getCurrentAmount(VoPResultItem item) {
		if (currentMoneyTransfer != null && currentMoneyTransfer.getAmount() != null) {
			return formatAmount(currentMoneyTransfer.getAmount(), getCurrentCurrency());
		}
		return item != null ? formatAmount(item.getAmount(), getCurrentCurrency()) : "";
	}

	private String getCurrentCurrency() {
		return currentMoneyTransfer != null ? firstNonBlank(currentMoneyTransfer.getCurrency(), "EUR") : "EUR";
	}

	private String formatAmount(BigDecimal amount, String currency) {
		if (amount == null) {
			return null;
		}
		String formattedAmount = String.format(Locale.GERMANY, "%,.2f", amount);
		String resolvedCurrency = TextValues.trimToNull(currency);
		return resolvedCurrency != null ? formattedAmount + " " + resolvedCurrency : formattedAmount;
	}

	private String getVoPStatusText(VoPStatus status) {
		if (status == null) {
			return getText("UI_DIALOG_HBCI_VOP_UNKNOWN");
		}
		return switch (status) {
		case MATCH -> getText("UI_DIALOG_HBCI_VOP_MATCH");
		case CLOSE_MATCH -> getText("UI_DIALOG_HBCI_VOP_CLOSE_MATCH");
		case NO_MATCH -> getText("UI_DIALOG_HBCI_VOP_NO_MATCH");
		case NOT_APPLICABLE -> getText("UI_DIALOG_HBCI_VOP_NOT_APPLICABLE");
		case PENDING -> getText("UI_DIALOG_HBCI_VOP_PENDING");
		};
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

	private String trimToBlank(String value) {
		String trimmedValue = TextValues.trimToNull(value);
		return trimmedValue != null ? trimmedValue : "";
	}

	private String firstNonBlank(String preferredValue, String fallbackValue) {
		String trimmedValue = TextValues.trimToNull(preferredValue);
		return trimmedValue != null ? trimmedValue : trimToBlank(fallbackValue);
	}
}
