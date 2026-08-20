package de.zft2.gbanking.enablebanking;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;

public class EnablebankingCallbackMaterialService {

	private static final SecureRandom RANDOM = new SecureRandom();

	public CallbackMaterial ensureMaterial(Psd2ClientConfiguration configuration) {
		byte[] privateKeyBytes = configuration.getCallbackPrivateKeyPkcs8();
		byte[] certificateBytes = configuration.getCallbackCertificate();
		if (privateKeyBytes != null && certificateBytes != null) {
			return decode(privateKeyBytes, certificateBytes);
		}
		CallbackMaterial material = generate();
		configuration.setCallbackPrivateKeyPkcs8(material.privateKey().getEncoded());
		try {
			configuration.setCallbackCertificate(material.certificate().getEncoded());
		} catch (GeneralSecurityException exception) {
			throw new EnablebankingException("Das lokale Callback-Zertifikat konnte nicht gespeichert werden.", exception);
		}
		return material;
	}

	private CallbackMaterial generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048, RANDOM);
			KeyPair keyPair = generator.generateKeyPair();
			Instant now = Instant.now();
			X509Certificate certificate = EnablebankingCertificateGenerator.generate(keyPair,
					now.minus(1, ChronoUnit.DAYS), now.plus(10 * 365L, ChronoUnit.DAYS), RANDOM);
			certificate.checkValidity();
			certificate.verify(keyPair.getPublic());
			return new CallbackMaterial(keyPair.getPrivate(), certificate);
		} catch (Exception exception) {
			throw new EnablebankingException("Das lokale HTTPS-Zertifikat konnte nicht erzeugt werden.", exception);
		}
	}

	private CallbackMaterial decode(byte[] privateKeyBytes, byte[] certificateBytes) {
		try {
			PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			X509Certificate certificate = (X509Certificate) factory
					.generateCertificate(new java.io.ByteArrayInputStream(certificateBytes));
			certificate.checkValidity();
			return new CallbackMaterial(privateKey, certificate);
		} catch (GeneralSecurityException exception) {
			throw new EnablebankingException("Das gespeicherte lokale Callback-Zertifikat ist ungültig.", exception);
		}
	}

	public static final class CallbackMaterial {

		private final byte[] privateKeyPkcs8;
		private final byte[] certificateEncoded;

		private CallbackMaterial(PrivateKey privateKey, X509Certificate certificate) {
			privateKeyPkcs8 = privateKey.getEncoded();
			try {
				certificateEncoded = certificate.getEncoded();
			} catch (GeneralSecurityException exception) {
				throw new EnablebankingException("Das lokale Callback-Zertifikat konnte nicht gelesen werden.", exception);
			}
		}

		public PrivateKey privateKey() {
			try {
				return KeyFactory.getInstance("RSA").generatePrivate(
						new PKCS8EncodedKeySpec(Arrays.copyOf(privateKeyPkcs8, privateKeyPkcs8.length)));
			} catch (GeneralSecurityException exception) {
				throw new EnablebankingException("Der lokale Callback-Schlüssel ist ungültig.", exception);
			}
		}

		public X509Certificate certificate() {
			try {
				CertificateFactory factory = CertificateFactory.getInstance("X.509");
				return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
						Arrays.copyOf(certificateEncoded, certificateEncoded.length)));
			} catch (GeneralSecurityException exception) {
				throw new EnablebankingException("Das lokale Callback-Zertifikat ist ungültig.", exception);
			}
		}
	}
}
