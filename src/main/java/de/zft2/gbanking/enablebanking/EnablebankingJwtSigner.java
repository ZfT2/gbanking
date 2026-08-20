package de.zft2.gbanking.enablebanking;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import de.zft2.gbanking.util.SimpleJson;

final class EnablebankingJwtSigner {

	private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
	private static final long TOKEN_VALIDITY_SECONDS = 3_600;

	private final String applicationId;
	private final PrivateKey privateKey;
	private final Clock clock;

	EnablebankingJwtSigner(String applicationId, PrivateKey privateKey) {
		this(applicationId, privateKey, Clock.systemUTC());
	}

	EnablebankingJwtSigner(String applicationId, PrivateKey privateKey, Clock clock) {
		if (applicationId == null || applicationId.isBlank()) {
			throw new EnablebankingException("Die Enablebanking Application-ID fehlt.");
		}
		this.applicationId = applicationId.trim();
		this.privateKey = privateKey;
		this.clock = clock;
	}

	String createToken() {
		Instant now = clock.instant();
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("typ", "JWT");
		header.put("alg", "RS256");
		header.put("kid", applicationId);

		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", "enablebanking.com");
		claims.put("aud", "api.enablebanking.com");
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(TOKEN_VALIDITY_SECONDS).getEpochSecond());

		String signingInput = encode(SimpleJson.write(header)) + "." + encode(SimpleJson.write(claims));
		return signingInput + "." + sign(signingInput);
	}

	private String sign(String signingInput) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
			return BASE64_URL.encodeToString(signature.sign());
		} catch (GeneralSecurityException exception) {
			throw new EnablebankingException("Das Enablebanking-Token konnte nicht signiert werden.", exception);
		}
	}

	private String encode(String value) {
		return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
