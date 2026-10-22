package de.zft2.gbanking.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Central helper for SHA-256 digests.
 *
 * <p>
 * All digest calculations of the application are done here so that the used encoding (hex or
 * base64) and the handling of the algorithm lookup are consistent.
 * </p>
 */
public final class Hashes {

	private static final int BUFFER_SIZE = 8192;
	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private Hashes() {
	}

	/**
	 * Creates a new SHA-256 digest that can be updated incrementally.
	 *
	 * @return a fresh message digest
	 * @throws IllegalStateException if SHA-256 is not available in the runtime
	 */
	public static MessageDigest newSha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	/**
	 * Calculates the SHA-256 digest of the given bytes and encodes it as lower case hex string.
	 *
	 * @param content the content to hash
	 * @return the hex encoded digest
	 */
	public static String sha256Hex(byte[] content) {
		return HEX_FORMAT.formatHex(newSha256Digest().digest(content));
	}

	/**
	 * Encodes a digest as lower case hex string.
	 *
	 * @param digest the raw digest bytes
	 * @return the hex encoded digest
	 */
	public static String toHex(byte[] digest) {
		return HEX_FORMAT.formatHex(digest);
	}

	/**
	 * Calculates the SHA-256 digest of the UTF-8 representation of the given text and encodes it as
	 * lower case hex string.
	 *
	 * @param content the text to hash
	 * @return the hex encoded digest
	 */
	public static String sha256Hex(String content) {
		return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Calculates the SHA-256 digest of the given bytes and encodes it as base64 string.
	 *
	 * @param content the content to hash
	 * @return the base64 encoded digest
	 */
	public static String sha256Base64(byte[] content) {
		return Base64.getEncoder().encodeToString(newSha256Digest().digest(content));
	}

	/**
	 * Calculates the SHA-256 digest of the UTF-8 representation of the given text and encodes it as
	 * base64url string without padding.
	 *
	 * @param content the text to hash, {@code null} is treated as empty text
	 * @return the base64url encoded digest
	 */
	public static String sha256Base64Url(String content) {
		byte[] bytes = Objects.toString(content, "").getBytes(StandardCharsets.UTF_8);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(newSha256Digest().digest(bytes));
	}

	/**
	 * Calculates the SHA-256 digest of the complete file content and encodes it as lower case hex
	 * string.
	 *
	 * @param file the file to hash
	 * @return the hex encoded digest
	 * @throws IOException if the file cannot be read
	 */
	public static String sha256HexOfFile(Path file) throws IOException {
		MessageDigest digest = newSha256Digest();
		try (InputStream inputStream = Files.newInputStream(file)) {
			byte[] buffer = new byte[BUFFER_SIZE];
			int read;
			while ((read = inputStream.read(buffer)) >= 0) {
				digest.update(buffer, 0, read);
			}
		}
		return HEX_FORMAT.formatHex(digest.digest());
	}

	/**
	 * Calculates the SHA-256 digest of the first bytes of the file and encodes it as lower case hex
	 * string. The digest is used as cheap content marker for change detection.
	 *
	 * @param file      the file to hash
	 * @param byteCount the number of bytes to read
	 * @return the hex encoded digest
	 * @throws IOException if the file cannot be read
	 */
	public static String sha256HexOfFilePrefix(Path file, int byteCount) throws IOException {
		MessageDigest digest = newSha256Digest();
		byte[] buffer = new byte[byteCount];
		try (InputStream inputStream = Files.newInputStream(file)) {
			int bytesRead = inputStream.readNBytes(buffer, 0, buffer.length);
			digest.update(buffer, 0, bytesRead);
		}
		return HEX_FORMAT.formatHex(digest.digest());
	}
}
