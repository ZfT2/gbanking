package de.zft2.gbanking.demo;

import java.util.Arrays;
import java.util.Optional;

import de.zft2.gbanking.tenant.TenantProfile;
import de.zft2.gbanking.tenant.TenantSession;
import de.zft2.gbanking.tenant.TenantStore;

public final class DemoTenantManager {

	private final TenantStore tenantStore;

	public DemoTenantManager(TenantStore tenantStore) {
		this.tenantStore = tenantStore;
	}

	public boolean demoTenantExists() {
		return tenantStore.findByUsername(TenantStore.DEMO_USERNAME).isPresent();
	}

	public TenantSession createFreshDemoSession() {
		char[] password = createPassword();
		try {
			removeExistingDemoTenant(password);
			TenantProfile tenant = tenantStore.createDemoTenant(password);
			return tenantStore.authenticateSession(tenant.id(), password)
					.orElseThrow(() -> new IllegalStateException("Created demo tenant could not be authenticated"));
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	public void removeDemoTenant() {
		char[] password = createPassword();
		try {
			removeExistingDemoTenant(password);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private void removeExistingDemoTenant(char[] password) {
		Optional<TenantProfile> existingTenant = tenantStore.findByUsername(TenantStore.DEMO_USERNAME);
		if (existingTenant.isPresent()) {
			tenantStore.deleteTenantAndData(existingTenant.get().id(), password);
		}
	}

	private static char[] createPassword() {
		return TenantStore.DEMO_USERNAME.toCharArray();
	}
}
