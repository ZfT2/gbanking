package de.zft2.gbanking.service.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.kapott.hbci.GV_Result.GVRKontoauszug.Format;
import org.kapott.hbci.GV_Result.GVRKontoauszug.GVRKontoauszugEntry;

import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.tenant.TenantFileEncryptionContext;
import de.zft2.gbanking.tenant.TenantPaths;
import de.zft2.gbanking.util.AppPaths;

class AccountStatementFileService {

	private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");

	private final Path statementsDirectory;
	private final BooleanSupplier encryptionEnabled;
	private Boolean encryptionOverride;

	AccountStatementFileService() {
		this(resolveStatementsDirectory(), AccountStatementSettings::isFileEncryptionEnabled);
	}

	AccountStatementFileService(Path statementsDirectory) {
		this(statementsDirectory, () -> false);
	}

	AccountStatementFileService(Path statementsDirectory, BooleanSupplier encryptionEnabled) {
		this.statementsDirectory = Objects.requireNonNull(statementsDirectory, "statementsDirectory").normalize();
		this.encryptionEnabled = Objects.requireNonNull(encryptionEnabled, "encryptionEnabled");
	}

	Path resolve(String fileName) {
		Path plaintextFile = plaintextFile(fileName);
		Path encryptedFile = TenantFileEncryptionContext.encryptedFile(plaintextFile);
		return Files.isRegularFile(encryptedFile, LinkOption.NOFOLLOW_LINKS) ? encryptedFile : plaintextFile;
	}

	Path prepareForOpening(String fileName) {
		Path statementFile = resolve(fileName);
		if (!fileName(statementFile).endsWith(TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX)) {
			return statementFile;
		}
		try {
			return TenantFileEncryptionContext.decryptForOpening(statementFile, fileName);
		} catch (IOException | RuntimeException exception) {
			throw new GBankingException("Kontoauszug konnte nicht entschlüsselt werden.", exception);
		}
	}

	synchronized void updateEncryption(boolean enabled) {
		try {
			Files.createDirectories(statementsDirectory);
			for (String fileName : storedFileNames(enabled)) {
				String logicalFileName = enabled ? fileName
						: fileName.substring(0, fileName.length() - TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX.length());
				updateEncryption(logicalFileName, enabled);
			}
			encryptionOverride = enabled;
		} catch (IOException | RuntimeException exception) {
			throw new GBankingException("Kontoauszüge konnten nicht umgeschlüsselt werden.", exception);
		}
	}

	synchronized Path save(BankAccount bankAccount, GVRKontoauszugEntry entry, Set<String> existingFileNames) {
		try {
			Files.createDirectories(statementsDirectory);
			String extension = extension(entry.getFormat());
			String fileName = buildFileName(bankAccount, entry, extension, existingFileNames);
			return writeStatementFile(fileName, entry.getData());
		} catch (IOException e) {
			throw new GBankingException("Kontoauszug konnte nicht gespeichert werden.", e);
		}
	}

	synchronized Path saveAs(GVRKontoauszugEntry entry, String fileName) {
		try {
			Files.createDirectories(statementsDirectory);
			return writeStatementFile(fileName, entry.getData());
		} catch (IOException e) {
			throw new GBankingException("Kontoauszug konnte nicht gespeichert werden.", e);
		}
	}

	private static Path resolveStatementsDirectory() {
		var configuredDirectory = DbRuntimeContext.getCurrentAccountStatementsDirectory();
		if (configuredDirectory.isPresent()) {
			return configuredDirectory.get();
		}
		Path databaseDirectory = AppPaths.resolveInApplicationDirectory(DbRuntimeContext.getCurrentDbDirectory());
		return TenantPaths.resolveAccountStatementsDirectory(databaseDirectory);
	}

	private String buildFileName(BankAccount bankAccount, GVRKontoauszugEntry entry, String extension, Set<String> existingFileNames) {
		String baseName = "account-" + accountIdentifier(bankAccount, entry) + "_" + statementId(entry) + "_" + statementFileName(entry);
		return resolveUniqueFileName(baseName, extension, existingFileNames);
	}

	private String resolveUniqueFileName(String baseName, String extension, Set<String> existingFileNames) {
		String fileName = baseName + "." + extension;
		int counter = 1;
		while (storageFileExists(fileName) || existingFileNames.contains(fileName)) {
			fileName = baseName + "_" + String.format(Locale.ROOT, "%02d", counter) + "." + extension;
			counter++;
		}
		return fileName;
	}

