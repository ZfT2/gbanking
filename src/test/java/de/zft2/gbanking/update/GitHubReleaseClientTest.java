package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GitHubReleaseClientTest {

	@Test
	void parseRelease_shouldSelectPlatformAssetAndVerificationAssets() throws Exception {
		String json = """
				{
				  "tag_name": "v0.5.2",
				  "html_url": "https://github.com/ZfT2/gbanking/releases/tag/v0.5.2",
				  "body": "Release notes",
				  "assets": [
				    {
				      "name": "gbanking-0.5.2-linux.zip",
				      "browser_download_url": "https://example.invalid/linux.zip",
				      "size": 11
				    },
				    {
				      "name": "gbanking-0.5.2-windows.zip",
				      "browser_download_url": "https://example.invalid/windows.zip",
				      "size": 22
				    },
				    {
				      "name": "SHA256SUMS",
				      "browser_download_url": "https://example.invalid/SHA256SUMS",
				      "size": 33
				    },
				    {
				      "name": "SHA256SUMS.sig",
				      "browser_download_url": "https://example.invalid/SHA256SUMS.sig",
				      "size": 44
				    }
				  ]
				}
				""";

		UpdateRelease release = new GitHubReleaseClient().parseRelease(json, OperatingSystem.WINDOWS);

		assertEquals("0.5.2", release.version());
		assertEquals("gbanking-0.5.2-windows.zip", release.applicationAsset().name());
		assertEquals("SHA256SUMS", release.checksumsAsset().name());
		assertEquals("SHA256SUMS.sig", release.signatureAsset().name());
	}

	@Test
	void parseRelease_shouldRejectReleaseWithoutSignatureAsset() {
		String json = """
				{
				  "tag_name": "v0.5.2",
				  "html_url": "https://github.com/ZfT2/gbanking/releases/tag/v0.5.2",
				  "assets": [
				    {
				      "name": "gbanking-0.5.2-windows.zip",
				      "browser_download_url": "https://example.invalid/windows.zip",
				      "size": 22
				    },
				    {
				      "name": "SHA256SUMS",
				      "browser_download_url": "https://example.invalid/SHA256SUMS",
				      "size": 33
				    }
				  ]
				}
				""";

		assertThrows(UpdateException.class, () -> new GitHubReleaseClient().parseRelease(json, OperatingSystem.WINDOWS));
	}
}
