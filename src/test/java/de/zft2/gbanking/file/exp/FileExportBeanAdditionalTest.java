package de.zft2.gbanking.file.exp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.gui.JavaFxTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExportBeanAdditionalTest {

	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

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

		JavaFxTestSupport.runFx(() -> exportBean.update(150, false, "Export %s", "fertig"));

		assertEquals("Export fertig", worker.getProcessingState());
		assertEquals(100, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.update(-200, true, "Export %s", "zurueckgesetzt"));

		assertEquals("Export zurueckgesetzt", worker.getProcessingState());
		assertEquals(0, worker.getWorkerProgress());
	}

	@Test
	void updateWorkerStateAccountsAndBookings_shouldNotExceedConfiguredRanges() {
		TestWorker worker = new TestWorker();
		TestExportBean exportBean = new TestExportBean(worker);
		exportBean.totalAccounts = 2;
		exportBean.totalBookings = 5;

		JavaFxTestSupport.runFx(() -> exportBean.updateAccounts(2, "Konten %d", 2));
		assertEquals("Konten 2", worker.getProcessingState());
		assertEquals(10, worker.getWorkerProgress());

		JavaFxTestSupport.runFx(() -> exportBean.updateBookings(5, "Buchungen %d", 5));
		assertEquals("Buchungen 5", worker.getProcessingState());
		assertEquals(90, worker.getWorkerProgress());
	}

	private static class TestExportBean extends FileExportBean {

		TestExportBean(BaseWorker worker) {
			super(worker);
		}

		void update(int progress, boolean updateProgress, String message, Object... param) {
			updateWorkerState(progress, updateProgress, message, param);
		}

		void updateAccounts(long exportedCount, String message, Object... param) {
			updateWorkerStateAccounts(exportedCount, message, param);
		}

		void updateBookings(long exportedCount, String message, Object... param) {
			updateWorkerStateBookings(exportedCount, message, param);
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
}
