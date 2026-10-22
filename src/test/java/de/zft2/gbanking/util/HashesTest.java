package de.zft2.gbanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashesTest {

	@Test
	void sha256Hex_shouldMatchKnownDigest() {
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashes.sha256Hex(""));
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
				Hashes.sha256Hex("abc"));
	}

	@Test
	void sha256Hex_shouldReturnSameDigestForSameBytes() {
		byte[] content = "Gewinn und Verlust".getBytes(StandardCharsets.UTF_8);
		assertEquals(Hashes.sha256Hex(content), Hashes.sha256Hex(content));
	}

	@Test
	void sha256Base64_shouldReturnStandardBase64Digest() {
		assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", Hashes.sha256Base64("abc".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void sha256Base64Url_shouldReturnUrlSafeDigestWithoutPadding() {
		String digest = Hashes.sha256Base64Url("abc");
		assertTrue(digest.endsWith("=") == false);
		assertTrue(digest.contains("+") == false);
		assertTrue(digest.contains("/") == false);
		assertEquals(43, digest.length());
	}

	@Test
	void sha256Base64Url_shouldTreatNullAsEmptyText() {
		assertEquals(Hashes.sha256Base64Url(""), Hashes.sha256Base64Url(null));
	}

	@Test
	void sha256HexOfFile_shouldMatchDigestOfFileContent(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("content.txt");
		Files.writeString(file, "abc", StandardCharsets.UTF_8);
		assertEquals(Hashes.sha256Hex("abc"), Hashes.sha256HexOfFile(file));
	}

	@Test
	void sha256HexOfFilePrefix_shouldOnlyHashTheGivenPrefix(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("content.txt");
		Files.writeString(file, "abcdef", StandardCharsets.UTF_8);
		assertEquals(Hashes.sha256Hex("abc"), Hashes.sha256HexOfFilePrefix(file, 3));
	}

	@Test
	void newSha256Digest_shouldReturnUsableDigest() {
		MessageDigest digest = Hashes.newSha256Digest();
		digest.update("abc".getBytes(StandardCharsets.UTF_8));
		assertEquals(Hashes.sha256Hex("abc"), Hashes.toHex(digest.digest()));
	}
}
