package de.zft2.gbanking.enablebanking;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.security.auth.x500.X500Principal;

final class EnablebankingCertificateGenerator {

	private static final String SIGNATURE_ALGORITHM_OID = "1.2.840.113549.1.1.11";
	private static final String BASIC_CONSTRAINTS_OID = "2.5.29.19";
	private static final String KEY_USAGE_OID = "2.5.29.15";
	private static final String EXTENDED_KEY_USAGE_OID = "2.5.29.37";
	private static final String SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1";
	private static final String SUBJECT_ALTERNATIVE_NAME_OID = "2.5.29.17";
	private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'", Locale.ROOT)
			.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter GENERALIZED_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'", Locale.ROOT)
			.withZone(ZoneOffset.UTC);

	private EnablebankingCertificateGenerator() {
	}

	static X509Certificate generate(KeyPair keyPair, Instant validFrom, Instant validUntil, SecureRandom random)
			throws GeneralSecurityException {
		byte[] signatureAlgorithm = EnablebankingDer.sequence(EnablebankingDer.oid(SIGNATURE_ALGORITHM_OID),
				EnablebankingDer.nullValue());
		byte[] subject = new X500Principal("CN=localhost,O=GBanking").getEncoded();
		byte[] certificateData = EnablebankingDer.sequence(
				EnablebankingDer.explicit(0, EnablebankingDer.integer(BigInteger.valueOf(2))),
				EnablebankingDer.integer(new BigInteger(159, random).setBit(158)), signatureAlgorithm, subject,
				EnablebankingDer.sequence(time(validFrom), time(validUntil)), subject, keyPair.getPublic().getEncoded(),
				extensions());

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(keyPair.getPrivate(), random);
		signature.update(certificateData);
		byte[] certificate = EnablebankingDer.sequence(certificateData, signatureAlgorithm,
				EnablebankingDer.bitString(signature.sign(), 0));
		return (X509Certificate) CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(certificate));
	}

	private static byte[] extensions() {
		byte[] basicConstraints = extension(BASIC_CONSTRAINTS_OID, true, EnablebankingDer.sequence());
		byte[] keyUsage = extension(KEY_USAGE_OID, true,
				EnablebankingDer.bitString(new byte[] { (byte) 0xa0 }, 5));
		byte[] extendedKeyUsage = extension(EXTENDED_KEY_USAGE_OID, false,
				EnablebankingDer.sequence(EnablebankingDer.oid(SERVER_AUTH_OID)));
		byte[] ipv6Loopback = new byte[16];
		ipv6Loopback[15] = 1;
		byte[] subjectAlternativeNames = extension(SUBJECT_ALTERNATIVE_NAME_OID, false,
				EnablebankingDer.sequence(
						EnablebankingDer.tagged(0x82, "localhost".getBytes(StandardCharsets.US_ASCII)),
						EnablebankingDer.tagged(0x87, new byte[] { 127, 0, 0, 1 }),
						EnablebankingDer.tagged(0x87, ipv6Loopback)));
		return EnablebankingDer.explicit(3, EnablebankingDer.sequence(basicConstraints, keyUsage,
				extendedKeyUsage, subjectAlternativeNames));
	}

	private static byte[] extension(String oid, boolean critical, byte[] value) {
		return critical
				? EnablebankingDer.sequence(EnablebankingDer.oid(oid), EnablebankingDer.bool(true),
						EnablebankingDer.octetString(value))
				: EnablebankingDer.sequence(EnablebankingDer.oid(oid), EnablebankingDer.octetString(value));
	}

	private static byte[] time(Instant value) {
		int year = value.atZone(ZoneOffset.UTC).getYear();
		return year >= 1950 && year < 2050
				? EnablebankingDer.ascii(0x17, UTC_TIME.format(value))
				: EnablebankingDer.ascii(0x18, GENERALIZED_TIME.format(value));
	}
}
