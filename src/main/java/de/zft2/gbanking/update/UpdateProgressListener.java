package de.zft2.gbanking.update;

@FunctionalInterface
public interface UpdateProgressListener {

	void onProgress(String message);

	default void onDownloadProgress(long downloadedBytes, long totalBytes) {
		// Optional callback for callers that display byte-level download progress.
	}
}
