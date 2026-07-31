package de.zft2.gbanking.tenant;

public record TenantProfile(String id, String username, int encryptionIterations, String encryptionSalt, String wrappedKeyNonce,
		String wrappedDataKey) {

	TenantKeyEnvelope encryptionEnvelope() {
		return new TenantKeyEnvelope(encryptionIterations, encryptionSalt, wrappedKeyNonce, wrappedDataKey);
	}
}
