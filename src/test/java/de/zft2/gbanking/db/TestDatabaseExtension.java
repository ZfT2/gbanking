package de.zft2.gbanking.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Provides a temporary test database per test class: the database is opened before all tests (or earlier on first
 * access, e.g. from a field initializer), all tables are cleared before each test, and the database is closed and
 * deleted after all tests.
 * <p>
 * The extension has to be registered in a static field, otherwise the class-level callbacks are not honored:
 * {@code @RegisterExtension static final TestDatabaseExtension database = new TestDatabaseExtension();}
 */
public final class TestDatabaseExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

	private final boolean clearBeforeEach;
	private Path directory;

	public TestDatabaseExtension() {
		this(true);
	}

	private TestDatabaseExtension(boolean clearBeforeEach) {
		this.clearBeforeEach = clearBeforeEach;
	}

	/** Creates an extension that keeps the table contents between the tests of a class. */
	public static TestDatabaseExtension withoutClearing() {
		return new TestDatabaseExtension(false);
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		controller();
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		if (clearBeforeEach) {
			DBControllerTestUtil.clearAllTables(DBController.getConnection());
		}
	}

	@Override
	public void afterAll(ExtensionContext context) throws IOException {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(directory);
		directory = null;
	}

	/** Returns the directory of the temporary database, creating it on first access. */
	public Path directory() {
		if (directory == null) {
			try {
				directory = Files.createTempDirectory("gb_test_");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return directory;
	}

	/** Returns the controller of the temporary database, opening the database on first access. */
	public DBController controller() {
		return DBController.getInstance(directory().toString());
	}
}
