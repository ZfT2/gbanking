package de.zft2.gbanking.service.moneytransfer;

import java.util.List;

import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIMsgStatus;
import org.kapott.hbci.status.HBCIStatus;

final class HbciExecutionAssessment {

	private HbciExecutionAssessment() {
	}

	/**
	 * Checks whether the job delivered a successful business response, even if only the dialog end failed.
	 */
	static boolean hasBusinessResponse(HBCIExecStatus status, HBCIJobResult result) {
		return result != null && result.isOK() && (status != null && status.isOK() || isOnlyDialogEndFailure(status));
	}

	static boolean isOnlyDialogEndFailure(HBCIExecStatus status) {
		if (status == null || status.isOK()) {
			return false;
		}
		List<String> customerIds = status.getCustomerIds();
		if (customerIds == null || customerIds.isEmpty()) {
			return false;
		}

		boolean endFailureFound = false;
		for (String customerId : customerIds) {
			List<Exception> exceptions = status.getExceptions(customerId);
			if (exceptions != null && !exceptions.isEmpty()) {
				return false;
			}
			HBCIDialogStatus dialogStatus = status.getDialogStatus(customerId);
			if (!hasSuccessfulPayload(dialogStatus)) {
				return false;
			}
			if (dialogStatus.endStatus == null || !dialogStatus.endStatus.isOK()) {
				endFailureFound = true;
			}
		}
		return endFailureFound;
	}

	/**
	 * Checks whether the bank explicitly rejected the job with an error code. Exceptions are deliberately ignored, because an
	 * exception while receiving the response does not prove that the bank did not execute the job.
	 */
	static boolean isRejectedByBank(HBCIJobResult result) {
		HBCIStatus jobStatus = result != null ? result.getJobStatus() : null;
		if (jobStatus == null) {
			return false;
		}
		HBCIStatus globStatus = result.getGlobStatus();
		return jobStatus.hasErrors() || globStatus != null && globStatus.hasErrors() && !jobStatus.isOK();
	}

	/**
	 * Checks whether the job provably never reached the bank, because no dialog was opened or the dialog initialization failed
	 * before any job message was sent. Unknown states are treated as possibly submitted.
	 */
	static boolean wasNotSubmitted(HBCIExecStatus status) {
		if (status == null) {
			return false;
		}
		for (String customerId : status.getCustomerIds()) {
			HBCIDialogStatus dialogStatus = status.getDialogStatus(customerId);
			if (dialogStatus == null || dialogStatus.initStatus != null && dialogStatus.initStatus.isOK()
					&& dialogStatus.msgStatus != null && dialogStatus.msgStatus.length > 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasSuccessfulPayload(HBCIDialogStatus status) {
		if (status == null || status.initStatus == null || !status.initStatus.isOK()
				|| status.msgStatus == null || status.msgStatus.length == 0) {
			return false;
		}
		for (HBCIMsgStatus messageStatus : status.msgStatus) {
			if (messageStatus == null || !messageStatus.isOK()) {
				return false;
			}
		}
		return true;
	}
}
