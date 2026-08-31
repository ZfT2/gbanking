package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportInternal;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIStatus;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.SepaCancellationCode;
import de.zft2.gbanking.db.dao.enu.SepaOrderStatus;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

class InstantPaymentStatusService extends AbstractDbService {

	static final String JOB_NAME = "InstUebSEPAStatus";
	private static final String PAIN_FORMAT = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
	private static final String RETRY_CODE = "3045";
	private static final int MAX_REQUESTS = 3;
	private static final Logger log = LogManager.getLogger(InstantPaymentStatusService.class);

	void retrieveStatusIfNecessary(HBCIHandler handler, GBankingHBCICallback callback, MoneyTransfer moneyTransfer, Konto senderAccount,
			SepaOrderStatus initialStatus, HBCIJobResult initialResult) {
		String bankOrderId = trimToNull(moneyTransfer.getBankOrderId());
		if (bankOrderId == null || !requiresStatusRequest(initialStatus, initialResult)
				|| !ensureStatusRequestSupported(handler, hasReturnCode(initialResult, RETRY_CODE))) {
			return;
		}
		requestStatusUntilFinished(handler, callback, moneyTransfer, senderAccount, bankOrderId);
	}

	boolean retrieveStatus(HBCIHandler handler, GBankingHBCICallback callback, MoneyTransfer moneyTransfer, Konto senderAccount) {
		String bankOrderId = trimToNull(moneyTransfer.getBankOrderId());
		if (bankOrderId == null || !ensureStatusRequestSupported(handler, true)) {
			return false;
		}
		return requestStatusUntilFinished(handler, callback, moneyTransfer, senderAccount, bankOrderId);
	}

	private boolean requestStatusUntilFinished(HBCIHandler handler, GBankingHBCICallback callback, MoneyTransfer moneyTransfer,
			Konto senderAccount, String bankOrderId) {
		int waitSeconds = minimumWaitSeconds(handler);
		boolean successful = false;
		for (int request = 0; request < MAX_REQUESTS; request++) {
			if (!waitBeforeRequest(waitSeconds)) {
				return false;
			}
			StatusResponse response = executeStatusRequest(handler, callback, moneyTransfer, senderAccount, bankOrderId);
			successful = successful || response != null && response.successful() && response.sepaOrderStatus() != null;
			if (isFinished(response)) {
				return successful;
			}
		}
		log.info("Stopped SEPA instant payment status requests after {} attempts. transferId={}", MAX_REQUESTS, moneyTransfer.getId());
		return successful;
	}

	private boolean isFinished(StatusResponse response) {
		return response == null || response.sepaOrderStatus() != null && response.sepaOrderStatus().isFinal()
				|| !response.retryRequested() && response.sepaOrderStatus() == null;
	}

	private boolean requiresStatusRequest(SepaOrderStatus status, HBCIJobResult result) {
		return hasReturnCode(result, RETRY_CODE) || status != null && !status.isFinal();
	}

	private boolean supportsStatusRequest(HBCIHandler handler) {
		Properties jobs = handler != null ? handler.getSupportedLowlevelJobs() : null;
		boolean supported = jobs != null && jobs.containsKey(JOB_NAME);
		if (!supported) {
			log.debug("Lowlevel HBCI job {} is not supported by this bank access.", JOB_NAME);
		}
		return supported;
	}

	private boolean ensureStatusRequestSupported(HBCIHandler handler, boolean refreshWhenUnsupported) {
		return supportsStatusRequest(handler) || refreshWhenUnsupported && refreshStatusParameters(handler);
	}

	private boolean refreshStatusParameters(HBCIHandler handler) {
		HBCIPassport passport = handler.getPassport();
		Properties previousBpd = copy(passport.getBPD());
		try {
			log.info("Refreshing BPD because the bank requested a SEPA instant payment status query");
			HBCIDialogStatus status = handler.refreshXPD(HBCIHandler.REFRESH_BPD);
			if (status == null || !status.isOK()) {
				restoreBpd(passport, previousBpd);
				return false;
			}
			return supportsStatusRequest(handler);
		} catch (RuntimeException exception) {
			restoreBpd(passport, previousBpd);
			log.warn("Could not refresh BPD for the SEPA instant payment status query", exception);
			return false;
		}
	}

