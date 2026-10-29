package de.zft2.gbanking.db;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the rollback journal, write-ahead log and shared-memory files that SQLite keeps next to a database file.
 */
public final class SqliteSidecarFiles {

	private static final List<String> SUFFIXES = List.of("-journal", "-wal", "-shm");

	private SqliteSidecarFiles() {
	}

	public static List<Path> of(Path databaseFile) {
		return SUFFIXES.stream().map(suffix -> databaseFile.resolveSibling(databaseFile.getFileName() + suffix)).toList();
	}
}
