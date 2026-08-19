package de.zft2.gbanking.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.zft2.gbanking.db.BuildInfo;
import de.zft2.gbanking.util.AppPaths;

public final class DiagnosticPackageCreator {

	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String CURRENT_LOG_FILE = "gbanking.log";
	private static final String ROLLED_LOG_PREFIX = "gbanking-";

	private final Path logDirectory;

	public DiagnosticPackageCreator() {
		this(AppPaths.resolveInApplicationDirectory("logs"));
	}

	DiagnosticPackageCreator(Path logDirectory) {
		this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory").toAbsolutePath().normalize();
	}

	public Path getLogDirectory() {
		return logDirectory;
	}

	public String defaultFileName() {
		return "gbanking-diagnose-" + FILE_TIMESTAMP.format(java.time.LocalDateTime.now(ZoneId.systemDefault())) + ".zip";
	}

	public Path createDiagnosticPackage(Path selectedFile) throws IOException {
		Path target = ensureZipExtension(Objects.requireNonNull(selectedFile, "selectedFile").toAbsolutePath().normalize());
		Path parent = Objects.requireNonNull(target.getParent(), "target parent");
		String fileName = fileName(target);
		Files.createDirectories(parent);
		Path temporaryFile = Files.createTempFile(parent, fileName, ".tmp");
		List<Path> logFiles = findLogFiles();
		try {
			writePackage(temporaryFile, logFiles);
			moveIntoPlace(temporaryFile, target);
			return target;
		} catch (IOException exception) {
			deleteTemporaryFile(temporaryFile, exception);
			throw exception;
		}
	}

	private List<Path> findLogFiles() throws IOException {
		if (!Files.isDirectory(logDirectory)) {
			return List.of();
		}
		try (var files = Files.list(logDirectory)) {
			return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).filter(DiagnosticPackageCreator::isGbankingLog)
					.sorted(Comparator.comparing(DiagnosticPackageCreator::fileName)).toList();
		}
	}

	private static boolean isGbankingLog(Path file) {
		String fileName = fileName(file);
		return CURRENT_LOG_FILE.equals(fileName)
				|| fileName.startsWith(ROLLED_LOG_PREFIX) && (fileName.endsWith(".log") || fileName.endsWith(".log.gz"));
	}

	private static void writePackage(Path target, List<Path> logFiles) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
			writeEntry(zip, "diagnostics.txt", diagnosticInformation(logFiles.size()).getBytes(StandardCharsets.UTF_8));
			for (Path logFile : logFiles) {
				zip.putNextEntry(new ZipEntry("logs/" + fileName(logFile)));
				Files.copy(logFile, zip);
				zip.closeEntry();
			}
		}
	}

	private static String diagnosticInformation(int logFileCount) {
		return "generatedAt=" + OffsetDateTime.now(ZoneId.systemDefault()) + '\n'
				+ "applicationVersion=" + BuildInfo.getProgramVersion() + '\n'
				+ "javaVersion=" + BuildInfo.getJavaVersion() + '\n'
				+ "javaFxVersion=" + BuildInfo.getJavaFxVersion() + '\n'
				+ "osName=" + systemProperty("os.name") + '\n'
				+ "osVersion=" + systemProperty("os.version") + '\n'
				+ "osArchitecture=" + systemProperty("os.arch") + '\n'
				+ "locale=" + Locale.getDefault().toLanguageTag() + '\n'
				+ "timeZone=" + ZoneId.systemDefault() + '\n'
				+ "availableProcessors=" + Runtime.getRuntime().availableProcessors() + '\n'
				+ "logFileCount=" + logFileCount + '\n';
	}

	private static String systemProperty(String key) {
		return Objects.toString(System.getProperty(key), "unknown");
	}

	private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content);
		zip.closeEntry();
	}

	private static Path ensureZipExtension(Path file) {
		String fileName = fileName(file);
		return fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
				? file
				: file.resolveSibling(fileName + ".zip");
	}

	private static String fileName(Path file) {
		return Objects.requireNonNull(file.getFileName(), "file name").toString();
	}

	private static void moveIntoPlace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteTemporaryFile(Path temporaryFile, IOException originalFailure) {
		try {
			Files.deleteIfExists(temporaryFile);
		} catch (IOException cleanupFailure) {
			originalFailure.addSuppressed(cleanupFailure);
		}
	}
}
