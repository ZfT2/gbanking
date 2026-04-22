package de.zft2.gbanking.db;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class DbRuntimeContext {

	private static Path currentDbDirectory = Paths.get(".");

	private DbRuntimeContext() {
	}

	public static synchronized void setCurrentDbDirectory(String dbDirectory) {
		currentDbDirectory = normalize(dbDirectory);
	}

	public static synchronized String resolveDbDirectory(String requestedDbDirectory) {
		if (requestedDbDirectory == null || requestedDbDirectory.isBlank() || ".".equals(requestedDbDirectory.trim())) {
			return currentDbDirectory.toString();
		}

		currentDbDirectory = normalize(requestedDbDirectory);
		return currentDbDirectory.toString();
	}

	public static synchronized String getCurrentDbDirectory() {
		return currentDbDirectory.toString();
	}

	private static Path normalize(String dbDirectory) {
		if (dbDirectory == null || dbDirectory.isBlank()) {
			return Paths.get(".");
		}
		return Paths.get(dbDirectory).normalize();
	}
}
