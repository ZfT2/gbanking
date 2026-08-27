package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseValidationRegistryTest {

	private static final long TEST_TIME_TO_LIVE = 10;

	@TempDir
	private Path tempDirectory;

	@Test
	void validationShouldBeReusableExactlyOnceWithoutConsumingVersionPeek() throws Exception {
		Path databaseFile = createFile("institute.db", "database");
		DatabaseValidationRegistry registry = registry(new AtomicLong());
		registry.remember(databaseFile, false, "2026-07-29");

		assertEquals("2026-07-29", registry.validatedInstituteVersion(databaseFile).orElseThrow());
		assertEquals("2026-07-29", registry.validatedInstituteVersion(databaseFile).orElseThrow());

		DatabaseValidationRegistry.Evidence evidence = registry.consume(databaseFile, false).orElseThrow();
		assertFalse(evidence.fullIntegrityCheck());
		assertEquals("2026-07-29", evidence.instituteVersion());
		assertTrue(registry.consume(databaseFile, false).isEmpty());
	}

	@Test
	void fullValidationShouldSatisfyQuickButQuickShouldNotSatisfyFull() throws Exception {
		Path fullDatabase = createFile("full.db", "full");
		Path quickDatabase = createFile("quick.db", "quick");
		DatabaseValidationRegistry registry = registry(new AtomicLong());
		registry.remember(fullDatabase, true, null);
		registry.remember(quickDatabase, false, null);

		assertTrue(registry.consume(fullDatabase, false).orElseThrow().fullIntegrityCheck());
		assertTrue(registry.consume(quickDatabase, true).isEmpty());
		assertTrue(registry.consume(quickDatabase, false).isPresent());
	}

	@Test
	void sameSizeRewriteShouldInvalidateValidationEvenWithRestoredTimestamp() throws Exception {
		Path databaseFile = createFile("same-size.db", "first");
		FileTime modifiedAt = Files.getLastModifiedTime(databaseFile);
		DatabaseValidationRegistry registry = registry(new AtomicLong());
		registry.remember(databaseFile, false, null);

		Files.writeString(databaseFile, "other");
		Files.setLastModifiedTime(databaseFile, modifiedAt);

		assertTrue(registry.consume(databaseFile, false).isEmpty());
	}

	@Test
	void sqliteSidecarChangeShouldInvalidateValidation() throws Exception {
		Path databaseFile = createFile("sidecar.db", "database");
		DatabaseValidationRegistry registry = registry(new AtomicLong());
		registry.remember(databaseFile, false, null);

		Files.writeString(Path.of(databaseFile + "-wal"), "committed WAL content");

		assertTrue(registry.consume(databaseFile, false).isEmpty());
	}

	@Test
	void expiredAndOldestValidationShouldNotBeReusable() throws Exception {
		AtomicLong time = new AtomicLong();
		DatabaseValidationRegistry registry = new DatabaseValidationRegistry(2, TEST_TIME_TO_LIVE, time::get);
		Path first = createFile("first.db", "first");
		Path second = createFile("second.db", "second");
		Path third = createFile("third.db", "third");
		registry.remember(first, false, null);
		registry.remember(second, false, null);
		registry.remember(third, false, null);

		assertTrue(registry.consume(first, false).isEmpty());
		assertTrue(registry.consume(second, false).isPresent());
		time.set(TEST_TIME_TO_LIVE);
		assertTrue(registry.consume(third, false).isEmpty());
	}

	private Path createFile(String fileName, String content) throws Exception {
		Path file = tempDirectory.resolve(fileName);
		Files.writeString(file, content);
		return file;
	}

	private static DatabaseValidationRegistry registry(AtomicLong time) {
		return new DatabaseValidationRegistry(8, TEST_TIME_TO_LIVE, time::get);
	}
}
