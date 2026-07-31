package de.zft2.gbanking.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GitHubReleaseClient {

	private static final URI DEFAULT_LATEST_RELEASE_URI = URI.create("https://api.github.com/repos/ZfT2/gbanking/releases/latest");
	private static final String CHECKSUMS_ASSET_NAME = "SHA256SUMS";
	private static final String SIGNATURE_ASSET_NAME = "SHA256SUMS.sig";

	private final HttpClient httpClient;
	private final URI latestReleaseUri;

	public GitHubReleaseClient() {
		this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), DEFAULT_LATEST_RELEASE_URI);
	}

	GitHubReleaseClient(HttpClient httpClient, URI latestReleaseUri) {
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
		this.latestReleaseUri = Objects.requireNonNull(latestReleaseUri, "latestReleaseUri must not be null");
	}

	public UpdateRelease fetchLatestRelease(OperatingSystem operatingSystem) throws IOException, InterruptedException, UpdateException {
		HttpRequest request = HttpRequest.newBuilder(latestReleaseUri)
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "GBanking-Update")
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() == 404) {
			throw new UpdateException("No published GitHub release was found");
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new UpdateException("GitHub release request failed with HTTP status " + response.statusCode());
		}

		return parseRelease(response.body(), operatingSystem);
	}

	UpdateRelease parseRelease(String json, OperatingSystem operatingSystem) throws UpdateException {
		Object parsedJson = SimpleJsonParser.parse(json);
		if (!(parsedJson instanceof Map<?, ?> root)) {
			throw new UpdateException("GitHub release response is not a JSON object");
		}

		String tagName = string(root, "tag_name");
		String version = VersionComparator.normalize(tagName);
		URI htmlUrl = URI.create(string(root, "html_url"));
		String body = optionalString(root, "body");
		List<Object> assets = list(root, "assets");

		ReleaseAsset applicationAsset = findApplicationAsset(assets, operatingSystem);
		ReleaseAsset checksumsAsset = findAssetByName(assets, CHECKSUMS_ASSET_NAME);
		ReleaseAsset signatureAsset = findAssetByName(assets, SIGNATURE_ASSET_NAME);
		return new UpdateRelease(version, tagName, htmlUrl, body, applicationAsset, checksumsAsset, signatureAsset);
	}

	@SuppressWarnings("unchecked")
	private ReleaseAsset findApplicationAsset(List<Object> assets, OperatingSystem operatingSystem) throws UpdateException {
		String expectedSuffix = operatingSystem.assetSuffix();
		for (Object asset : assets) {
			if (asset instanceof Map<?, ?> assetMap) {
				String name = optionalString(assetMap, "name");
				if (name.toLowerCase(Locale.ROOT).endsWith(expectedSuffix)) {
					return toReleaseAsset((Map<String, Object>) assetMap);
				}
			}
		}
		throw new UpdateException("GitHub release does not contain an asset ending with " + expectedSuffix);
	}

	@SuppressWarnings("unchecked")
	private ReleaseAsset findAssetByName(List<Object> assets, String expectedName) throws UpdateException {
		for (Object asset : assets) {
			if (asset instanceof Map<?, ?> assetMap && expectedName.equals(optionalString(assetMap, "name"))) {
				return toReleaseAsset((Map<String, Object>) assetMap);
			}
		}
		throw new UpdateException("GitHub release does not contain " + expectedName);
	}

	private ReleaseAsset toReleaseAsset(Map<String, Object> asset) throws UpdateException {
		String name = string(asset, "name");
		URI downloadUrl = URI.create(string(asset, "browser_download_url"));
		long size = number(asset, "size");
		return new ReleaseAsset(name, downloadUrl, size);
	}

	private String string(Map<?, ?> object, String key) throws UpdateException {
		Object value = object.get(key);
		if (value == null) {
			throw new UpdateException("Missing JSON field: " + key);
		}
		return String.valueOf(value);
	}

	private String optionalString(Map<?, ?> object, String key) {
		Object value = object.get(key);
		return value != null ? String.valueOf(value) : "";
	}

	private long number(Map<?, ?> object, String key) {
		Object value = object.get(key);
		return value instanceof Number number ? number.longValue() : 0L;
	}

	@SuppressWarnings("unchecked")
	private List<Object> list(Map<?, ?> object, String key) throws UpdateException {
		Object value = object.get(key);
		if (value instanceof List<?> list) {
			return (List<Object>) list;
		}
		throw new UpdateException("Missing JSON array field: " + key);
	}
}
