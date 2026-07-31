package de.zft2.gbanking.tenant;

import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class TenantDataKey implements AutoCloseable {

	private byte[] keyBytes;

	TenantDataKey(byte[] keyBytes) {
		this.keyBytes = Arrays.copyOf(keyBytes, keyBytes.length);
	}

	synchronized SecretKey toSecretKey() {
		if (keyBytes == null) {
			throw new IllegalStateException("Tenant data key has already been cleared");
		}
		return new SecretKeySpec(keyBytes, "AES");
	}

	synchronized byte[] copyBytes() {
		if (keyBytes == null) {
			throw new IllegalStateException("Tenant data key has already been cleared");
		}
		return Arrays.copyOf(keyBytes, keyBytes.length);
	}

	@Override
	public synchronized void close() {
		if (keyBytes != null) {
			Arrays.fill(keyBytes, (byte) 0);
			keyBytes = null;
		}
	}
}
