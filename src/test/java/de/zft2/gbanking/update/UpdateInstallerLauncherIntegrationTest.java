package de.zft2.gbanking.update;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateInstallerLauncherIntegrationTest {

	private static final long NON_EXISTING_PROCESS_ID = Integer.MAX_VALUE;
	private static final String POWERSHELL_EXECUTABLE = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";

	@TempDir
	Path tempDir;

	@Test
	void installerShouldReplaceLibrariesAndInstituteDatabaseAndCleanupBackup() throws Exception {
		Path installDirectory = tempDir.resolve("installation");
		Path sourceDirectory = tempDir.resolve("update");
		Path workDirectory = installDirectory.resolve(".updates").resolve("gbanking-update-integration");
		createDistribution(installDirectory, "dependency-1.0.jar", "old library", "old institute database");
		createDistribution(sourceDirectory, "dependency-2.0.jar", "new library", "new institute database");
		Files.createDirectories(workDirectory);

		OperatingSystem operatingSystem = OperatingSystem.current();
		PreparedUpdate update = new PreparedUpdate("2.0", installDirectory, sourceDirectory, workDirectory);
		Path script = new UpdateInstallerLauncher(operatingSystem).createInstallerScript(update, NON_EXISTING_PROCESS_ID, false);
		Process installer = startInstaller(operatingSystem, script);
		boolean finished = installer.waitFor(30, TimeUnit.SECONDS);
		if (!finished) {
			installer.destroyForcibly();
		}
		assertTrue(finished, "Update installer did not finish in time");
		String installerOutput = new String(installer.getInputStream().readAllBytes(), UTF_8);
		assertEquals(0, installer.exitValue(), installerOutput);

		assertFalse(Files.exists(installDirectory.resolve("lib/dependency-1.0.jar")));
		assertTrue(Files.isRegularFile(installDirectory.resolve("lib/dependency-2.0.jar")));
		assertEquals("new institute database", Files.readString(installDirectory.resolve("data/institute.db"), UTF_8));

		Path backupDirectory = workDirectory.resolve("backup");
		assertTrue(Files.isRegularFile(backupDirectory.resolve("lib/dependency-1.0.jar")));
		assertEquals("old institute database", Files.readString(backupDirectory.resolve("data/institute.db"), UTF_8));

		new UpdateBackupCleaner().cleanup(installDirectory);

		assertFalse(Files.exists(backupDirectory));
		assertTrue(Files.isRegularFile(installDirectory.resolve("lib/dependency-2.0.jar")));
	}

	private Process startInstaller(OperatingSystem operatingSystem, Path script) throws IOException {
		List<String> command = operatingSystem.isWindows()
				? List.of(POWERSHELL_EXECUTABLE, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
				: List.of("/bin/sh", script.toString());
		return new ProcessBuilder(command).redirectErrorStream(true).start();
	}

	private void createDistribution(Path directory, String jarName, String jarContent, String instituteContent) throws IOException {
		Path binDirectory = directory.resolve("bin");
		Path libDirectory = directory.resolve("lib");
		Path dataDirectory = directory.resolve("data");
		Files.createDirectories(binDirectory);
		Files.createDirectories(libDirectory);
		Files.createDirectories(dataDirectory);
		Files.writeString(binDirectory.resolve("gbanking.bat"), "@exit /b 0\r\n", UTF_8);
		Files.writeString(binDirectory.resolve("gbanking.sh"), "#!/usr/bin/env sh\nexit 0\n", UTF_8);
		Files.writeString(binDirectory.resolve("gbanking.command"), "#!/usr/bin/env sh\nexit 0\n", UTF_8);
		writeTestJar(libDirectory.resolve(jarName), jarContent);
		Files.writeString(dataDirectory.resolve("institute.db"), instituteContent, UTF_8);
	}

	private void writeTestJar(Path jarFile, String content) throws IOException {
		try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
			outputStream.putNextEntry(new JarEntry("version.txt"));
			outputStream.write(content.getBytes(UTF_8));
			outputStream.closeEntry();
		}
	}
}
