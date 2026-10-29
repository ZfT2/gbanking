package de.zft2.gbanking.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Null-safe helpers to extract the plain file name from paths, mainly for log and UI messages.
 */
public final class FileNames {

	private FileNames() {
	}

	/**
	 * Returns the file name of the given path, the whole path if it has no name element, or {@code null} for {@code null}.
	 */
	public static String fileName(Path path) {
		if (path == null) {
			return null;
		}
		Path name = path.getFileName();
		return name != null ? name.toString() : path.toString();
	}

	/**
	 * Returns the file name part of the given path text, or the text itself if it is {@code null} or not a valid path.
	 */
	public static String fileName(String path) {
		if (path == null) {
			return null;
		}
		try {
			return fileName(Path.of(path));
		} catch (InvalidPathException e) {
			return path;
		}
	}
}
