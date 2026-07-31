package de.zft2.gbanking.update;

import java.net.URI;
import java.util.Objects;

public record ReleaseAsset(String name, URI downloadUrl, long size) {

	public ReleaseAsset {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(downloadUrl, "downloadUrl must not be null");
		if (!isPlainFileName(name)) {
			throw new IllegalArgumentException("Release asset name must be a plain file name: " + name);
		}
	}

	static boolean isPlainFileName(String name) {
		return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\") && !name.contains("..");
	}
}
