package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.zft2.gbanking.concurrent.ProgressReporter;
import de.zft2.gbanking.db.TestDatabaseExtension;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.gui.JavaFxTestSupport;
import de.zft2.gbanking.messages.Messages;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportBeanAdditionalTest {

	@RegisterExtension
	static final TestDatabaseExtension database = TestDatabaseExtension.withoutClearing();

	@Test
	void exportConstants_shouldResolveKnownDescriptionsAndExposeXmlTags() {
		assertEquals(FileExportBean.ExportConstants.IBAN, FileExportBean.ExportConstants.forString("IBAN"));
		assertEquals("IBAN", FileExportBean.ExportConstants.IBAN.getTag());
		assertEquals("Datum", FileExportBean.ExportConstants.DATE.toString());
		assertNull(FileExportBean.ExportConstants.forString("unbekannt"));
	}

	@Test
	void updateWorkerState_shouldFormatMessageAndClampAbsoluteProgress() {
		TestWorker worker = new TestWorker();
		TestExportBean exportBean = new TestExportBean(worker);

		JavaFxTestSupport.runFx(() -> exportBean.update(150, "UI_PROGRESS_EXPORT_ACCOUNT", "fertig"));

		assertEquals(Messages.getInstance().getFormattedMessage("UI_PROGRESS_EXPORT_ACCOUNT", "fertig"), worker.getProcessingState());
		assertEquals(100, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.update(-200, "UI_PROGRESS_EXPORT_ACCOUNT", "zurueckgesetzt"));

		assertEquals(Messages.getInstance().getFormattedMessage("UI_PROGRESS_EXPORT_ACCOUNT", "zurueckgesetzt"), worker.getProcessingState());
		assertEquals(0, worker.getWorkerProgress());
	}

	@Test
	void reportAccountProgress_shouldStayInsideTheAccountPhase() {
		TestWorker worker = new TestWorker();
		TestExportBean exportBean = new TestExportBean(worker);
		exportBean.totalAccounts = 2;

		JavaFxTestSupport.runFx(() -> exportBean.reportAccounts(1));
		assertEquals(5, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.reportAccounts(2));
		assertEquals(10, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.reportAccounts(3));
		assertEquals(10, worker.getWorkerProgress());
	}

	@Test
	void countExportedBooking_shouldAdvanceTheBookingPhase() {
		TestWorker worker = new TestWorker();
		TestExportBean exportBean = new TestExportBean(worker);
		exportBean.totalBookings = 4;

		JavaFxTestSupport.runFx(() -> exportBean.countBooking());
		assertEquals(30, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.countBooking());
		assertEquals(50, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.countBooking());
		assertEquals(70, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.countBooking());
		assertEquals(90, worker.getWorkerProgress());
	}

	@Test
	void countExportedBooking_shouldReportOnlyChangedPercentages() {
		CountingProgressReporter reporter = new CountingProgressReporter();
		TestExportBean exportBean = new TestExportBean(reporter);
		exportBean.totalBookings = 1000;

		for (int booking = 0; booking < 20; booking++) {
			exportBean.countBooking();
		}

		assertTrue(reporter.getProgressValues().size() < 20, "The progress should not be reported for every booking");
		assertEquals(12, reporter.getProgressValues().get(reporter.getProgressValues().size() - 1).intValue());
	}

	@Test
	void updateState_shouldReportTheMessageWithoutChangingTheProgress() {
		TestWorker worker = new TestWorker();
		TestExportBean exportBean = new TestExportBean(worker);

		JavaFxTestSupport.runFx(() -> exportBean.update(40, "UI_PROGRESS_EXPORT_ACCOUNT", "Test"));
		JavaFxTestSupport.runFx(() -> exportBean.state("UI_PROGRESS_EXPORT_BOOKINGS_ACCOUNT", "Test"));

		assertEquals(Messages.getInstance().getFormattedMessage("UI_PROGRESS_EXPORT_BOOKINGS_ACCOUNT", "Test"), worker.getProcessingState());
		assertEquals(40, worker.getWorkerProgress());
	}

	private static class TestExportBean extends FileExportBean {

		TestExportBean(ProgressReporter progressReporter) {
			super(progressReporter);
		}

		void update(int progress, String message, Object... param) {
			updateWorkerState(progress, message, param);
		}

		void state(String message, Object... param) {
			updateState(message, param);
		}

		void reportAccounts(long processedAccounts) {
			reportAccountProgress(processedAccounts);
		}

		void countBooking() {
			countExportedBooking();
		}

		@Override
		public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) {
			return true;
		}
	}

	private static class TestWorker extends BaseWorker {
		@Override
		protected Void call() {
			return null;
		}
	}

	private static class CountingProgressReporter implements ProgressReporter {

		private final List<Double> progressValues = new ArrayList<>();

		@Override
		public void reportState(String state) {
		}

		@Override
		public void reportProgress(double progress) {
			progressValues.add(progress);
		}

		@Override
		public void checkCancelled() {
		}

		List<Double> getProgressValues() {
			return progressValues;
		}
	}
}
