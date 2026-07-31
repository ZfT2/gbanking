package de.zft2.gbanking.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

public final class UpdateSignatureVerifier {

	private static final String DEFAULT_PUBLIC_KEY_RESOURCE = "/update/update-signing-public-key.pem";
	private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

	private final PublicKey publicKey;

	public UpdateSignatureVerifier(PublicKey publicKey) {
		this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
	}

	public static UpdateSignatureVerifier loadDefault() throws IOException, GeneralSecurityException {
		try (InputStream inputStream = UpdateSignatureVerifier.class.getResourceAsStream(DEFAULT_PUBLIC_KEY_RESOURCE)) {
			if (inputStream == null) {
				throw new IOException("Missing update signing public key resource: " + DEFAULT_PUBLIC_KEY_RESOURCE);
			}
			return new UpdateSignatureVerifier(loadPublicKey(inputStream));
		}
	}

	public static PublicKey loadPublicKey(InputStream inputStream) throws IOException, GeneralSecurityException {
		String pem = new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
		String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s", "");
		byte[] encodedKey = Base64.getDecoder().decode(base64);
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
	}

	public void verifyOrThrow(Path dataFile, Path signatureFile) throws IOException, GeneralSecurityException, UpdateException {
		if (!verify(dataFile, signatureFile)) {
			throw new UpdateException("Update signature verification failed");
		}
	}

	public boolean verify(Path dataFile, Path signatureFile) throws IOException, GeneralSecurityException {
		Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
		signature.initVerify(publicKey);

		try (InputStream inputStream = Files.newInputStream(dataFile)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = inputStream.read(buffer)) >= 0) {
				signature.update(buffer, 0, read);
			}
		}
		return signature.verify(Files.readAllBytes(signatureFile));
	}
}
