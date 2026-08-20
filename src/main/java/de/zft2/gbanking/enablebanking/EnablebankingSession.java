package de.zft2.gbanking.enablebanking;

import java.time.OffsetDateTime;
import java.util.List;

public record EnablebankingSession(String sessionId, String status, OffsetDateTime validUntil,
		List<EnablebankingRemoteAccount> accounts) {

	public EnablebankingSession {
		accounts = List.copyOf(accounts);
	}

	@Override
	public List<EnablebankingRemoteAccount> accounts() {
		return List.copyOf(accounts);
	}

	public boolean isAuthorized() {
		return "AUTHORIZED".equalsIgnoreCase(status);
	}
}
