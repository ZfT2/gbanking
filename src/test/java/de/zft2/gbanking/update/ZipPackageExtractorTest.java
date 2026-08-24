package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipPackageExtractorTest {

	@TempDir
	Path tempDir;

	@Test
	void extract_shouldReturnDistributionRoot() throws Exception {
		Path zipFile = tempDir.resolve("gbanking.zip");
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/");
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/bin/");
			addFile(zipOutputStream, "GBanking-0.5.2-linux/bin/gbanking.sh", "run");
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/lib/");
			addFile(zipOutputStream, "GBanking-0.5.2-linux/lib/gbanking.jar", "jar");
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/data/");
			addFile(zipOutputStream, "GBanking-0.5.2-linux/data/institute.db", "database");
		}

		Path root = new ZipPackageExtractor().extract(zipFile, tempDir.resolve("extract"));

		assertEquals("GBanking-0.5.2-linux", root.getFileName().toString());
		assertTrue(Files.isDirectory(root.resolve("bin")));
		assertTrue(Files.isDirectory(root.resolve("lib")));
	}

	@Test
	void extract_shouldRejectDistributionWithoutInstituteDatabase() throws Exception {
		Path zipFile = tempDir.resolve("missing-institute-db.zip");
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/bin/");
			addDirectory(zipOutputStream, "GBanking-0.5.2-linux/lib/");
		}

		assertThrows(UpdateException.class, () -> new ZipPackageExtractor().extract(zipFile, tempDir.resolve("missing-db")));
	}

	@Test
	void extract_shouldRejectZipSlipEntries() throws Exception {
		Path zipFile = tempDir.resolve("bad.zip");
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			addFile(zipOutputStream, "../evil.txt", "bad");
		}

		assertThrows(UpdateException.class, () -> new ZipPackageExtractor().extract(zipFile, tempDir.resolve("extract")));
	}

	private void addDirectory(ZipOutputStream zipOutputStream, String name) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(name));
		zipOutputStream.closeEntry();
	}

	private void addFile(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(name));
		zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
		zipOutputStream.closeEntry();
	}
}
