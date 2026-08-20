package de.zft2.gbanking.enablebanking;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class EnablebankingPrivateKeyReader {

	private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
	private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";
	private static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
	private static final String PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";
	private static final String RSA_ENCRYPTION_OID = "1.2.840.113549.1.1.1";

	private EnablebankingPrivateKeyReader() {
	}

	public static byte[] readPkcs8(String pem) {
		if (pem == null || pem.isBlank()) {
			throw new EnablebankingException("Der private Enablebanking-Schlüssel fehlt.");
		}
		try {
			String trimmed = pem.trim();
			byte[] encoded = decodePem(trimmed);
			if (trimmed.startsWith(PKCS1_HEADER)) {
				encoded = wrapPkcs1(encoded);
			}
			return decodePkcs8(encoded).getEncoded();
		} catch (IllegalArgumentException exception) {
			throw new EnablebankingException("Der private Enablebanking-Schlüssel konnte nicht gelesen werden.", exception);
		}
	}

	public static PrivateKey decodePkcs8(byte[] encoded) {
		if (encoded == null || encoded.length == 0) {
			throw new EnablebankingException("Der private Enablebanking-Schlüssel fehlt.");
		}
		try {
			PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
			validate(privateKey);
			return privateKey;
		} catch (GeneralSecurityException | RuntimeException exception) {
			throw new EnablebankingException("Der gespeicherte Enablebanking-Schlüssel ist ungültig.", exception);
		}
	}

	private static byte[] decodePem(String pem) {
		String header;
		String footer;
		if (pem.startsWith(PKCS8_HEADER) && pem.endsWith(PKCS8_FOOTER)) {
			header = PKCS8_HEADER;
			footer = PKCS8_FOOTER;
		} else if (pem.startsWith(PKCS1_HEADER) && pem.endsWith(PKCS1_FOOTER)) {
			header = PKCS1_HEADER;
			footer = PKCS1_FOOTER;
		} else {
			throw new EnablebankingException("Nicht unterstütztes Format des privaten Enablebanking-Schlüssels.");
		}
		String base64 = pem.substring(header.length(), pem.length() - footer.length()).replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64);
	}

	private static byte[] wrapPkcs1(byte[] encoded) {
		return EnablebankingDer.sequence(EnablebankingDer.integer(BigInteger.ZERO),
				EnablebankingDer.sequence(EnablebankingDer.oid(RSA_ENCRYPTION_OID), EnablebankingDer.nullValue()),
				EnablebankingDer.octetString(encoded));
	}

	private static void validate(PrivateKey privateKey) {
		if (privateKey == null || !"RSA".equalsIgnoreCase(privateKey.getAlgorithm()) || privateKey.getEncoded() == null) {
			throw new EnablebankingException("Enablebanking benötigt einen privaten RSA-Schlüssel.");
		}
	}
}
