package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIMsgStatus;
import org.kapott.hbci.status.HBCIRetVal;

class HbciExecutionAssessmentTest {

	@Test
	void shouldRecognizeFailureLimitedToDialogEnd() {
		assertTrue(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), successStatus(), new HBCIMsgStatus())));
	}

	@Test
	void shouldRejectPayloadAndSuccessfulDialogEndStatuses() {
		assertFalse(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), new HBCIMsgStatus(), new HBCIMsgStatus())));
		assertFalse(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), successStatus(), successStatus())));
	}

	private static HBCIExecStatus createStatus(HBCIMsgStatus initStatus, HBCIMsgStatus messageStatus, HBCIMsgStatus endStatus) {
		HBCIDialogStatus dialogStatus = new HBCIDialogStatus();
		dialogStatus.setInitStatus(initStatus);
		dialogStatus.setMsgStatus(new HBCIMsgStatus[] { messageStatus });
		dialogStatus.setEndStatus(endStatus);
		HBCIExecStatus executionStatus = new HBCIExecStatus();
		executionStatus.addDialogStatus("customer-1", dialogStatus);
		return executionStatus;
	}

	private static HBCIMsgStatus successStatus() {
		HBCIMsgStatus status = new HBCIMsgStatus();
		status.globStatus.addRetVal(new HBCIRetVal(null, null, null, "0010", "OK", null));
		return status;
	}
}