	private Path writeStatementFile(String fileName, byte[] data) throws IOException {
		Path plaintextFile = plaintextFile(fileName);
		if (!isEncryptionEnabled()) {
			Files.write(plaintextFile, data, StandardOpenOption.CREATE_NEW);
			return plaintextFile;
		}

		Path encryptedFile = TenantFileEncryptionContext.encryptedFile(plaintextFile);
		Path temporaryFile = temporaryFile(encryptedFile);
		try {
			Files.deleteIfExists(temporaryFile);
			TenantFileEncryptionContext.encrypt(data, temporaryFile);
			TenantFileEncryptionContext.moveAtomically(temporaryFile, encryptedFile);
			return encryptedFile;
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	String logicalFileName(Path statementFile) {
		String fileName = fileName(statementFile);
		return fileName.endsWith(TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX)
				? fileName.substring(0, fileName.length() - TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX.length())
				: fileName;
	}

	private Path plaintextFile(String fileName) {
		Path statementFile = statementsDirectory.resolve(Objects.requireNonNull(fileName, "fileName")).normalize();
		Path normalizedDirectory = statementsDirectory.toAbsolutePath().normalize();
		Path normalizedFile = statementFile.toAbsolutePath().normalize();
		if (!normalizedFile.startsWith(normalizedDirectory)) {
			throw new GBankingException("Ungültiger Dateipfad für Kontoauszug: " + fileName);
		}
		return statementFile;
	}

	private boolean storageFileExists(String fileName) {
		Path plaintextFile = plaintextFile(fileName);
		return Files.exists(plaintextFile) || Files.exists(TenantFileEncryptionContext.encryptedFile(plaintextFile));
	}

	private List<String> storedFileNames(boolean encrypt) throws IOException {
		try (var files = Files.list(statementsDirectory)) {
			return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
					.map(this::fileName)
					.filter(fileName -> !fileName.endsWith(".tmp"))
					.filter(fileName -> encrypt != fileName.endsWith(TenantFileEncryptionContext.ENCRYPTED_FILE_SUFFIX))
					.toList();
		}
	}

	private boolean isEncryptionEnabled() {
		return encryptionOverride != null ? encryptionOverride : encryptionEnabled.getAsBoolean();
	}

	private void updateEncryption(String fileName, boolean enabled) throws IOException {
		Path plaintextFile = plaintextFile(fileName);
		Path encryptedFile = TenantFileEncryptionContext.encryptedFile(plaintextFile);
		if (enabled && Files.isRegularFile(plaintextFile, LinkOption.NOFOLLOW_LINKS)) {
			transform(plaintextFile, encryptedFile, true);
		} else if (!enabled && Files.isRegularFile(encryptedFile, LinkOption.NOFOLLOW_LINKS)) {
			transform(encryptedFile, plaintextFile, false);
		}
	}

	private void transform(Path sourceFile, Path targetFile, boolean encrypt) throws IOException {
		Path temporaryFile = temporaryFile(targetFile);
		try {
			Files.deleteIfExists(temporaryFile);
			if (encrypt) {
				TenantFileEncryptionContext.encrypt(sourceFile, temporaryFile);
			} else {
				TenantFileEncryptionContext.decrypt(sourceFile, temporaryFile);
			}
			TenantFileEncryptionContext.moveAtomically(temporaryFile, targetFile);
			Files.delete(sourceFile);
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private Path temporaryFile(Path targetFile) {
		return targetFile.resolveSibling(fileName(targetFile) + ".tmp");
	}

	private String fileName(Path file) {
		return Objects.requireNonNull(file.getFileName(), "file name").toString();
	}

	private String accountIdentifier(BankAccount bankAccount, GVRKontoauszugEntry entry) {
		String identifier = firstText(bankAccount.getIban(), entry.getIBAN(), bankAccount.getNumber(), Integer.toString(bankAccount.getId()));
		return sanitizeFileNamePart(identifier);
	}

	private String statementId(GVRKontoauszugEntry entry) {
		LocalDate statementDate = toLocalDate(entry.getDate());
		int year = entry.getYear() > 0 ? entry.getYear() : statementYear(statementDate);
		int number = entry.getNumber() > 0 ? entry.getNumber() : statementNumber(statementDate);
		return yearText(year) + "-" + String.format(Locale.ROOT, "%04d", number);
	}

	private int statementYear(LocalDate statementDate) {
		return statementDate != null ? statementDate.getYear() : 0;
	}

	private int statementNumber(LocalDate statementDate) {
		return statementDate != null ? statementDate.getMonthValue() : 0;
	}

	private String yearText(int year) {
		return year > 0 ? Integer.toString(year) : "0000";
	}

	private String statementFileName(GVRKontoauszugEntry entry) {
		String bankFileName = removeFileExtension(fileNameOnly(entry.getFilename()));
		return sanitizeFileNamePart(firstText(bankFileName, "statement"));
	}

	private String fileNameOnly(String value) {
		String normalized = Objects.toString(value, "").replace('\\', '/');
		int slashIndex = normalized.lastIndexOf('/');
		return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
	}

	private String removeFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex <= 0) {
			return fileName;
		}
		return fileName.substring(0, dotIndex);
	}

	private String sanitizeFileNamePart(String value) {
		String sanitized = UNSAFE_FILE_NAME_CHARS.matcher(Objects.toString(value, "").trim()).replaceAll("_");
		sanitized = trimUnderscores(sanitized);
		return sanitized.isBlank() ? "unknown" : sanitized;
	}

	private String trimUnderscores(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) == '_') {
			start++;
		}
		while (end > start && value.charAt(end - 1) == '_') {
			end--;
		}
		return value.substring(start, end);
	}

	private String extension(Format format) {
		if (format == null || format.getExtention() == null || format.getExtention().isBlank()) {
			return "bin";
		}
		return format.getExtention().toLowerCase(Locale.ROOT);
	}

	private LocalDate toLocalDate(java.util.Date date) {
		if (date == null) {
			return null;
		}
		if (date instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	private String firstText(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
