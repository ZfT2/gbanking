package de.zft2.gbanking.service.bankaccess;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.GVInfoList;
import org.kapott.hbci.GV.GVInfoOrder;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRInfoList;
import org.kapott.hbci.GV_Result.GVRInfoOrder;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIExecStatus;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.hbci.HbciStatusMessageExtractor;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.service.HbciSessionRunner;

public class BankMessageService implements BaseMessagesDb {

	private static final Logger log = LogManager.getLogger(BankMessageService.class);

	private static final String INFO_LIST_JOB = GVInfoList.getLowlevelName();
	private static final String INFO_DETAILS_JOB = GVInfoOrder.getLowlevelName();
	private static final String FREE_TEXT_TYPE = "F";
	private static final int DETAILS_BATCH_SIZE = 10;

	private final BankAccessService hbciSupport;
	private final GBankingLoggingHandler logHandler;
	private final HbciSessionRunner hbciSessionRunner;

	public BankMessageService(BankAccessService hbciSupport, GBankingLoggingHandler logHandler) {
		this.hbciSupport = hbciSupport;
		this.logHandler = logHandler;
		hbciSessionRunner = new HbciSessionRunner(hbciSupport);
	}

	public List<BankMessage> listBankMessages(BankAccess bankAccess) {
		if (bankAccess == null || bankAccess.getId() <= 0) {
			return List.of();
		}
		List<BankMessage> messages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
		return messages != null ? messages : List.of();
	}

