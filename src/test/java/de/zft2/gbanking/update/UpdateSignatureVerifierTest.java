package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateSignatureVerifierTest {

	@TempDir
	Path tempDir;

	@Test
	void verify_shouldAcceptValidSignatureAndRejectTampering() throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		Path checksums = tempDir.resolve("SHA256SUMS");
		Files.writeString(checksums, "content", StandardCharsets.UTF_8);
		Path signatureFile = tempDir.resolve("SHA256SUMS.sig");
		Files.write(signatureFile, sign(keyPair, checksums));

		UpdateSignatureVerifier verifier = new UpdateSignatureVerifier(keyPair.getPublic());

		assertTrue(verifier.verify(checksums, signatureFile));

		Files.writeString(checksums, "tampered", StandardCharsets.UTF_8);
		assertFalse(verifier.verify(checksums, signatureFile));
	}

	private byte[] sign(KeyPair keyPair, Path dataFile) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(keyPair.getPrivate());
		signature.update(Files.readAllBytes(dataFile));
		return signature.sign();
	}
}