	private Properties copy(Properties properties) {
		if (properties == null) {
			return null;
		}
		Properties copy = new Properties();
		copy.putAll(properties);
		return copy;
	}

	private void restoreBpd(HBCIPassport passport, Properties previousBpd) {
		try {
			if (passport instanceof HBCIPassportInternal internalPassport) {
				internalPassport.setBPD(previousBpd);
				passport.saveChanges();
			}
		} catch (RuntimeException exception) {
			log.error("Could not restore BPD after a failed SEPA instant payment status parameter refresh", exception);
		}
	}

	private int minimumWaitSeconds(HBCIHandler handler) {
		Properties restrictions = handler.getLowlevelJobRestrictions(JOB_NAME);
		String value = restrictions != null ? trimToNull(restrictions.getProperty("minwait")) : null;
		if (value == null) {
			return 0;
		}
		try {
			int parsed = Integer.parseInt(value);
			return Math.max(0, parsed);
		} catch (NumberFormatException exception) {
			log.warn("Ignoring invalid minimum wait time for {}: {}", JOB_NAME, value);
			return 0;
		}
	}

	private boolean waitBeforeRequest(int waitSeconds) {
		try {
			Thread.sleep(waitSeconds * 1_000L);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.info("SEPA instant payment status request was interrupted.");
			return false;
		}
	}

	private StatusResponse executeStatusRequest(HBCIHandler handler, GBankingHBCICallback callback, MoneyTransfer moneyTransfer,
			Konto senderAccount, String bankOrderId) {
		LocalDateTime start = LocalDateTime.now();
		try {
			HBCIJob<HBCIJobResult> job = createStatusJob(handler, senderAccount, bankOrderId);
			callback.registerJobDescription(job, getText("UI_DIALOG_HBCI_JOB_REALTIME_TRANSFER_STATUS"));
			HBCIExecStatus executionStatus = handler.execute();
			LocalDateTime finish = LocalDateTime.now();
			HBCIJobResult result = job.getJobResult();
			StatusResponse response = toStatusResponse(result, bankOrderId, isSuccessful(executionStatus, result));
			persistProtocol(moneyTransfer, start, finish, executionStatus, result, response);
			return response;
		} catch (RuntimeException exception) {
			persistFailureProtocol(moneyTransfer, start, exception);
			log.warn("SEPA instant payment status request failed. transferId={}", moneyTransfer.getId(), exception);
			return null;
		}
	}

	private HBCIJob<HBCIJobResult> createStatusJob(HBCIHandler handler, Konto account, String bankOrderId) {
		BankAccessService bankAccessService = ServiceRegistry.getService(BankAccessService.class);
		HBCIJob<HBCIJobResult> job = bankAccessService.newLowlevelHbciJob(handler, JOB_NAME);
		setParam(job, "My.iban", account.iban);
		setParam(job, "My.bic", account.bic);
		setParam(job, "My.number", account.number);
		setParam(job, "My.subnumber", account.subnumber);
		setParam(job, "My.KIK.country", account.country);
		setParam(job, "My.KIK.blz", account.blz);
		job.setParam("formats.format", supportedPainFormat(handler));
		job.setParam("orderid", bankOrderId);
		job.addToQueue();
		return job;
	}

	private String supportedPainFormat(HBCIHandler handler) {
		Properties restrictions = handler.getLowlevelJobRestrictions(JOB_NAME);
		if (restrictions == null) {
			return PAIN_FORMAT;
		}
		return restrictions.stringPropertyNames().stream().filter(key -> key.startsWith("suppformats"))
				.map(key -> trimToNull(restrictions.getProperty(key))).filter(value -> value != null && value.contains("pain.001."))
				.sorted().reduce((first, second) -> second).orElse(PAIN_FORMAT);
	}

