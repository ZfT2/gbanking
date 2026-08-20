package de.zft2.gbanking.enablebanking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.util.SimpleJson;

class EnablebankingCryptoTest {

	@Test
	void jwtShouldContainRequiredClaimsAndValidSignature() throws Exception {
		KeyPair keyPair = keyPair();
		Clock clock = Clock.fixed(Instant.parse("2026-08-20T10:15:30Z"), ZoneOffset.UTC);
		String token = new EnablebankingJwtSigner("application-id", keyPair.getPrivate(), clock).createToken();
		String[] parts = token.split("\\.");

		Map<?, ?> header = (Map<?, ?>) SimpleJson.parse(decode(parts[0]));
		Map<?, ?> claims = (Map<?, ?>) SimpleJson.parse(decode(parts[1]));
		assertEquals("RS256", header.get("alg"));
		assertEquals("application-id", header.get("kid"));
		assertEquals("enablebanking.com", claims.get("iss"));
		assertEquals("api.enablebanking.com", claims.get("aud"));
		assertTrue(verify(keyPair, parts));
	}

	@Test
	void callbackMaterialShouldBeGeneratedAndReused() throws Exception {
		Psd2ClientConfiguration configuration = new Psd2ClientConfiguration();
		EnablebankingCallbackMaterialService service = new EnablebankingCallbackMaterialService();

		var first = service.ensureMaterial(configuration);
		var second = service.ensureMaterial(configuration);

		assertNotNull(configuration.getCallbackPrivateKeyPkcs8());
		assertNotNull(configuration.getCallbackCertificate());
		assertEquals(first.certificate(), second.certificate());
		assertEquals("localhost", first.certificate().getSubjectAlternativeNames().iterator().next().get(1));
		assertTrue(first.certificate().getKeyUsage()[0]);
		assertTrue(first.certificate().getKeyUsage()[2]);
		assertTrue(first.certificate().getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"));
	}

	@Test
	void shouldReadPkcs8PemWithoutExternalProvider() throws Exception {
		byte[] encoded = keyPair().getPrivate().getEncoded();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
				+ "\n-----END PRIVATE KEY-----";

		assertArrayEquals(encoded, EnablebankingPrivateKeyReader.readPkcs8(pem));
	}

	@Test
	void shouldReadPkcs1PemWithoutExternalProvider() throws Exception {
		RSAPrivateCrtKey key = (RSAPrivateCrtKey) keyPair().getPrivate();
		byte[] encoded = EnablebankingDer.sequence(EnablebankingDer.integer(BigInteger.ZERO),
				EnablebankingDer.integer(key.getModulus()), EnablebankingDer.integer(key.getPublicExponent()),
				EnablebankingDer.integer(key.getPrivateExponent()), EnablebankingDer.integer(key.getPrimeP()),
				EnablebankingDer.integer(key.getPrimeQ()), EnablebankingDer.integer(key.getPrimeExponentP()),
				EnablebankingDer.integer(key.getPrimeExponentQ()), EnablebankingDer.integer(key.getCrtCoefficient()));
		String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
				+ "\n-----END RSA PRIVATE KEY-----";

		assertArrayEquals(key.getEncoded(), EnablebankingPrivateKeyReader.readPkcs8(pem));
	}

	private KeyPair keyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private String decode(String value) {
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	private boolean verify(KeyPair keyPair, String[] parts) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(keyPair.getPublic());
		signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		return signature.verify(Base64.getUrlDecoder().decode(parts[2]));
	}
}
