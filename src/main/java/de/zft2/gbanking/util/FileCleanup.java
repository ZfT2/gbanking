package de.zft2.gbanking.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class FileCleanup {

	private FileCleanup() {
	}

	public static void deleteTree(Path root) throws IOException {
		deleteTree(root, path -> true);
	}

	/**
	 * Deletes {@code root} and everything below it that matches {@code filter}, children before their parents.
	 * Symbolic links are deleted themselves and never followed.
	 *
	 * @return {@code true} if at least one path was deleted
	 */
	public static boolean deleteTree(Path root, Predicate<Path> filter) throws IOException {
		if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			List<Path> pathsToDelete = paths.filter(filter).sorted(Comparator.reverseOrder()).toList();
			for (Path path : pathsToDelete) {
				Files.deleteIfExists(path);
			}
			return !pathsToDelete.isEmpty();
		}
	}

	/**
	 * Deletes a partially written file after {@code originalFailure}; a cleanup failure is attached as suppressed
	 * exception so that it never hides the original cause.
	 */
	public static void deleteAfterFailure(Path file, Exception originalFailure) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException cleanupFailure) {
			originalFailure.addSuppressed(cleanupFailure);
		}
	}
}
