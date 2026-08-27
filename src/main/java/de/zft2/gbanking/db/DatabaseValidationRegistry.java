package de.zft2.gbanking.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

final class DatabaseValidationRegistry {

	private static final int DEFAULT_CAPACITY = 8;
	private static final int CONTENT_MARKER_SIZE = 4_096;
	private static final long DEFAULT_TIME_TO_LIVE_NANOS = Duration.ofMinutes(5).toNanos();
	private static final LinkOption[] NO_FOLLOW_LINKS = { LinkOption.NOFOLLOW_LINKS };
	private static final String[] SQLITE_SIDECAR_SUFFIXES = { "-wal", "-journal" };

	private final int capacity;
	private final long timeToLiveNanos;
	private final LongSupplier nanoTime;
	private final Map<Path, ValidationTicket> tickets = new LinkedHashMap<>(DEFAULT_CAPACITY, 0.75f, true);

	DatabaseValidationRegistry() {
		this(DEFAULT_CAPACITY, DEFAULT_TIME_TO_LIVE_NANOS, System::nanoTime);
	}

	DatabaseValidationRegistry(int capacity, long timeToLiveNanos, LongSupplier nanoTime) {
		if (capacity <= 0 || timeToLiveNanos <= 0) {
			throw new IllegalArgumentException("capacity and timeToLiveNanos must be positive");
		}
		this.capacity = capacity;
		this.timeToLiveNanos = timeToLiveNanos;
		this.nanoTime = nanoTime;
	}

	synchronized void remember(Path databaseFile, boolean fullIntegrityCheck, String instituteVersion) {
		Path normalizedFile = normalize(databaseFile);
		Optional<DatabaseFingerprint> fingerprint = DatabaseFingerprint.capture(normalizedFile);
		if (fingerprint.isEmpty()) {
			tickets.remove(normalizedFile);
			return;
		}

		long now = nanoTime.getAsLong();
		ValidationTicket previous = reusableTicket(normalizedFile, fingerprint.get(), now).orElse(null);
		boolean fullCheck = fullIntegrityCheck || previous != null && previous.fullIntegrityCheck();
		String version = instituteVersion != null
				? instituteVersion
				: previous != null ? previous.instituteVersion() : null;
		tickets.put(normalizedFile, new ValidationTicket(fingerprint.get(), fullCheck, version, now));
		prune(now);
	}

	synchronized Optional<Evidence> consume(Path databaseFile, boolean fullIntegrityCheckRequired) {
		Path normalizedFile = normalize(databaseFile);
		Optional<ValidationTicket> ticket = currentTicket(normalizedFile);
		if (ticket.isEmpty() || fullIntegrityCheckRequired && !ticket.get().fullIntegrityCheck()) {
			return Optional.empty();
		}
		tickets.remove(normalizedFile);
		return Optional.of(ticket.get().evidence());
	}

	synchronized Optional<String> validatedInstituteVersion(Path databaseFile) {
		return currentTicket(normalize(databaseFile)).map(ValidationTicket::instituteVersion);
	}

	private Optional<ValidationTicket> currentTicket(Path normalizedFile) {
		Optional<DatabaseFingerprint> fingerprint = DatabaseFingerprint.capture(normalizedFile);
		if (fingerprint.isEmpty()) {
			tickets.remove(normalizedFile);
			return Optional.empty();
		}
		return reusableTicket(normalizedFile, fingerprint.get(), nanoTime.getAsLong());
	}

	private Optional<ValidationTicket> reusableTicket(Path normalizedFile, DatabaseFingerprint fingerprint, long now) {
		ValidationTicket ticket = tickets.get(normalizedFile);
		if (ticket == null) {
			return Optional.empty();
		}
		if (now - ticket.createdAtNanos() >= timeToLiveNanos || !ticket.fingerprint().equals(fingerprint)) {
			tickets.remove(normalizedFile);
			return Optional.empty();
		}
		return Optional.of(ticket);
	}

	private void prune(long now) {
		tickets.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos() >= timeToLiveNanos);
		Iterator<Path> iterator = tickets.keySet().iterator();
		while (tickets.size() > capacity && iterator.hasNext()) {
			iterator.next();
			iterator.remove();
		}
	}

	private static Path normalize(Path databaseFile) {
		return databaseFile.toAbsolutePath().normalize();
	}

	record Evidence(boolean fullIntegrityCheck, String instituteVersion) {
	}

	private record ValidationTicket(DatabaseFingerprint fingerprint, boolean fullIntegrityCheck,
			String instituteVersion, long createdAtNanos) {

		Evidence evidence() {
			return new Evidence(fullIntegrityCheck, instituteVersion);
		}
	}

	private record DatabaseFingerprint(FileSnapshot database, FileSnapshot writeAheadLog,
			FileSnapshot rollbackJournal) {

		static Optional<DatabaseFingerprint> capture(Path databaseFile) {
			FileSnapshot database = FileSnapshot.capture(databaseFile);
			if (database == null || !database.regularFile()) {
				return Optional.empty();
			}
			FileSnapshot[] sidecars = new FileSnapshot[SQLITE_SIDECAR_SUFFIXES.length];
			for (int index = 0; index < SQLITE_SIDECAR_SUFFIXES.length; index++) {
				sidecars[index] = FileSnapshot.capture(Path.of(databaseFile + SQLITE_SIDECAR_SUFFIXES[index]));
				if (sidecars[index] == null) {
					return Optional.empty();
				}
			}
			return Optional.of(new DatabaseFingerprint(database, sidecars[0], sidecars[1]));
		}
	}

	private record FileSnapshot(boolean present, boolean regularFile, long size, FileTime modifiedAt,
			FileTime createdAt, String fileKey, String contentMarker) {

		private static final FileSnapshot ABSENT = new FileSnapshot(false, false, 0, null, null, null, null);

		static FileSnapshot capture(Path file) {
			try {
				BasicFileAttributes attributes = Files.readAttributes(
						file, BasicFileAttributes.class, NO_FOLLOW_LINKS);
				Object key = attributes.fileKey();
				String marker = attributes.isRegularFile() ? contentMarker(file) : null;
				return new FileSnapshot(true, attributes.isRegularFile(), attributes.size(),
						attributes.lastModifiedTime(), attributes.creationTime(),
						key != null ? key.toString() : null, marker);
			} catch (NoSuchFileException exception) {
				return ABSENT;
			} catch (IOException | SecurityException exception) {
				return Files.notExists(file, NO_FOLLOW_LINKS) ? ABSENT : null;
			}
		}

		private static String contentMarker(Path file) throws IOException {
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				byte[] buffer = new byte[CONTENT_MARKER_SIZE];
				try (InputStream inputStream = Files.newInputStream(file)) {
					int bytesRead = inputStream.readNBytes(buffer, 0, buffer.length);
					digest.update(buffer, 0, bytesRead);
				}
				return HexFormat.of().formatHex(digest.digest());
			} catch (NoSuchAlgorithmException exception) {
				throw new IllegalStateException("SHA-256 is unavailable", exception);
			}
		}
	}
}