	public BankMessageRetrievalResult retrieveBankMessagesWithResult(BankAccess bankAccess, char[] pin) {
		log.info("Starting HBCI bank message retrieval for bank access id {}", bankAccess != null ? bankAccess.getId() : null);
		BankAccess retrievalAccess = initBankAccess(bankAccess, pin);
		if (retrievalAccess == null) {
			log.info("HBCI bank message retrieval skipped, no bank access available.");
			clearSecret(pin);
			return BankMessageRetrievalResult.failure();
		}

		try {
			BankMessageRetrievalResult result = hbciSessionRunner.run(retrievalAccess, pin,
					session -> retrieveBankMessages(retrievalAccess, session));
			log.info("Finished HBCI bank message retrieval for bank access id {}, success={}, messages={}", retrievalAccess.getId(),
					result.successful(), result.messages().size());
			return result;
		} catch (InterruptedException e) {
			log.error("Error handling HBCI bank message retrieval for bank access id {}", retrievalAccess.getId(), e);
			Thread.currentThread().interrupt();
			return BankMessageRetrievalResult.failure();
		} catch (HBCI_Exception e) {
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(e)) {
				log.warn("Stopping HBCI bank message retrieval for bank access id {} because the bank reported invalid PIN credentials.",
						retrievalAccess.getId());
				return BankMessageRetrievalResult.wrongPinFailure();
			}
			throw e;
		}
	}

	private BankAccess initBankAccess(BankAccess bankAccess, char[] pin) {
		if (bankAccess == null || bankAccess.getId() <= 0) {
			return null;
		}
		BankAccess retrievalAccess = dbController.getBankAccessById(bankAccess.getId());
		if (retrievalAccess == null) {
			return null;
		}
		retrievalAccess.setPin(pin);
		return retrievalAccess;
	}

	private BankMessageRetrievalResult retrieveBankMessages(BankAccess bankAccess, HbciSessionRunner.HbciSession session) {
		logHandler.logRetrivedBankAccessInfo(session.passport(), false);
		if (!isSupported(session.handler().getSupportedLowlevelJobs(), INFO_LIST_JOB)) {
			log.info("Bank access id {} does not advertise HBCI job {}; retrieving institution messages through an empty dialog.",
					bankAccess.getId(), INFO_LIST_JOB);
			return retrieveInstitutionMessages(bankAccess, session);
		}

		BankMessageOverviewResult overviewResult = retrieveBankMessageOverview(session);
		if (overviewResult.wrongPin()) {
			return BankMessageRetrievalResult.wrongPinFailure();
		}
		if (!overviewResult.successful()) {
			return BankMessageRetrievalResult.failure(saveInstitutionMessages(bankAccess, session.callback().drainInstitutionMessages()));
		}

		BankMessageDetailsResult detailsResult = retrieveBankMessageDetails(session, overviewResult.entries());
		if (detailsResult.wrongPin()) {
			return BankMessageRetrievalResult.wrongPinFailure();
		}

		LocalDateTime retrievedAt = LocalDateTime.now(ZoneId.systemDefault());
		List<BankMessage> savedMessages = new ArrayList<>(saveInstitutionMessages(bankAccess,
				session.callback().drainInstitutionMessages(), retrievedAt));
		savedMessages.addAll(saveBankMessages(bankAccess, overviewResult.entries(), detailsResult.messages(), retrievedAt));
		return detailsResult.successful() ? BankMessageRetrievalResult.success(savedMessages) : BankMessageRetrievalResult.failure(savedMessages);
	}

	BankMessageRetrievalResult retrieveInstitutionMessages(BankAccess bankAccess, HbciSessionRunner.HbciSession session) {
		session.handler().createEmptyDialog();
		HBCIExecStatus status = session.handler().execute();
		List<BankMessage> savedMessages = saveInstitutionMessages(bankAccess, session.callback().drainInstitutionMessages());
		if (!status.isOK()) {
			log.error("HBCI institution message retrieval error, status: {}", status);
			session.callback().handleFailure(status.getErrorString());
			return HbciStatusMessageExtractor.containsWrongPinFeedback(status) ? BankMessageRetrievalResult.wrongPinFailure()
					: BankMessageRetrievalResult.failure(savedMessages);
		}
		return BankMessageRetrievalResult.success(savedMessages);
	}

	private BankMessageOverviewResult retrieveBankMessageOverview(HbciSessionRunner.HbciSession session) {
		HBCIJob<GVRInfoList> infoListJob = hbciSupport.newHbciJob(session.handler(), INFO_LIST_JOB);
		infoListJob.addToQueue();
		session.callback().registerJobDescription(infoListJob, getText("UI_DIALOG_HBCI_JOB_BANK_MESSAGE_LIST"));

		HBCIExecStatus status = session.handler().execute();
		if (!status.isOK()) {
			log.error("HBCI bank message overview error, status: {}", status);
			session.callback().handleFailure(status.getErrorString());
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(status)) {
				return BankMessageOverviewResult.wrongPinFailure();
			}
			return BankMessageOverviewResult.failure();
		}

		GVRInfoList result = infoListJob.getJobResult();
		if (result == null || !result.isOK()) {
			log.info("No successful HBCI bank message overview result returned: {}", result);
			return BankMessageOverviewResult.failure();
		}

		List<GVRInfoList.Info> entries = readOverviewEntries(result);
		log.info("Received {} HBCI bank message overview entries.", entries.size());
		return BankMessageOverviewResult.success(entries);
	}

	private BankMessageDetailsResult retrieveBankMessageDetails(HbciSessionRunner.HbciSession session, List<GVRInfoList.Info> entries) {
		List<String> freeTextCodes = collectFreeTextCodes(entries);
		if (freeTextCodes.isEmpty()) {
			return BankMessageDetailsResult.success(Map.of());
		}
		if (!isSupported(session.handler().getSupportedLowlevelJobs(), INFO_DETAILS_JOB)) {
			log.warn("Bank returned {} free-text bank message(s), but HBCI job {} is not advertised.", freeTextCodes.size(), INFO_DETAILS_JOB);
			return BankMessageDetailsResult.success(Map.of());
		}

		List<HBCIJob<GVRInfoOrder>> detailsJobs = createBankMessageDetailsJobs(session, freeTextCodes);
		HBCIExecStatus status = session.handler().execute();
		if (!status.isOK()) {
			log.error("HBCI bank message details error, status: {}", status);
			session.callback().handleFailure(status.getErrorString());
			if (HbciStatusMessageExtractor.containsWrongPinFeedback(status)) {
				return BankMessageDetailsResult.wrongPinFailure();
			}
			return BankMessageDetailsResult.failure(Map.of());
		}

		Map<String, String> messages = readDetailMessages(detailsJobs);
		log.info("Received {} HBCI bank message detail entries.", messages.size());
		return BankMessageDetailsResult.success(messages);
	}

	private List<HBCIJob<GVRInfoOrder>> createBankMessageDetailsJobs(HbciSessionRunner.HbciSession session, List<String> freeTextCodes) {
		List<HBCIJob<GVRInfoOrder>> jobs = new ArrayList<>();
		int totalBatchCount = (freeTextCodes.size() + DETAILS_BATCH_SIZE - 1) / DETAILS_BATCH_SIZE;
		for (int startIndex = 0; startIndex < freeTextCodes.size(); startIndex += DETAILS_BATCH_SIZE) {
			int endIndex = Math.min(startIndex + DETAILS_BATCH_SIZE, freeTextCodes.size());
			HBCIJob<GVRInfoOrder> job = createBankMessageDetailsJob(session.handler(), freeTextCodes.subList(startIndex, endIndex));
			session.callback().registerJobDescription(job, getText("UI_DIALOG_HBCI_JOB_BANK_MESSAGE_DETAILS",
					Integer.toString(jobs.size() + 1), Integer.toString(totalBatchCount)));
			jobs.add(job);
		}
		return jobs;
	}

	private HBCIJob<GVRInfoOrder> createBankMessageDetailsJob(HBCIHandler handler, List<String> codes) {
		HBCIJob<GVRInfoOrder> job = hbciSupport.newHbciJob(handler, INFO_DETAILS_JOB);
		for (int index = 0; index < codes.size(); index++) {
			job.setParam(toInfoDetailsCodeParameter(index), codes.get(index));
		}
		job.addToQueue();
		log.debug("Queued HBCI bank message details job with {} code(s).", codes.size());
		return job;
	}

	private String toInfoDetailsCodeParameter(int zeroBasedIndex) {
		return zeroBasedIndex == 0 ? "code" : "code_" + (zeroBasedIndex + 1);
	}

	private List<GVRInfoList.Info> readOverviewEntries(GVRInfoList result) {
		GVRInfoList.Info[] entries = result.getEntries();
		List<GVRInfoList.Info> messages = new ArrayList<>();
		if (entries == null) {
			return messages;
		}
		for (GVRInfoList.Info entry : entries) {
			if (entry != null) {
				messages.add(entry);
			}
		}
		return messages;
	}

	private List<String> collectFreeTextCodes(List<GVRInfoList.Info> entries) {
		List<String> codes = new ArrayList<>();
		for (GVRInfoList.Info entry : entries) {
			if (entry != null && FREE_TEXT_TYPE.equalsIgnoreCase(trimToBlank(entry.type)) && trimToNull(entry.code) != null) {
				codes.add(entry.code.trim());
			}
		}
		return codes;
	}

	private Map<String, String> readDetailMessages(List<HBCIJob<GVRInfoOrder>> detailsJobs) {
		Map<String, String> messages = new HashMap<>();
		for (HBCIJob<GVRInfoOrder> detailsJob : detailsJobs) {
			GVRInfoOrder result = detailsJob.getJobResult();
			if (result == null || !result.isOK()) {
				continue;
			}
			for (GVRInfoOrder.Info entry : result.getEntries()) {
				if (entry != null && trimToNull(entry.code) != null) {
					messages.put(entry.code.trim(), trimToBlank(entry.msg));
				}
			}
		}
		return messages;
	}

	List<BankMessage> saveBankMessages(BankAccess bankAccess, List<GVRInfoList.Info> entries, Map<String, String> detailMessages,
			LocalDateTime retrievedAt) {
		if (bankAccess == null || bankAccess.getId() <= 0 || entries == null || entries.isEmpty()) {
			return List.of();
		}

		Map<String, BankMessage> existingMessages = existingMessagesByKey(bankAccess);
		Map<String, BankMessage> savedMessages = new LinkedHashMap<>();
		for (GVRInfoList.Info entry : entries) {
			if (entry == null) {
				continue;
			}
			String messageKey = createMessageKey(entry);
			BankMessage storedMessage = existingMessages.get(messageKey);
			BankMessage bankMessage = toBankMessage(bankAccess, entry, detailMessages, messageKey, retrievedAt, storedMessage);
			BankMessage savedMessage = dbController.insertOrUpdate(bankMessage);
			BankMessage persistedMessage = savedMessage != null ? savedMessage : bankMessage;
			existingMessages.put(messageKey, persistedMessage);
			savedMessages.put(messageKey, persistedMessage);
		}
		return new ArrayList<>(savedMessages.values());
	}

	public static List<BankMessage> saveInstitutionMessages(BankAccess bankAccess, List<String> messages) {
		return saveInstitutionMessages(bankAccess, messages, LocalDateTime.now(ZoneId.systemDefault()));
	}

	static List<BankMessage> saveInstitutionMessages(BankAccess bankAccess, List<String> messages, LocalDateTime retrievedAt) {
		if (bankAccess == null || bankAccess.getId() <= 0 || messages == null || messages.isEmpty()) {
			return List.of();
		}

		try {
			Map<String, BankMessage> existingMessages = existingMessagesByKey(bankAccess);
			Map<String, BankMessage> savedMessages = new LinkedHashMap<>();
			for (String messageText : messages) {
				String normalizedText = trimToNull(messageText);
				if (normalizedText == null) {
					continue;
				}
				String messageKey = sha256("KIMSG|" + normalizeForMessageKey(normalizedText));
				BankMessage bankMessage = toInstitutionMessage(bankAccess, normalizedText, messageKey, retrievedAt,
						existingMessages.get(messageKey));
				BankMessage savedMessage = dbController.insertOrUpdate(bankMessage);
				BankMessage persistedMessage = savedMessage != null ? savedMessage : bankMessage;
				existingMessages.put(messageKey, persistedMessage);
				savedMessages.put(messageKey, persistedMessage);
			}
			return new ArrayList<>(savedMessages.values());
		} catch (RuntimeException exception) {
			log.warn("Could not persist institution messages for bank access id {}.", bankAccess.getId(), exception);
			return List.of();
		}
	}

	private static Map<String, BankMessage> existingMessagesByKey(BankAccess bankAccess) {
		Map<String, BankMessage> messagesByKey = new HashMap<>();
		List<BankMessage> existingMessages = dbController.getAllByParentFull(BankMessage.class, bankAccess.getId());
		if (existingMessages == null) {
			return messagesByKey;
		}
		for (BankMessage message : existingMessages) {
			String messageKey = trimToNull(message.getMessageKey());
			if (messageKey != null) {
				messagesByKey.put(messageKey, message);
			}
		}
		return messagesByKey;
	}

	private static BankMessage toInstitutionMessage(BankAccess bankAccess, String text, String messageKey, LocalDateTime retrievedAt,
			BankMessage storedMessage) {
		int separatorIndex = text.indexOf(':');
		BankMessage bankMessage = new BankMessage();
		if (storedMessage != null && storedMessage.getId() > 0) {
			bankMessage.setId(storedMessage.getId());
		}
		bankMessage.setBankAccessId(bankAccess.getId());
		bankMessage.setBankName(trimToBlank(bankAccess.getBankName()));
		bankMessage.setMessageKey(messageKey);
		bankMessage.setCode("");
		bankMessage.setType(FREE_TEXT_TYPE);
		bankMessage.setFormat("TXT");
		bankMessage.setDescription(separatorIndex > 0 ? text.substring(0, separatorIndex).trim() : "");
		bankMessage.setComments("");
		bankMessage.setMessage(separatorIndex > 0 ? text.substring(separatorIndex + 1).trim() : text);
		bankMessage.setRetrievedAt(retrievedAt);
		bankMessage.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return bankMessage;
	}

	private BankMessage toBankMessage(BankAccess bankAccess, GVRInfoList.Info entry, Map<String, String> detailMessages, String messageKey,
			LocalDateTime retrievedAt, BankMessage storedMessage) {
		BankMessage bankMessage = new BankMessage();
		if (storedMessage != null && storedMessage.getId() > 0) {
			bankMessage.setId(storedMessage.getId());
		}
		bankMessage.setBankAccessId(bankAccess.getId());
		bankMessage.setBankName(trimToBlank(bankAccess.getBankName()));
		bankMessage.setMessageKey(messageKey);
		bankMessage.setCode(trimToBlank(entry.code));
		bankMessage.setType(trimToBlank(entry.type));
		bankMessage.setFormat(trimToBlank(entry.format));
		bankMessage.setDescription(trimToBlank(entry.description));
		bankMessage.setVersionDate(toLocalDate(entry.date));
		bankMessage.setComments(joinComments(entry.comment));
		bankMessage.setMessage(resolveDetailMessage(entry, detailMessages));
		bankMessage.setRetrievedAt(retrievedAt);
		bankMessage.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return bankMessage;
	}

	private String resolveDetailMessage(GVRInfoList.Info entry, Map<String, String> detailMessages) {
		if (entry == null || detailMessages == null || detailMessages.isEmpty() || trimToNull(entry.code) == null) {
			return "";
		}
		return trimToBlank(detailMessages.get(entry.code.trim()));
	}

	private String createMessageKey(GVRInfoList.Info entry) {
		String keyMaterial = String.join("|",
				normalizeForMessageKey(entry != null ? entry.code : null),
				formatDate(toLocalDate(entry != null ? entry.date : null)),
				normalizeForMessageKey(entry != null ? entry.description : null),
				normalizeForMessageKey(entry != null ? entry.type : null),
				normalizeForMessageKey(entry != null ? entry.format : null));
		return sha256(keyMaterial);
	}

	private static String normalizeForMessageKey(String value) {
		String trimmedValue = trimToNull(value);
		return trimmedValue != null ? trimmedValue.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT) : "";
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available.", e);
		}
	}

	private String joinComments(String[] comments) {
		if (comments == null || comments.length == 0) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (String comment : comments) {
			String normalizedComment = trimToNull(comment);
			if (normalizedComment == null) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(System.lineSeparator());
			}
			builder.append(normalizedComment);
		}
		return builder.toString();
	}

	private LocalDate toLocalDate(java.util.Date date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private String formatDate(LocalDate date) {
		return date != null ? date.toString() : "";
	}

	private boolean isSupported(Properties supportedJobs, String jobName) {
		return supportedJobs != null && supportedJobs.containsKey(jobName);
	}

	private static String trimToBlank(String value) {
		String trimmedValue = trimToNull(value);
		return trimmedValue != null ? trimmedValue : "";
	}

	private void clearSecret(char[] secret) {
		HbciSessionRunner.clearSecret(secret);
	}

	private record BankMessageOverviewResult(boolean successful, boolean wrongPin, List<GVRInfoList.Info> entries) {

		private static BankMessageOverviewResult success(List<GVRInfoList.Info> entries) {
			return new BankMessageOverviewResult(true, false, entries != null ? List.copyOf(entries) : List.of());
		}

		private static BankMessageOverviewResult failure() {
			return new BankMessageOverviewResult(false, false, List.of());
		}

		private static BankMessageOverviewResult wrongPinFailure() {
			return new BankMessageOverviewResult(false, true, List.of());
		}
	}

	private record BankMessageDetailsResult(boolean successful, boolean wrongPin, Map<String, String> messages) {

		private static BankMessageDetailsResult success(Map<String, String> messages) {
			return new BankMessageDetailsResult(true, false, messages != null ? Map.copyOf(messages) : Map.of());
		}

		private static BankMessageDetailsResult failure(Map<String, String> messages) {
			return new BankMessageDetailsResult(false, false, messages != null ? Map.copyOf(messages) : Map.of());
		}

		private static BankMessageDetailsResult wrongPinFailure() {
			return new BankMessageDetailsResult(false, true, Map.of());
		}
	}
}