	private void setParam(HBCIJob<?> job, String name, String value) {
		String normalizedValue = trimToNull(value);
		if (normalizedValue != null) {
			job.setParam(name, normalizedValue);
		}
	}

	private StatusResponse toStatusResponse(HBCIJobResult result, String fallbackOrderId, boolean successful) {
		Properties data = result != null ? result.getResultData() : null;
		String orderId = firstResultValue(data, "orderid");
		return new StatusResponse(orderId != null ? orderId : fallbackOrderId,
				SepaOrderStatus.forCode(firstResultValue(data, "orderstatus")),
				SepaCancellationCode.forCode(firstResultValue(data, "ccode")), hasReturnCode(result, RETRY_CODE), successful);
	}

	private String firstResultValue(Properties data, String fieldName) {
		if (data == null) {
			return null;
		}
		return data.stringPropertyNames().stream().filter(key -> key.equals(fieldName) || key.endsWith("." + fieldName)).sorted()
				.map(key -> data.getProperty(key)).map(value -> trimToNull(value)).filter(value -> value != null).findFirst().orElse(null);
	}

	private boolean hasReturnCode(HBCIJobResult result, String returnCode) {
		return result != null && (hasReturnCode(result.getJobStatus(), returnCode) || hasReturnCode(result.getGlobStatus(), returnCode));
	}

	private boolean hasReturnCode(HBCIStatus status, String returnCode) {
		return status != null && Arrays.stream(status.getRetVals()).anyMatch(returnValue -> returnCode.equals(returnValue.code));
	}

	private void persistProtocol(MoneyTransfer moneyTransfer, LocalDateTime start, LocalDateTime finish, HBCIExecStatus executionStatus,
			HBCIJobResult result, StatusResponse response) {
		MoneyTransferStatus status = isSuccessful(executionStatus, result) ? moneyTransfer.getMoneytransferStatus() : MoneyTransferStatus.ERROR;
		MoneyTransferProtocol protocol = createProtocol(moneyTransfer, status, start, finish, response);
		protocol.setProtocolText(createProtocolText(executionStatus, result, response));
		dbController.insertOrUpdate(protocol);
	}

	private void persistFailureProtocol(MoneyTransfer moneyTransfer, LocalDateTime start, RuntimeException exception) {
		MoneyTransferProtocol protocol = createProtocol(moneyTransfer, MoneyTransferStatus.ERROR, start, LocalDateTime.now(),
				new StatusResponse(moneyTransfer.getBankOrderId(), null, null, false, false));
		protocol.setProtocolText(exception.getClass().getName() + ": " + exception.getMessage());
		dbController.insertOrUpdate(protocol);
	}

	private MoneyTransferProtocol createProtocol(MoneyTransfer moneyTransfer, MoneyTransferStatus status, LocalDateTime start,
			LocalDateTime finish, StatusResponse response) {
		MoneyTransferProtocol protocol = new MoneyTransferProtocol(moneyTransfer.getId(), status, start, finish);
		protocol.setBankOrderId(response.bankOrderId());
		protocol.setSepaOrderStatus(response.sepaOrderStatus());
		protocol.setSepaCancellationCode(response.sepaCancellationCode());
		return protocol;
	}

	private boolean isSuccessful(HBCIExecStatus status, HBCIJobResult result) {
		return status != null && status.isOK() && result != null && result.isOK();
	}

	private String createProtocolText(HBCIExecStatus executionStatus, HBCIJobResult result, StatusResponse response) {
		StringBuilder text = new StringBuilder("HBCI execution status: ").append(executionStatus);
		if (result != null && result.getJobStatus() != null) {
			text.append(System.lineSeparator()).append("HBCI job status: ").append(result.getJobStatus());
		}
		if (response.sepaOrderStatus() != null) {
			text.append(System.lineSeparator()).append("SEPA order status: ").append(response.sepaOrderStatus().getDbStateId());
		}
		return text.toString();
	}

	private record StatusResponse(String bankOrderId, SepaOrderStatus sepaOrderStatus, SepaCancellationCode sepaCancellationCode,
			boolean retryRequested, boolean successful) {
	}
}
