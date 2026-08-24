package de.zft2.gbanking.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class UpdateBackupCleaner {

	private static final Logger log = LogManager.getLogger(UpdateBackupCleaner.class);
	private static final String UPDATES_DIRECTORY = ".updates";
	private static final String UPDATE_DIRECTORY_PREFIX = "gbanking-update-";
	private static final String BACKUP_DIRECTORY = "backup";

	void cleanup(Path installDirectory) {
		Path updatesDirectory = installDirectory.resolve(UPDATES_DIRECTORY);
		if (!Files.isDirectory(updatesDirectory)) {
			return;
		}

		try (Stream<Path> paths = Files.list(updatesDirectory)) {
			List<Path> updateDirectories = paths.filter(path -> Files.isDirectory(path))
					.filter(path -> path.getFileName().toString().startsWith(UPDATE_DIRECTORY_PREFIX))
					.toList();
			for (Path updateDirectory : updateDirectories) {
				deleteBackup(updateDirectory.resolve(BACKUP_DIRECTORY));
			}
		} catch (IOException exception) {
			log.warn("Could not inspect update backup directory {}", updatesDirectory, exception);
		}
	}

	private void deleteBackup(Path backupDirectory) {
		if (!Files.isDirectory(backupDirectory)) {
			return;
		}

		try (Stream<Path> paths = Files.walk(backupDirectory)) {
			List<Path> pathsToDelete = paths.sorted(Comparator.reverseOrder()).toList();
			for (Path path : pathsToDelete) {
				Files.deleteIfExists(path);
			}
			log.info("Removed successful application update backup: {}", backupDirectory);
		} catch (IOException exception) {
			log.warn("Could not remove application update backup {}", backupDirectory, exception);
		}
	}
}
