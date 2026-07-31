package de.zft2.gbanking.tenant;

import java.util.Objects;

public final class TenantSession implements AutoCloseable {

	private TenantProfile profile;
	private final TenantPaths paths;
	private final TenantDataKey dataKey;

	TenantSession(TenantProfile profile, TenantPaths paths, TenantDataKey dataKey) {
		this.profile = Objects.requireNonNull(profile, "profile");
		this.paths = Objects.requireNonNull(paths, "paths");
		this.dataKey = Objects.requireNonNull(dataKey, "dataKey");
	}

	public TenantProfile profile() {
		return profile;
	}

	public TenantPaths paths() {
		return paths;
	}

	public void updateProfile(TenantProfile updatedProfile) {
		Objects.requireNonNull(updatedProfile, "updatedProfile");
		if (!profile.id().equals(updatedProfile.id())) {
			throw new IllegalArgumentException("The updated profile belongs to another tenant");
		}
		profile = updatedProfile;
	}

	TenantDataKey dataKey() {
		return dataKey;
	}

	@Override
	public void close() {
		dataKey.close();
	}
}
