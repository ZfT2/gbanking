package de.zft2.gbanking.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticPackageCreatorTest {

	@TempDir
	private Path tempDirectory;

	@Test
	void shouldCreateDiagnosticPackageWithOnlyGbankingLogs() throws Exception {
		Path logDirectory = tempDirectory.resolve("logs");
		Files.createDirectories(logDirectory);
		Files.writeString(logDirectory.resolve("gbanking.log"), "current log", StandardCharsets.UTF_8);
		Files.writeString(logDirectory.resolve("gbanking-2026-07-20-1.log"), "rolled log", StandardCharsets.UTF_8);
		writeGzip(logDirectory.resolve("gbanking-2026-07-19-1.log.gz"), "compressed log");
		Files.writeString(logDirectory.resolve("unrelated.log"), "not included", StandardCharsets.UTF_8);

		DiagnosticPackageCreator creator = new DiagnosticPackageCreator(logDirectory);
		Path result = creator.createDiagnosticPackage(tempDirectory.resolve("diagnosis"));

		assertEquals("diagnosis.zip", result.getFileName().toString());
		assertTrue(Files.isRegularFile(result));
		try (ZipFile zip = new ZipFile(result.toFile())) {
			assertNotNull(zip.getEntry("diagnostics.txt"));
			assertNotNull(zip.getEntry("logs/gbanking.log"));
			assertNotNull(zip.getEntry("logs/gbanking-2026-07-20-1.log"));
			assertNotNull(zip.getEntry("logs/gbanking-2026-07-19-1.log.gz"));
			assertFalse(zip.stream().anyMatch(entry -> entry.getName().contains("unrelated")));
			try (InputStream diagnosticsStream = zip.getInputStream(zip.getEntry("diagnostics.txt"))) {
				String diagnostics = new String(diagnosticsStream.readAllBytes(), StandardCharsets.UTF_8);
				assertTrue(diagnostics.contains("applicationVersion="));
				assertTrue(diagnostics.contains("logFileCount=3"));
			}
		}
	}

	@Test
	void shouldCreatePackageWithoutLogDirectory() throws Exception {
		DiagnosticPackageCreator creator = new DiagnosticPackageCreator(tempDirectory.resolve("missing-logs"));

		Path result = creator.createDiagnosticPackage(tempDirectory.resolve("diagnosis.zip"));

		try (ZipFile zip = new ZipFile(result.toFile())) {
			assertNotNull(zip.getEntry("diagnostics.txt"));
			assertEquals(1, zip.size());
		}
	}

	private static void writeGzip(Path file, String content) throws Exception {
		try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(file))) {
			output.write(content.getBytes(StandardCharsets.UTF_8));
		}
	}
}
