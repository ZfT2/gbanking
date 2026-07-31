package de.zft2.gbanking.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ChecksumVerifier {

	public void verify(Path checksumsFile, Path file, String expectedFileName) throws IOException, UpdateException {
		Map<String, String> checksums = readChecksums(checksumsFile);
		String expectedChecksum = checksums.get(expectedFileName);
		if (expectedChecksum == null) {
			throw new UpdateException("Missing SHA-256 checksum for " + expectedFileName);
		}

		String actualChecksum = sha256(file);
		if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
			throw new UpdateException("SHA-256 checksum mismatch for " + expectedFileName);
		}
	}

	Map<String, String> readChecksums(Path checksumsFile) throws IOException {
		Map<String, String> checksums = new LinkedHashMap<>();
		for (String line : Files.readAllLines(checksumsFile, StandardCharsets.UTF_8)) {
			String trimmedLine = line.trim();
			int separatorIndex = firstWhitespace(trimmedLine);
			if (trimmedLine.isBlank() || trimmedLine.startsWith("#") || separatorIndex <= 0) {
				continue;
			}

			String checksum = trimmedLine.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
			String fileName = trimmedLine.substring(separatorIndex).trim();
			if (fileName.startsWith("*")) {
				fileName = fileName.substring(1);
			}
			if (checksum.matches("[0-9a-f]{64}") && ReleaseAsset.isPlainFileName(fileName)) {
				checksums.put(fileName, checksum);
			}
		}
		return checksums;
	}

	private String sha256(Path file) throws IOException, UpdateException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream inputStream = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer)) >= 0) {
					digest.update(buffer, 0, read);
				}
			}
			return toHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new UpdateException("SHA-256 is not available", e);
		}
	}

	private int firstWhitespace(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private String toHex(byte[] bytes) {
		StringBuilder hex = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			hex.append(String.format(Locale.ROOT, "%02x", value));
		}
		return hex.toString();
	}
}
