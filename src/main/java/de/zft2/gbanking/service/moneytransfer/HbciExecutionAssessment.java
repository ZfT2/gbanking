package de.zft2.gbanking.service.moneytransfer;

import java.util.List;

import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIMsgStatus;

final class HbciExecutionAssessment {

	private HbciExecutionAssessment() {
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
