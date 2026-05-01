package de.zft2.gbanking.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

	private static final Path APPLICATION_BASE_DIRECTORY = determineApplicationBaseDirectory();

	private AppPaths() {
	}

	public static Path getApplicationBaseDirectory() {
		return APPLICATION_BASE_DIRECTORY;
	}

	public static Path resolveInApplicationDirectory(String path) {
		if (path == null || path.isBlank()) {
			return APPLICATION_BASE_DIRECTORY;
		}
		return resolveInApplicationDirectory(Paths.get(path.trim()));
	}

	public static Path resolveInApplicationDirectory(String first, String... more) {
		return resolveInApplicationDirectory(Paths.get(first, more));
	}

	public static Path resolveInApplicationDirectory(Path path) {
		if (path == null) {
			return APPLICATION_BASE_DIRECTORY;
		}
		return path.isAbsolute() ? path.normalize() : APPLICATION_BASE_DIRECTORY.resolve(path).normalize();
	}

	private static Path determineApplicationBaseDirectory() {
		try {
			Path codeSource = Paths.get(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();

			if (Files.isRegularFile(codeSource)) {
				return resolveForJar(codeSource);
			}

			if (Files.isDirectory(codeSource)) {
				return resolveForClassesDirectory(codeSource);
			}
		} catch (Exception e) {
			// Fall back to the current working directory if the code source cannot be resolved.
		}

		return Paths.get(".").toAbsolutePath().normalize();
	}

	private static Path resolveForJar(Path jarPath) {
		Path jarDirectory = jarPath.getParent();
		if (jarDirectory == null) {
			return Paths.get(".").toAbsolutePath().normalize();
		}

		if (hasName(jarDirectory, "lib") && jarDirectory.getParent() != null) {
			return jarDirectory.getParent().normalize();
		}

		return jarDirectory.normalize();
	}

	private static Path resolveForClassesDirectory(Path classesDirectory) {
		Path directoryName = classesDirectory.getFileName();
		Path targetDirectory = classesDirectory.getParent();
		if (directoryName != null && targetDirectory != null && hasName(targetDirectory, "target")
				&& ("classes".equals(directoryName.toString()) || "test-classes".equals(directoryName.toString()))
				&& targetDirectory.getParent() != null) {
			return targetDirectory.getParent().normalize();
		}

		return classesDirectory.normalize();
	}

	private static boolean hasName(Path path, String expectedName) {
		return path.getFileName() != null && expectedName.equalsIgnoreCase(path.getFileName().toString());
	}
}
