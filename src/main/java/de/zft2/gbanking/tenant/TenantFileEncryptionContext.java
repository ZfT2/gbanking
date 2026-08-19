package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.util.AppPaths;

public final class TenantFileEncryptionContext {

	public static final String ENCRYPTED_FILE_SUFFIX = ".enc";

	private static final Logger log = LogManager.getLogger(TenantFileEncryptionContext.class);
	private static final TenantEncryptionManager ENCRYPTION_MANAGER = new TenantEncryptionManager();

	private static TenantSession activeSession;
	private static Path decryptedStatementsDirectory;

	private TenantFileEncryptionContext() {
	}

	public static synchronized void activate(TenantSession session) {
		TenantSession newSession = Objects.requireNonNull(session, "session");
		clearDecryptedStatements();
		activeSession = newSession;
		decryptedStatementsDirectory = AppPaths.resolveInApplicationDirectory("work", "tenant", newSession.profile().id(),
				TenantPaths.ACCOUNT_STATEMENTS_DIRECTORY_NAME);
		clearDecryptedStatements();
	}

	public static synchronized void deactivate() {
		clearDecryptedStatements();
		activeSession = null;
		decryptedStatementsDirectory = null;
	}

	public static Path encryptedFile(Path plaintextFile) {
		Path fileName = Objects.requireNonNull(plaintextFile.getFileName(), "file name");
		return plaintextFile.resolveSibling(fileName + ENCRYPTED_FILE_SUFFIX);
	}

	public static synchronized void encrypt(byte[] content, Path targetFile) throws IOException {
		TenantSession session = requireActiveSession();
		ENCRYPTION_MANAGER.writeEncryptedContent(targetFile, session, output -> output.write(content));
	}

	public static synchronized void encrypt(Path sourceFile, Path targetFile) throws IOException {
		TenantSession session = requireActiveSession();
		ENCRYPTION_MANAGER.encryptFile(sourceFile, targetFile, session);
		ENCRYPTION_MANAGER.verifyFile(targetFile, session.dataKey());
	}

	public static synchronized void decrypt(Path sourceFile, Path targetFile) throws IOException {
		ENCRYPTION_MANAGER.decryptFile(sourceFile, targetFile, requireActiveSession().dataKey());
	}

	public static synchronized Path decryptForOpening(Path sourceFile, String logicalFileName) throws IOException {
		TenantSession session = requireActiveSession();
		Files.createDirectories(decryptedStatementsDirectory);
		Path targetFile = Files.createTempFile(decryptedStatementsDirectory, "statement-", fileExtension(logicalFileName));
		ENCRYPTION_MANAGER.decryptFile(sourceFile, targetFile, session.dataKey());
		targetFile.toFile().deleteOnExit();
		return targetFile;
	}

	public static synchronized void moveAtomically(Path sourceFile, Path targetFile) throws IOException {
		ENCRYPTION_MANAGER.moveAtomically(sourceFile, targetFile);
	}

	private static TenantSession requireActiveSession() {
		if (activeSession == null) {
			throw new IllegalStateException("No active tenant encryption session");
		}
		return activeSession;
	}

	private static String fileExtension(String fileName) {
		if (fileName == null) {
			return ".bin";
		}
		int index = fileName.lastIndexOf('.');
		String extension = index >= 0 ? fileName.substring(index) : ".bin";
		return extension.matches("\\.[A-Za-z0-9]{1,10}") ? extension : ".bin";
	}

	private static void clearDecryptedStatements() {
		if (decryptedStatementsDirectory == null || !Files.exists(decryptedStatementsDirectory)) {
			return;
		}
		try (var paths = Files.walk(decryptedStatementsDirectory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (IOException exception) {
			log.warn("Could not completely remove temporarily decrypted account statements", exception);
		}
	}
}
