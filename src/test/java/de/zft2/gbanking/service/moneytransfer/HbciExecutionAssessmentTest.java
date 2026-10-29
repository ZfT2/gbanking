package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.HBCIJobResultImpl;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIMsgStatus;
import org.kapott.hbci.status.HBCIRetVal;

class HbciExecutionAssessmentTest {

	@BeforeEach
	void initHbciUtils() {
		// hbci4j logs bank errors and exceptions, which requires an initialized thread context
		HBCIUtils.initThread(new Properties(), new HBCICallbackConsole());
	}

	@AfterEach
	void closeHbciUtils() {
		HBCIUtils.doneThread();
	}

	@Test
	void shouldRecognizeFailureLimitedToDialogEnd() {
		assertTrue(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), successStatus(), new HBCIMsgStatus())));
	}

	@Test
	void shouldRejectPayloadAndSuccessfulDialogEndStatuses() {
		assertFalse(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), new HBCIMsgStatus(), new HBCIMsgStatus())));
		assertFalse(HbciExecutionAssessment.isOnlyDialogEndFailure(createStatus(successStatus(), successStatus(), successStatus())));
	}

	@Test
	void shouldRecognizeBankRejectionOnlyByErrorCodes() {
		assertTrue(HbciExecutionAssessment.isRejectedByBank(createJobResult("9010", null)));
		assertTrue(HbciExecutionAssessment.isRejectedByBank(createJobResult(null, "9050")));
		assertFalse(HbciExecutionAssessment.isRejectedByBank(createJobResult("0020", "9050")));
		assertFalse(HbciExecutionAssessment.isRejectedByBank(null));
		HBCIJobResultImpl exceptionOnly = createJobResult(null, null);
		exceptionOnly.globStatus.addException(new IllegalStateException("connection lost"));
		assertFalse(HbciExecutionAssessment.isRejectedByBank(exceptionOnly));
	}

	@Test
	void shouldTreatOnlyUnsentJobsAsNotSubmitted() {
		assertTrue(HbciExecutionAssessment.wasNotSubmitted(new HBCIExecStatus()));
		assertTrue(HbciExecutionAssessment.wasNotSubmitted(createStatus(new HBCIMsgStatus(), null, null)));
		assertFalse(HbciExecutionAssessment.wasNotSubmitted(createStatus(successStatus(), new HBCIMsgStatus(), null)));
		assertFalse(HbciExecutionAssessment.wasNotSubmitted(null));
		HBCIExecStatus abortedDialog = new HBCIExecStatus();
		abortedDialog.addException("customer-1", new IllegalStateException("connection lost"));
		assertFalse(HbciExecutionAssessment.wasNotSubmitted(abortedDialog));
	}

	private static HBCIJobResultImpl createJobResult(String jobCode, String globalCode) {
		HBCIJobResultImpl result = new HBCIJobResultImpl();
		if (jobCode != null) {
			result.jobStatus.addRetVal(new HBCIRetVal(null, null, null, jobCode, "job", null));
		}
		if (globalCode != null) {
			result.globStatus.addRetVal(new HBCIRetVal(null, null, null, globalCode, "global", null));
		}
		return result;
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
