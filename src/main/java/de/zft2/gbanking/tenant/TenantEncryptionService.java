package de.zft2.gbanking.tenant;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import de.zft2.gbanking.BaseMessages;

public class TenantEncryptionService implements BaseMessages {

	private static final byte[] CONTAINER_MAGIC = "GBANK001".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] KEY_AAD = "GBanking tenant data key v1".getBytes(StandardCharsets.US_ASCII);
	private static final int CONTAINER_VERSION = 1;
	private static final byte[] CONTENT_AAD = ByteBuffer.allocate(CONTAINER_MAGIC.length + Integer.BYTES)
			.put(CONTAINER_MAGIC).putInt(CONTAINER_VERSION).array();
	private static final int DATA_KEY_LENGTH_BYTES = 32;
	private static final int SALT_LENGTH_BYTES = 16;
	private static final int NONCE_LENGTH_BYTES = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int WRAPPED_KEY_LENGTH_BYTES = DATA_KEY_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / Byte.SIZE;
	private static final int DEFAULT_KDF_ITERATIONS = 210_000;
	private static final int BUFFER_SIZE = 64 * 1024;
	private static final int HEADER_LENGTH = CONTAINER_MAGIC.length + Integer.BYTES + Integer.BYTES + SALT_LENGTH_BYTES
			+ NONCE_LENGTH_BYTES + WRAPPED_KEY_LENGTH_BYTES + NONCE_LENGTH_BYTES;
	private static final String UI_ERROR_ENCRYPTION_KEY = "UI_ERROR_TENANT_ENCRYPTION_KEY";

	private final SecureRandom secureRandom = new SecureRandom();

	TenantKeyEnvelope createEnvelope(char[] password) {
		byte[] dataKey = randomBytes(DATA_KEY_LENGTH_BYTES);
		try (TenantDataKey tenantDataKey = new TenantDataKey(dataKey)) {
			return createEnvelope(password, tenantDataKey);
		} finally {
			Arrays.fill(dataKey, (byte) 0);
		}
	}

	TenantKeyEnvelope createEnvelope(char[] password, TenantDataKey dataKey) {
		byte[] salt = randomBytes(SALT_LENGTH_BYTES);
		byte[] nonce = randomBytes(NONCE_LENGTH_BYTES);
		byte[] rawDataKey = dataKey.copyBytes();
		try {
			SecretKey wrappingKey = deriveKey(password, salt, DEFAULT_KDF_ITERATIONS);
			Cipher cipher = initializeCipher(Cipher.ENCRYPT_MODE, wrappingKey, nonce, KEY_AAD);
			byte[] wrappedKey = cipher.doFinal(rawDataKey);
			return new TenantKeyEnvelope(DEFAULT_KDF_ITERATIONS, encode(salt), encode(nonce), encode(wrappedKey));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(getText(UI_ERROR_ENCRYPTION_KEY), e);
		} finally {
			Arrays.fill(rawDataKey, (byte) 0);
			Arrays.fill(salt, (byte) 0);
			Arrays.fill(nonce, (byte) 0);
		}
	}

	TenantDataKey unlockDataKey(TenantProfile profile, char[] password) {
		return unlockDataKey(profile.encryptionEnvelope(), password);
	}

	public void decryptContainer(Path containerFile, Path targetFile, char[] password) throws IOException {
		TenantKeyEnvelope envelope;
		try (DataInputStream inputStream = openContainerInput(containerFile)) {
			envelope = readHeader(inputStream).envelope();
		}
		try (TenantDataKey dataKey = unlockDataKey(envelope, password)) {
			decryptFile(containerFile, targetFile, dataKey);
		}
	}

	private TenantDataKey unlockDataKey(TenantKeyEnvelope envelope, char[] password) {
		try (SensitiveByteArrays sensitiveBytes = new SensitiveByteArrays()) {
			byte[] salt = sensitiveBytes.add(decode(envelope.salt()));
			byte[] nonce = sensitiveBytes.add(decode(envelope.nonce()));
			byte[] wrappedKey = sensitiveBytes.add(decode(envelope.wrappedKey()));
			validateEnvelope(envelope.iterations(), salt, nonce, wrappedKey);
			SecretKey wrappingKey = deriveKey(password, salt, envelope.iterations());
			Cipher cipher = initializeCipher(Cipher.DECRYPT_MODE, wrappingKey, nonce, KEY_AAD);
			byte[] dataKey = sensitiveBytes.add(cipher.doFinal(wrappedKey));
			if (dataKey.length != DATA_KEY_LENGTH_BYTES) {
				throw new IllegalStateException(getText(UI_ERROR_ENCRYPTION_KEY));
			}
			return new TenantDataKey(dataKey);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException(getText(UI_ERROR_ENCRYPTION_KEY), e);
		}
	}

	void encryptFile(Path sourceFile, Path containerFile, TenantSession session) throws IOException {
		writeContainer(containerFile, session, outputStream -> Files.copy(sourceFile, outputStream));
	}

	void verifyFile(Path containerFile, TenantDataKey dataKey) throws IOException {
		decryptContent(containerFile, dataKey, OutputStream.nullOutputStream());
	}

	void writeEncryptedContent(Path containerFile, TenantSession session, ContentWriter contentWriter) throws IOException {
		writeContainer(containerFile, session, contentWriter);
		decryptContent(containerFile, session.dataKey(), OutputStream.nullOutputStream());
	}

	void decryptFile(Path containerFile, Path targetFile, TenantDataKey dataKey) throws IOException {
		Files.deleteIfExists(targetFile);
		try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetFile, StandardOpenOption.CREATE_NEW))) {
			decryptContent(containerFile, dataKey, outputStream);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(targetFile);
			throw e;
		}
		forceFile(targetFile);
	}

	void moveAtomically(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			throw new IOException("Atomic replacement is not supported for tenant data", e);
		}
	}

	void updateContainerEnvelopes(TenantPaths tenantPaths, TenantProfile updatedProfile, TenantDataKey dataKey) throws IOException {
		List<Path> containers;
		try (Stream<Path> backupFiles = filesIn(tenantPaths.backupDirectory());
				Stream<Path> statementFiles = filesIn(tenantPaths.accountStatementsDirectory())) {
			Stream<Path> encryptedStatements = statementFiles.filter(path -> fileName(path)
					.endsWith(TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX));
			containers = Stream.concat(Stream.concat(Stream.of(tenantPaths.encryptedDatabaseFile()), backupFiles), encryptedStatements)
					.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
					.filter(path -> path.equals(tenantPaths.encryptedDatabaseFile())
							|| fileName(path).endsWith(TenantBackupService.BACKUP_SUFFIX)
							|| fileName(path).endsWith(TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX))
					.toList();
		}
		for (Path container : containers) {
			updateContainerEnvelope(container, updatedProfile.encryptionEnvelope(), dataKey);
		}
	}

	private Stream<Path> filesIn(Path directory) throws IOException {
		return Files.exists(directory) ? Files.list(directory) : Stream.empty();
	}

	private void writeContainer(Path containerFile, TenantSession session, ContentWriter contentWriter) throws IOException {
		Path parentDirectory = containerFile.toAbsolutePath().normalize().getParent();
		if (parentDirectory == null) {
			throw new IOException("Encrypted tenant data target has no parent directory");
		}
		Files.createDirectories(parentDirectory);
		Files.deleteIfExists(containerFile);
		byte[] contentNonce = randomBytes(NONCE_LENGTH_BYTES);
		try {
			Cipher cipher = initializeContentCipher(Cipher.ENCRYPT_MODE, session.dataKey(), contentNonce);
			try (OutputStream fileOutput = new BufferedOutputStream(Files.newOutputStream(containerFile, StandardOpenOption.CREATE_NEW));
					DataOutputStream headerOutput = new DataOutputStream(fileOutput)) {
				writeHeader(headerOutput, session.profile().encryptionEnvelope(), contentNonce);
				headerOutput.flush();
				try (CipherOutputStream cipherOutput = new CipherOutputStream(fileOutput, cipher)) {
					contentWriter.write(cipherOutput);
				}
			}
			forceFile(containerFile);
		} catch (GeneralSecurityException | IOException | RuntimeException e) {
			Files.deleteIfExists(containerFile);
			if (e instanceof IOException ioException) {
				throw ioException;
			}
			throw new IOException("Could not encrypt tenant data", e);
		} finally {
			Arrays.fill(contentNonce, (byte) 0);
		}
	}

	private void decryptContent(Path containerFile, TenantDataKey dataKey, OutputStream outputStream) throws IOException {
		byte[] encryptedBuffer = new byte[BUFFER_SIZE];
		try (DataInputStream inputStream = openContainerInput(containerFile)) {
			ContainerHeader header = readHeader(inputStream);
			Cipher cipher = initializeContentCipher(Cipher.DECRYPT_MODE, dataKey, header.contentNonce());
			int read;
			while ((read = inputStream.read(encryptedBuffer)) >= 0) {
				if (read == 0) {
					continue;
				}
				writePlaintext(cipher.update(encryptedBuffer, 0, read), outputStream);
			}
			writePlaintext(cipher.doFinal(), outputStream);
			outputStream.flush();
		} catch (AEADBadTagException e) {
			throw new IOException("Encrypted tenant data authentication failed", e);
		} catch (GeneralSecurityException e) {
			throw new IOException("Could not decrypt tenant data", e);
		} finally {
			Arrays.fill(encryptedBuffer, (byte) 0);
		}
	}

	private void updateContainerEnvelope(Path containerFile, TenantKeyEnvelope envelope, TenantDataKey dataKey) throws IOException {
		Path tempFile = containerFile.resolveSibling(fileName(containerFile) + ".rewrap.tmp");
		Files.deleteIfExists(tempFile);
		try (DataInputStream inputStream = openContainerInput(containerFile);
				OutputStream fileOutput = new BufferedOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.CREATE_NEW));
				DataOutputStream outputStream = new DataOutputStream(fileOutput)) {
			ContainerHeader oldHeader = readHeader(inputStream);
			writeHeader(outputStream, envelope, oldHeader.contentNonce());
			inputStream.transferTo(outputStream);
		}
		try {
			forceFile(tempFile);
			decryptContent(tempFile, dataKey, OutputStream.nullOutputStream());
			moveAtomically(tempFile, containerFile);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	private DataInputStream openContainerInput(Path containerFile) throws IOException {
		if (Files.size(containerFile) < HEADER_LENGTH + GCM_TAG_LENGTH_BITS / Byte.SIZE) {
			throw new IOException("Encrypted tenant data is incomplete");
		}
		return new DataInputStream(new BufferedInputStream(Files.newInputStream(containerFile)));
	}

	private ContainerHeader readHeader(DataInputStream inputStream) throws IOException {
		byte[] magic = inputStream.readNBytes(CONTAINER_MAGIC.length);
		if (!Arrays.equals(CONTAINER_MAGIC, magic)) {
			throw new IOException("Unsupported encrypted tenant data format");
		}
		int version = inputStream.readInt();
		if (version != CONTAINER_VERSION) {
			throw new IOException("Unsupported encrypted tenant data version: " + version);
		}
		int iterations = inputStream.readInt();
		byte[] salt = inputStream.readNBytes(SALT_LENGTH_BYTES);
		byte[] wrappingNonce = inputStream.readNBytes(NONCE_LENGTH_BYTES);
		byte[] wrappedKey = inputStream.readNBytes(WRAPPED_KEY_LENGTH_BYTES);
		byte[] contentNonce = inputStream.readNBytes(NONCE_LENGTH_BYTES);
		validateEnvelope(iterations, salt, wrappingNonce, wrappedKey);
		if (contentNonce.length != NONCE_LENGTH_BYTES) {
			throw new IOException("Encrypted tenant data header is incomplete");
		}
		return new ContainerHeader(new TenantKeyEnvelope(iterations, encode(salt), encode(wrappingNonce), encode(wrappedKey)), contentNonce);
	}

	private void writeHeader(DataOutputStream outputStream, TenantKeyEnvelope envelope, byte[] contentNonce) throws IOException {
		byte[] salt = decode(envelope.salt());
		byte[] wrappingNonce = decode(envelope.nonce());
		byte[] wrappedKey = decode(envelope.wrappedKey());
		validateEnvelope(envelope.iterations(), salt, wrappingNonce, wrappedKey);
		if (contentNonce.length != NONCE_LENGTH_BYTES) {
			throw new IOException("Invalid tenant data nonce");
		}
		outputStream.write(CONTAINER_MAGIC);
		outputStream.writeInt(CONTAINER_VERSION);
		outputStream.writeInt(envelope.iterations());
		outputStream.write(salt);
		outputStream.write(wrappingNonce);
		outputStream.write(wrappedKey);
		outputStream.write(contentNonce);
	}

	private void validateEnvelope(int iterations, byte[] salt, byte[] nonce, byte[] wrappedKey) {
		if (iterations <= 0 || salt.length != SALT_LENGTH_BYTES || nonce.length != NONCE_LENGTH_BYTES
				|| wrappedKey.length != WRAPPED_KEY_LENGTH_BYTES) {
			throw new IllegalArgumentException("Invalid tenant encryption key envelope");
		}
	}

	private Cipher initializeContentCipher(int mode, TenantDataKey dataKey, byte[] nonce) throws GeneralSecurityException {
		return initializeCipher(mode, dataKey.toSecretKey(), nonce, CONTENT_AAD);
	}

	private Cipher initializeCipher(int mode, SecretKey key, byte[] nonce, byte[] aad) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
		cipher.updateAAD(aad);
		return cipher;
	}

	private SecretKey deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
		PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, DATA_KEY_LENGTH_BYTES * Byte.SIZE);
		try {
			byte[] derivedKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
			try {
				return new SecretKeySpec(derivedKey, "AES");
			} finally {
				Arrays.fill(derivedKey, (byte) 0);
			}
		} finally {
			keySpec.clearPassword();
		}
	}

	private void writePlaintext(byte[] plaintext, OutputStream outputStream) throws IOException {
		if (plaintext == null || plaintext.length == 0) {
			return;
		}
		try {
			outputStream.write(plaintext);
		} finally {
			Arrays.fill(plaintext, (byte) 0);
		}
	}

	private byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		secureRandom.nextBytes(bytes);
		return bytes;
	}

	private static final class SensitiveByteArrays implements AutoCloseable {

		private final List<byte[]> values = new ArrayList<>();

		private byte[] add(byte[] value) {
			values.add(value);
			return value;
		}

		@Override
		public void close() {
			for (byte[] value : values) {
				Arrays.fill(value, (byte) 0);
			}
		}
	}

	private static String fileName(Path file) {
		return Objects.requireNonNull(file.getFileName(), "file name").toString();
	}

	private String encode(byte[] value) {
		return Base64.getEncoder().encodeToString(value);
	}

	private byte[] decode(String value) {
		return Base64.getDecoder().decode(value);
	}

	private void forceFile(Path file) throws IOException {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
	}

	private record ContainerHeader(TenantKeyEnvelope envelope, byte[] contentNonce) {

		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof ContainerHeader header
					&& Objects.equals(envelope, header.envelope) && Arrays.equals(contentNonce, header.contentNonce);
		}

		@Override
		public int hashCode() {
			return 31 * Objects.hashCode(envelope) + Arrays.hashCode(contentNonce);
		}

		@Override
		public String toString() {
			return "ContainerHeader[envelope=" + envelope + ", contentNonce=" + Arrays.toString(contentNonce) + "]";
		}
	}

	@FunctionalInterface
	interface ContentWriter {

		void write(OutputStream outputStream) throws IOException;
	}
}
