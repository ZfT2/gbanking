package de.zft2.gbanking.update;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.nio.file.ExtendedOpenOption;

class UpdateInstallerLauncherIntegrationTest {

	private static final long NON_EXISTING_PROCESS_ID = Integer.MAX_VALUE;
	private static final String POWERSHELL_EXECUTABLE = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";

	@TempDir
	Path tempDir;

	@Test
	void installerShouldReplaceLibrariesAndInstituteDatabaseAndCleanupBackup() throws Exception {
		PreparedUpdate update = prepareUpdate();
		Path installDirectory = update.installDirectory();
		Path workDirectory = update.workDirectory();
		OperatingSystem operatingSystem = OperatingSystem.current();
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
		assertFalse(Files.exists(script));
		assertTrue(Files.isRegularFile(workDirectory.resolve("install-update.log")));
		assertTrue(Files.isRegularFile(installDirectory.resolve("lib/dependency-2.0.jar")));
	}

	@EnabledOnOs(OS.WINDOWS)
	@ParameterizedTest
	@ValueSource(strings = { "bin/gbanking.bat", "lib/dependency-1.0.jar", "data/institute.db", "README.md" })
	void installerShouldRetryTemporarilyLockedFiles(String lockedPath) throws Exception {
		PreparedUpdate update = prepareUpdate();
		Path script = new UpdateInstallerLauncher().createInstallerScript(update, NON_EXISTING_PROCESS_ID, false);
		Process installer = null;
		try {
			try (FileChannel lock = FileChannel.open(update.installDirectory().resolve(lockedPath), READ,
					ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE)) {
				installer = startInstaller(OperatingSystem.current(), script);
				awaitRetry(installer, update);
			}
			assertInstallerFinished(installer, 0);
			assertInstalledUpdate(update);
		} finally {
			stopInstaller(installer);
		}
	}

	@EnabledOnOs(OS.WINDOWS)
	@Test
	void installerShouldRetryPartialDirectoryCopyWithoutNestingDirectories() throws Exception {
		PreparedUpdate update = prepareUpdate();
		Path nestedLibrary = update.sourceDirectory().resolve("lib/nested/locked.jar");
		Files.createDirectories(nestedLibrary.getParent());
		writeTestJar(nestedLibrary, "nested library");
		Path script = new UpdateInstallerLauncher().createInstallerScript(update, NON_EXISTING_PROCESS_ID, false);
		Process installer = null;
		try {
			try (FileChannel lock = FileChannel.open(nestedLibrary, READ, ExtendedOpenOption.NOSHARE_READ)) {
				installer = startInstaller(OperatingSystem.current(), script);
				awaitRetry(installer, update);
			}
			assertInstallerFinished(installer, 0);
			assertInstalledUpdate(update);
			assertTrue(Files.isRegularFile(update.installDirectory().resolve("lib/nested/locked.jar")));
			assertFalse(Files.exists(update.installDirectory().resolve("lib/lib")));
		} finally {
			stopInstaller(installer);
		}
	}

