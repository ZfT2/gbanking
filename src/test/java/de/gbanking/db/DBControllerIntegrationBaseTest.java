package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Collection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import de.gbanking.db.dao.Dao;

/**
 * Integration tests for DBController.
 *
 * - The database file is created once for the entire test run. - Before each
 * test, all tables are cleared. - After all tests, the database file is
 * deleted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DBControllerIntegrationBaseTest {

	protected DBController db;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {

		// Create fresh DBControllerForTest instance
		tempDir = Files.createTempDirectory("gb_test_");
		db = DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}
	
	protected Calendar getCalendarWithoutTime(Calendar cal){
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}
	
	protected <T extends Dao> T findById(Collection<T> list, int id) {
		for (T element : list) {
			if (element.getId() == id) {
				return (T) element;
			}
		}
		fail(String.format("Dao with ID %s not found", id));
		return null;
	}
}
