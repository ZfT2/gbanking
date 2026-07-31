package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class TenantLock implements AutoCloseable {

	private static final String LOCK_FILE_NAME = "gbanking.lock";

	private final Path lockDirectory;
	private final Path lockFile;
	private final FileChannel channel;
	private final FileLock fileLock;

	private TenantLock(Path lockDirectory, Path lockFile, FileChannel channel, FileLock fileLock) {
		this.lockDirectory = lockDirectory.toAbsolutePath().normalize();
		this.lockFile = lockFile.toAbsolutePath().normalize();
		this.channel = channel;
		this.fileLock = fileLock;
	}

	public static Optional<TenantLock> tryAcquire(Path lockDirectory) throws IOException {
		Path normalizedDirectory = lockDirectory.toAbsolutePath().normalize();
		Files.createDirectories(normalizedDirectory);
		Path lockFile = normalizedDirectory.resolve(LOCK_FILE_NAME);
		try (LockFileHandle lockFileHandle = new LockFileHandle(lockFile)) {
			return lockFileHandle.tryAcquire(normalizedDirectory);
		}
	}

	public boolean isFor(Path directory) {
		return lockDirectory.equals(directory.toAbsolutePath().normalize());
	}

	@Override
	public void close() throws IOException {
		try {
			if (fileLock.isValid()) {
				fileLock.release();
			}
		} finally {
			closeChannel(channel);
		}
	}

	Path getLockFile() {
		return lockFile;
	}

	private static void closeChannel(FileChannel channel) throws IOException {
		if (channel != null) {
			channel.close();
		}
	}

	private static final class LockFileHandle implements AutoCloseable {

		private final Path lockFile;
		private FileChannel channel;
		private boolean channelTransferred;

		private LockFileHandle(Path lockFile) throws IOException {
			this.lockFile = lockFile;
			this.channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}

		private Optional<TenantLock> tryAcquire(Path tenantDirectory) throws IOException {
			try {
				FileLock acquiredFileLock = channel.tryLock();
				if (acquiredFileLock == null) {
					return Optional.empty();
				}
				channelTransferred = true;
				return Optional.of(new TenantLock(tenantDirectory, lockFile, channel, acquiredFileLock));
			} catch (OverlappingFileLockException e) {
				return Optional.empty();
			}
		}

		@Override
		public void close() throws IOException {
			if (!channelTransferred) {
				closeChannel(channel);
			}
		}
	}
}
