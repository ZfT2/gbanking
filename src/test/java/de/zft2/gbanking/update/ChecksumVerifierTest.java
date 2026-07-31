package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumVerifierTest {

	@TempDir
	Path tempDir;

	@Test
	void verify_shouldAcceptMatchingChecksum() throws Exception {
		Path archive = tempDir.resolve("gbanking-0.5.2-windows.zip");
		Files.writeString(archive, "archive", StandardCharsets.UTF_8);
		Path checksums = tempDir.resolve("SHA256SUMS");
		String archiveFileName = fileName(archive);
		Files.writeString(checksums, sha256(archive) + "  " + archiveFileName, StandardCharsets.UTF_8);

		assertDoesNotThrow(() -> new ChecksumVerifier().verify(checksums, archive, archiveFileName));
	}

	@Test
	void verify_shouldRejectMismatchingChecksum() throws Exception {
		Path archive = tempDir.resolve("gbanking-0.5.2-windows.zip");
		Files.writeString(archive, "archive", StandardCharsets.UTF_8);
		Path checksums = tempDir.resolve("SHA256SUMS");
		String archiveFileName = fileName(archive);
		Files.writeString(checksums, "0000000000000000000000000000000000000000000000000000000000000000  " + archiveFileName,
				StandardCharsets.UTF_8);

		assertThrows(UpdateException.class, () -> new ChecksumVerifier().verify(checksums, archive, archiveFileName));
	}

	private static String fileName(Path file) {
		return Objects.requireNonNull(file.getFileName(), "file name").toString();
	}

	private String sha256(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
	}
}
