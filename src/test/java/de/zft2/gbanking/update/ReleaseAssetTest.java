package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ReleaseAssetTest {

	@Test
	void constructorShouldAcceptPlainFileNamesOnly() {
		URI downloadUrl = URI.create("https://example.invalid/gbanking.zip");

		assertDoesNotThrow(() -> new ReleaseAsset("gbanking.zip", downloadUrl, 123L));
		assertThrows(IllegalArgumentException.class, () -> new ReleaseAsset("../gbanking.zip", downloadUrl, 123L));
		assertThrows(IllegalArgumentException.class, () -> new ReleaseAsset("dist/gbanking.zip", downloadUrl, 123L));
		assertThrows(IllegalArgumentException.class, () -> new ReleaseAsset("dist\\gbanking.zip", downloadUrl, 123L));
		assertThrows(IllegalArgumentException.class, () -> new ReleaseAsset("   ", downloadUrl, 123L));
	}

	@Test
	void isPlainFileNameShouldRejectPathTraversalAndDirectories() {
		assertTrue(ReleaseAsset.isPlainFileName("SHA256SUMS.sig"));
		assertFalse(ReleaseAsset.isPlainFileName(null));
		assertFalse(ReleaseAsset.isPlainFileName(""));
		assertFalse(ReleaseAsset.isPlainFileName("../SHA256SUMS"));
		assertFalse(ReleaseAsset.isPlainFileName("nested/SHA256SUMS"));
		assertFalse(ReleaseAsset.isPlainFileName("nested\\SHA256SUMS"));
	}
}
