package de.zft2.gbanking.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.util.AppPaths;

public class UpdateManager {

	private static final int DOWNLOAD_BUFFER_SIZE = 8192;
	private static final int HTTP_STATUS_MULTIPLE_CHOICES = 300;
	private static final int HTTP_STATUS_OK = 200;

	private final GitHubReleaseClient releaseClient;
	private final HttpClient httpClient;
	private final OperatingSystem operatingSystem;
	private final Path installDirectory;
	private final ChecksumVerifier checksumVerifier;
	private final ZipPackageExtractor zipPackageExtractor;
	private final UpdateInstallerLauncher installerLauncher;
	private final UpdateSignatureVerifier signatureVerifier;

	public UpdateManager() {
		this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), OperatingSystem.current(),
				AppPaths.getApplicationBaseDirectory(), new ChecksumVerifier(), new ZipPackageExtractor(), new UpdateInstallerLauncher(), null);
	}

	UpdateManager(HttpClient httpClient, OperatingSystem operatingSystem, Path installDirectory,
			ChecksumVerifier checksumVerifier, ZipPackageExtractor zipPackageExtractor, UpdateInstallerLauncher installerLauncher,
			UpdateSignatureVerifier signatureVerifier) {
		this.releaseClient = new GitHubReleaseClient();
		this.httpClient = httpClient;
		this.operatingSystem = operatingSystem;
		this.installDirectory = installDirectory.toAbsolutePath().normalize();
		this.checksumVerifier = checksumVerifier;
		this.zipPackageExtractor = zipPackageExtractor;
		this.installerLauncher = installerLauncher;
		this.signatureVerifier = signatureVerifier;
	}

	public boolean canInstallUpdates() {
		return Files.isDirectory(installDirectory.resolve("bin")) && Files.isDirectory(installDirectory.resolve("lib"));
	}

	public Optional<UpdateRelease> findUpdate() throws IOException, InterruptedException, UpdateException {
		UpdateRelease latestRelease = releaseClient.fetchLatestRelease(operatingSystem);
		return latestRelease.isNewerThan(BuildInfo.getProgramVersion()) ? Optional.of(latestRelease) : Optional.empty();
	}

	public PreparedUpdate downloadAndPrepare(UpdateRelease release, UpdateProgressListener progressListener)
			throws IOException, InterruptedException, GeneralSecurityException, UpdateException {
		if (!canInstallUpdates()) {
			throw new UpdateException("Updates can only be installed from an extracted release distribution");
		}

		Path updatesDir = installDirectory.resolve(".updates");
		Files.createDirectories(updatesDir);
		Path workDirectory = Files.createTempDirectory(updatesDir, "gbanking-update-");

		notify(progressListener, "Downloading update archive");
		Path archiveFile = download(release.applicationAsset(), workDirectory, progressListener);
		notify(progressListener, "Downloading checksums");
		Path checksumsFile = download(release.checksumsAsset(), workDirectory, null);
		notify(progressListener, "Downloading checksum signature");
		Path signatureFile = download(release.signatureAsset(), workDirectory, null);

		notify(progressListener, "Verifying checksum signature");
		signatureVerifier().verifyOrThrow(checksumsFile, signatureFile);
		notify(progressListener, "Verifying update archive checksum");
		checksumVerifier.verify(checksumsFile, archiveFile, release.applicationAsset().name());

		notify(progressListener, "Extracting update archive");
		Path sourceDirectory = zipPackageExtractor.extract(archiveFile, workDirectory.resolve("extracted"));
		return new PreparedUpdate(release.version(), installDirectory, sourceDirectory, workDirectory);
	}

	public void launchInstaller(PreparedUpdate preparedUpdate) throws IOException, UpdateException {
		installerLauncher.launch(preparedUpdate);
	}

	private Path download(ReleaseAsset asset, Path targetDirectory, UpdateProgressListener progressListener)
			throws IOException, InterruptedException, UpdateException {
		Path targetFile = targetDirectory.resolve(asset.name()).normalize();
		if (!targetFile.startsWith(targetDirectory.normalize())) {
			throw new UpdateException("Invalid update asset name: " + asset.name());
		}

		HttpRequest request = HttpRequest.newBuilder(asset.downloadUrl())
				.header("User-Agent", "GBanking-Update")
				.GET()
				.build();
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream inputStream = response.body()) {
			if (response.statusCode() < HTTP_STATUS_OK || response.statusCode() >= HTTP_STATUS_MULTIPLE_CHOICES) {
				throw new UpdateException("Download failed for " + asset.name() + " with HTTP status " + response.statusCode());
			}

			long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(asset.size());
			try (OutputStream outputStream = Files.newOutputStream(targetFile)) {
				byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
				long downloadedBytes = 0L;
				int read;
				notifyDownloadProgress(progressListener, downloadedBytes, totalBytes);
				while ((read = inputStream.read(buffer)) >= 0) {
					outputStream.write(buffer, 0, read);
					downloadedBytes += read;
					notifyDownloadProgress(progressListener, downloadedBytes, totalBytes);
				}
			}
		}
		return targetFile;
	}

	private UpdateSignatureVerifier signatureVerifier() throws IOException, GeneralSecurityException {
		return signatureVerifier != null ? signatureVerifier : UpdateSignatureVerifier.loadDefault();
	}

	private void notify(UpdateProgressListener progressListener, String message) {
		if (progressListener != null) {
			progressListener.onProgress(message);
		}
	}

	private void notifyDownloadProgress(UpdateProgressListener progressListener, long downloadedBytes, long totalBytes) {
		if (progressListener != null) {
			progressListener.onDownloadProgress(downloadedBytes, totalBytes);
		}
	}
}
