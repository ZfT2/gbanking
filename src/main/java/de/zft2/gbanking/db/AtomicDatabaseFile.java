package de.zft2.gbanking.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;

final class AtomicDatabaseFile {

	private static final List<String> SQLITE_SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");

	private AtomicDatabaseFile() {
	}

	static void create(Path targetFile, Initializer initializer) throws IOException, SQLException {
		Path normalizedTarget = targetFile.toAbsolutePath().normalize();
		Path parentDirectory = normalizedTarget.getParent();
		if (parentDirectory == null) {
			throw new IOException("Database file requires a parent directory");
		}
		Files.createDirectories(parentDirectory);

		Path stagingFile = Files.createTempFile(parentDirectory, "." + normalizedTarget.getFileName() + "-", ".creating");
		boolean moved = false;
		Exception failure = null;
		try {
			initializer.initialize(stagingFile);
			move(stagingFile, normalizedTarget);
			moved = true;
		} catch (IOException | SQLException | RuntimeException exception) {
			failure = exception;
			throw exception;
		} finally {
			if (!moved) {
				cleanup(stagingFile, failure);
			}
		}
	}

	private static void move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target);
		}
	}

	private static void cleanup(Path stagingFile, Exception originalFailure) throws IOException {
		IOException cleanupFailure = null;
		for (String suffix : SQLITE_SIDECAR_SUFFIXES) {
			cleanupFailure = delete(stagingFile.resolveSibling(stagingFile.getFileName() + suffix), cleanupFailure);
		}
		cleanupFailure = delete(stagingFile, cleanupFailure);
		if (cleanupFailure != null) {
			if (originalFailure != null) {
				originalFailure.addSuppressed(cleanupFailure);
			} else {
				throw cleanupFailure;
			}
		}
	}

	private static IOException delete(Path file, IOException failure) {
		try {
			Files.deleteIfExists(file);
			return failure;
		} catch (IOException exception) {
			if (failure == null) {
				return exception;
			}
			failure.addSuppressed(exception);
			return failure;
		}
	}

	@FunctionalInterface
	interface Initializer {

		void initialize(Path stagingFile) throws IOException, SQLException;
	}
}
