package de.zft2.gbanking.tenant;

record TenantKeyEnvelope(int iterations, String salt, String nonce, String wrappedKey) {
}