	@EnabledOnOs(OS.WINDOWS)
	@Test
	void installerShouldRestoreOriginalDistributionWhenLockPersists() throws Exception {
		PreparedUpdate update = prepareUpdate();
		Path script = new UpdateInstallerLauncher().createInstallerScript(update, NON_EXISTING_PROCESS_ID, false);
		Path lockedLibrary = update.installDirectory().resolve("lib/dependency-1.0.jar");
		Process installer = null;
		try (FileChannel lock = FileChannel.open(lockedLibrary, READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			long started = System.nanoTime();
			installer = startInstaller(OperatingSystem.current(), script);
			awaitRetry(installer, update);
			assertInstallerFinished(installer, 1);
			assertTrue(System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(30), "Installer should allow 30 seconds for the lock to clear");
			assertTrue(Files.isRegularFile(update.installDirectory().resolve("bin/gbanking.bat")));
			assertTrue(Files.isRegularFile(lockedLibrary));
			assertFalse(Files.exists(update.installDirectory().resolve("lib/dependency-2.0.jar")));
			assertEquals("old institute database", Files.readString(update.installDirectory().resolve("data/institute.db"), UTF_8));
			String log = Files.readString(update.workDirectory().resolve("install-update.log"));
			assertTrue(log.contains("Update failed:"));
			assertTrue(log.contains(update.installDirectory().resolve("lib").toString()));
			assertTrue(log.contains("Rollback completed."));
		} finally {
			stopInstaller(installer);
		}
	}

	@EnabledOnOs(OS.WINDOWS)
	@ParameterizedTest
	@ValueSource(strings = { "data/institute.db", "lib" })
	void installerShouldReportOtherFailuresWithoutRetrying(String missingPath) throws Exception {
		PreparedUpdate update = prepareUpdate();
		Path script = new UpdateInstallerLauncher().createInstallerScript(update, NON_EXISTING_PROCESS_ID, false);
		Path missingSource = update.sourceDirectory().resolve(missingPath);
		if (Files.isDirectory(missingSource)) {
			Files.delete(missingSource.resolve("dependency-2.0.jar"));
		}
		Files.delete(missingSource);
		Process installer = startInstaller(OperatingSystem.current(), script);
		try {
			assertTrue(installer.waitFor(10, TimeUnit.SECONDS), "A missing update file should fail without retrying");
			String log = Files.readString(update.workDirectory().resolve("install-update.log"));
			assertEquals(1, installer.exitValue(), log);
			assertTrue(Files.isRegularFile(update.installDirectory().resolve("bin/gbanking.bat")));
			assertTrue(Files.isRegularFile(update.installDirectory().resolve("lib/dependency-1.0.jar")));
			assertFalse(Files.exists(update.installDirectory().resolve("lib/dependency-2.0.jar")));
			assertEquals("old institute database", Files.readString(update.installDirectory().resolve("data/institute.db"), UTF_8));
			assertTrue(log.contains("Update failed:"));
			assertFalse(log.contains("Retrying blocked file operation:"));
			assertTrue(log.contains("Rollback completed."));
		} finally {
			stopInstaller(installer);
		}
	}

	private PreparedUpdate prepareUpdate() throws IOException {
		Path installDirectory = tempDir.resolve("installation [test's]");
		Path sourceDirectory = tempDir.resolve("update [test's]");
		Path workDirectory = installDirectory.resolve(".updates").resolve("gbanking-update-integration");
		createDistribution(installDirectory, "dependency-1.0.jar", "old library", "old institute database");
		createDistribution(sourceDirectory, "dependency-2.0.jar", "new library", "new institute database");
		Files.createDirectories(workDirectory);
		return new PreparedUpdate("2.0", installDirectory, sourceDirectory, workDirectory);
	}

	private void awaitRetry(Process installer, PreparedUpdate update) throws Exception {
		Path logFile = update.workDirectory().resolve("install-update.log");
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		String log = "";
		while (System.nanoTime() < deadline) {
			if (Files.exists(logFile)) {
				log = Files.readString(logFile);
				if (log.contains("Retrying blocked file operation:")) {
					return;
				}
			}
			if (!installer.isAlive()) {
				break;
			}
			Thread.sleep(50);
		}
		assertTrue(log.contains("Retrying blocked file operation:"), "Installer did not retry the file lock:\n" + log);
	}

	private void assertInstallerFinished(Process installer, int exitCode) throws Exception {
		assertTrue(installer.waitFor(60, TimeUnit.SECONDS), "Update installer did not finish in time");
		assertEquals(exitCode, installer.exitValue(), new String(installer.getInputStream().readAllBytes(), UTF_8));
	}

	private void assertInstalledUpdate(PreparedUpdate update) throws IOException {
		assertTrue(Files.isRegularFile(update.installDirectory().resolve("lib/dependency-2.0.jar")));
		assertFalse(Files.exists(update.installDirectory().resolve("lib/dependency-1.0.jar")));
		assertEquals("new institute database", Files.readString(update.installDirectory().resolve("data/institute.db"), UTF_8));
		assertEquals("new library", Files.readString(update.installDirectory().resolve("README.md"), UTF_8));
		assertTrue(Files.isRegularFile(update.workDirectory().resolve("backup/lib/dependency-1.0.jar")));
	}

	private void stopInstaller(Process installer) throws InterruptedException {
		if (installer != null && installer.isAlive()) {
			installer.destroyForcibly();
			assertTrue(installer.waitFor(5, TimeUnit.SECONDS), "Could not stop the test installer");
		}
	}

	private Process startInstaller(OperatingSystem operatingSystem, Path script) throws IOException {
		List<String> command = operatingSystem.isWindows()
				? List.of(POWERSHELL_EXECUTABLE, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
				: List.of("/bin/sh", script.toString());
		return new ProcessBuilder(command).directory(script.getParent().toFile()).redirectErrorStream(true).start();
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
		Files.writeString(directory.resolve("README.md"), jarContent, UTF_8);
	}

	private void writeTestJar(Path jarFile, String content) throws IOException {
		try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
			outputStream.putNextEntry(new JarEntry("version.txt"));
			outputStream.write(content.getBytes(UTF_8));
			outputStream.closeEntry();
		}
	}
}
