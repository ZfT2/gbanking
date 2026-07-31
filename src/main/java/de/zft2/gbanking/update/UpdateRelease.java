package de.zft2.gbanking.update;

import java.net.URI;
import java.util.Objects;

public record UpdateRelease(String version, String tagName, URI htmlUrl, String body, ReleaseAsset applicationAsset, ReleaseAsset checksumsAsset,
		ReleaseAsset signatureAsset) {

	public UpdateRelease {
		Objects.requireNonNull(version, "version must not be null");
		Objects.requireNonNull(tagName, "tagName must not be null");
		Objects.requireNonNull(htmlUrl, "htmlUrl must not be null");
		Objects.requireNonNull(applicationAsset, "applicationAsset must not be null");
		Objects.requireNonNull(checksumsAsset, "checksumsAsset must not be null");
		Objects.requireNonNull(signatureAsset, "signatureAsset must not be null");
	}

	public boolean isNewerThan(String currentVersion) {
		return VersionComparator.isNewer(version, currentVersion);
	}
}
