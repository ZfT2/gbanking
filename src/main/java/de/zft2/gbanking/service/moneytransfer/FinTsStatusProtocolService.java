package de.zft2.gbanking.service.moneytransfer;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRStatus;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIRetVal;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferProtocol;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.hbci.HbciJobSupport;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;

class FinTsStatusProtocolService extends AbstractDbService {

	static final String JOB_NAME = "Status";
	private static final Logger log = LogManager.getLogger(FinTsStatusProtocolService.class);

	private final MoneyTransferProtocolWriter protocolWriter = new MoneyTransferProtocolWriter(dbController, log, "FinTS status");

	MoneyTransferStatusResolution retrieveStatus(HBCIHandler handler, GBankingHBCICallback callback, MoneyTransfer moneyTransfer,
			String hbciJobId) {
		String normalizedJobId = trimToNull(hbciJobId);
		if (normalizedJobId == null || !supportsStatusProtocol(handler)) {
			return MoneyTransferStatusResolution.NOT_RESOLVED;
		}

		LocalDateTime start = LocalDateTime.now(ZoneId.systemDefault());
		try {
			HBCIJob<GVRStatus> job = createStatusJob(handler, normalizedJobId);
			callback.registerJobDescription(job, getText("UI_DIALOG_HBCI_JOB_TRANSFER_STATUS"));
			HBCIExecStatus executionStatus = handler.execute();
			LocalDateTime finish = LocalDateTime.now(ZoneId.systemDefault());
			GVRStatus result = job.getJobResult();
			GVRStatus.Entry entry = findEntry(result, normalizedJobId);
			boolean requestSuccessful = entry != null && HbciExecutionAssessment.hasBusinessResponse(executionStatus, result);
			MoneyTransferStatus resolvedStatus = requestSuccessful ? resolveTransferStatus(entry.retval) : null;
			persistProtocol(moneyTransfer, normalizedJobId, start, finish, executionStatus, result, entry, resolvedStatus);
			return new MoneyTransferStatusResolution(requestSuccessful, resolvedStatus);
		} catch (RuntimeException exception) {
			persistFailureProtocol(moneyTransfer, normalizedJobId, start, exception);
			log.warn("FinTS status protocol request failed. transferId={}", moneyTransfer.getId(), exception);
			return MoneyTransferStatusResolution.NOT_RESOLVED;
		}
	}

	private boolean supportsStatusProtocol(HBCIHandler handler) {
		boolean supported = HbciJobSupport.isSupported(handler, JOB_NAME);
		if (!supported) {
			log.debug("Lowlevel HBCI job {} is not supported by this bank access.", JOB_NAME);
		}
		return supported;
	}

	private HBCIJob<GVRStatus> createStatusJob(HBCIHandler handler, String hbciJobId) {
		HBCIJob<GVRStatus> job = ServiceRegistry.getService(BankAccessService.class).newHbciJob(handler, JOB_NAME);
		job.setParam("jobid", hbciJobId);
		job.addToQueue();
		return job;
	}

	private GVRStatus.Entry findEntry(GVRStatus result, String hbciJobId) {
		String[] reference = hbciJobId.split("/", -1);
		if (result == null || reference.length != 4) {
			return null;
		}
		return Arrays.stream(result.getStatusData())
				.filter(entry -> entry != null && entry.retval != null)
				.filter(entry -> Objects.equals(reference[1], entry.dialogid)
						&& Objects.equals(reference[2], entry.msgnum)
						&& Objects.equals(reference[3], entry.retval.segref))
				.findFirst().orElse(null);
	}

	private MoneyTransferStatus resolveTransferStatus(HBCIRetVal returnValue) {
		String code = returnValue != null ? trimToNull(returnValue.code) : null;
		if ("0020".equals(code)) {
			return MoneyTransferStatus.SENT;
		}
		return code != null && code.startsWith("9") ? MoneyTransferStatus.ERROR : null;
	}

	private void persistProtocol(MoneyTransfer moneyTransfer, String hbciJobId, LocalDateTime start, LocalDateTime finish,
			HBCIExecStatus executionStatus, GVRStatus result, GVRStatus.Entry entry, MoneyTransferStatus resolvedStatus) {
		MoneyTransferStatus protocolStatus = resolvedStatus != null && moneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.UNCERTAIN
				? resolvedStatus : moneyTransfer.getMoneytransferStatus();
		MoneyTransferProtocol protocol = new MoneyTransferProtocol(moneyTransfer.getId(), protocolStatus, start, finish);
		protocol.setHbciJobId(hbciJobId);
		String technicalProtocol = createProtocolText(executionStatus, result, entry);
		protocolWriter.logTechnicalProtocol(moneyTransfer, technicalProtocol);
		if (resolvedStatus == null) {
			MoneyTransferProtocolEvaluator.evaluateUncertain(technicalProtocol, false, null, false).applyTo(protocol);
		} else {
			MoneyTransferProtocolEvaluator.evaluate(resolvedStatus == MoneyTransferStatus.SENT, technicalProtocol, false, null, false)
					.applyTo(protocol);
		}
		dbController.executeInTransaction(() -> {
			if (protocolStatus != moneyTransfer.getMoneytransferStatus()) {
				moneyTransfer.setMoneytransferStatus(protocolStatus);
				dbController.insertOrUpdate(moneyTransfer);
			}
			dbController.insertOrUpdate(protocol);
		});
	}

	private void persistFailureProtocol(MoneyTransfer moneyTransfer, String hbciJobId, LocalDateTime start, RuntimeException exception) {
		MoneyTransferProtocolData data = new MoneyTransferProtocolData(null, hbciJobId, null, null);
		protocolWriter.persistFailureProtocol(moneyTransfer, start, exception, data,
				technicalProtocol -> MoneyTransferProtocolEvaluator.evaluateUncertain(technicalProtocol, false, null, false));
	}

	private String createProtocolText(HBCIExecStatus executionStatus, GVRStatus result, GVRStatus.Entry entry) {
		return MoneyTransferProtocolText.of("HBCI status protocol execution status", executionStatus)
				.appendNonNull("HBCI status protocol job status", result != null ? result.getJobStatus() : null)
				.appendNonNull("Referenced order status", entry != null ? entry.retval : null)
				.build();
	}
}
