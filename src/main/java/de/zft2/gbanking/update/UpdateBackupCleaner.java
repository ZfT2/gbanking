package de.zft2.gbanking.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class UpdateBackupCleaner {

	private static final Logger log = LogManager.getLogger(UpdateBackupCleaner.class);
	private static final String UPDATES_DIRECTORY = ".updates";
	private static final String UPDATE_DIRECTORY_PREFIX = "gbanking-update-";
	private static final String INSTALL_LOG = "install-update.log";

	void cleanup(Path installDirectory) {
		Path updatesDirectory = installDirectory.resolve(UPDATES_DIRECTORY);
		if (!Files.isDirectory(updatesDirectory)) {
			return;
		}

		try (Stream<Path> paths = Files.list(updatesDirectory)) {
			List<Path> updateDirectories = paths.filter(path -> {
				Path fileName = path.getFileName();
				return Files.isDirectory(path) && fileName != null
						&& fileName.toString().startsWith(UPDATE_DIRECTORY_PREFIX);
			})
					.toList();
			for (Path updateDirectory : updateDirectories) {
				deleteStaleFiles(updateDirectory);
			}
		} catch (IOException exception) {
			log.warn("Could not inspect update backup directory {}", updatesDirectory, exception);
		}
	}

	static void deleteTree(Path directory) {
		deleteTree(directory, path -> true);
	}

	private static void deleteStaleFiles(Path updateDirectory) {
		// The installer log is kept so that failed installations can still be analyzed.
		Path installLog = updateDirectory.resolve(INSTALL_LOG);
		boolean keepDirectory = Files.exists(installLog);
		deleteTree(updateDirectory, path -> !path.equals(installLog) && !(keepDirectory && path.equals(updateDirectory)));
	}

	private static void deleteTree(Path directory, Predicate<Path> filter) {
		try (Stream<Path> paths = Files.walk(directory)) {
			List<Path> pathsToDelete = paths.filter(filter).sorted(Comparator.reverseOrder()).toList();
			for (Path path : pathsToDelete) {
				Files.deleteIfExists(path);
			}
			if (!pathsToDelete.isEmpty()) {
				log.info("Removed application update files in {}", directory);
			}
		} catch (IOException exception) {
			log.warn("Could not remove application update files in {}", directory, exception);
		}
	}
}
