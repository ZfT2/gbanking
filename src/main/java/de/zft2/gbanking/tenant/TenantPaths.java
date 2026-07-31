package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TenantPaths(Path tenantDirectory, Path databaseDirectory) {

	public static final String TENANT_DIRECTORY_NAME = "tenant";
	public static final String DATABASE_DIRECTORY_NAME = "db";
	public static final String BACKUP_DIRECTORY_NAME = "backup";
	public static final String ACCOUNT_STATEMENTS_DIRECTORY_NAME = "accountStatements";
	public static final String DATABASE_FILE_NAME = "gbanking.db";
	public static final String ENCRYPTED_DATABASE_FILE_NAME = DATABASE_FILE_NAME + ".enc";
	private static final String DATABASE_DIRECTORY_PARAMETER = "databaseDirectory";

	public TenantPaths(Path tenantDirectory) {
		this(tenantDirectory, defaultDatabaseDirectory(tenantDirectory));
	}

	public TenantPaths {
		tenantDirectory = Objects.requireNonNull(tenantDirectory, "tenantDirectory").normalize();
		databaseDirectory = Objects.requireNonNull(databaseDirectory, DATABASE_DIRECTORY_PARAMETER).normalize();
	}

	public static TenantPaths fromDatabaseDirectory(Path databaseDirectory) {
		Path normalizedDirectory = Objects.requireNonNull(databaseDirectory, DATABASE_DIRECTORY_PARAMETER).normalize();
		Path tenantDirectory = normalizedDirectory.getParent();
		if (!hasName(normalizedDirectory, DATABASE_DIRECTORY_NAME) || tenantDirectory == null) {
			throw new IllegalArgumentException("Database directory is not part of a tenant directory: " + databaseDirectory);
		}
		return new TenantPaths(tenantDirectory);
	}

	public static Optional<Path> findDataDirectory(Path databaseDirectory) {
		if (databaseDirectory == null) {
			return Optional.empty();
		}

		Path normalizedDirectory = databaseDirectory.normalize();
		Path tenantDirectory = normalizedDirectory.getParent();
		Path tenantsDirectory = tenantDirectory != null ? tenantDirectory.getParent() : null;
		Path dataDirectory = tenantsDirectory != null ? tenantsDirectory.getParent() : null;
		if (!hasName(normalizedDirectory, DATABASE_DIRECTORY_NAME) || tenantDirectory == null
				|| !isUuid(tenantDirectory.getFileName()) || !hasName(tenantsDirectory, TENANT_DIRECTORY_NAME) || dataDirectory == null) {
			return Optional.empty();
		}
		return Optional.of(dataDirectory);
	}

	public static Path resolveAccountStatementsDirectory(Path databaseDirectory) {
		Path normalizedDirectory = Objects.requireNonNull(databaseDirectory, DATABASE_DIRECTORY_PARAMETER).normalize();
		Path tenantDirectory = normalizedDirectory.getParent();
		if (hasName(normalizedDirectory, DATABASE_DIRECTORY_NAME) && tenantDirectory != null) {
			return new TenantPaths(tenantDirectory).accountStatementsDirectory();
		}
		return normalizedDirectory.resolve(ACCOUNT_STATEMENTS_DIRECTORY_NAME);
	}

	public Path databaseFile() {
		return databaseDirectory().resolve(DATABASE_FILE_NAME);
	}

	public Path encryptedDatabaseFile() {
		return encryptedDatabaseDirectory().resolve(ENCRYPTED_DATABASE_FILE_NAME);
	}

	public Path databaseDecryptionTempFile() {
		return databaseDirectory().resolve(DATABASE_FILE_NAME + ".tmp");
	}

	public Path databaseEncryptionTempFile() {
		return encryptedDatabaseDirectory().resolve(ENCRYPTED_DATABASE_FILE_NAME + ".tmp");
	}

	public Path backupDirectory() {
		return tenantDirectory.resolve(BACKUP_DIRECTORY_NAME);
	}

	public Path accountStatementsDirectory() {
		return tenantDirectory.resolve(ACCOUNT_STATEMENTS_DIRECTORY_NAME);
	}

	public Path dataDirectory() {
		Path tenantsDirectory = tenantDirectory.getParent();
		Path dataDirectory = tenantsDirectory != null ? tenantsDirectory.getParent() : null;
		if (dataDirectory == null) {
			throw new IllegalStateException("Tenant directory has no data directory: " + tenantDirectory);
		}
		return dataDirectory;
	}

	public Path encryptedDatabaseDirectory() {
		return tenantDirectory.resolve(DATABASE_DIRECTORY_NAME);
	}

	public boolean usesSeparateDatabaseDirectory() {
		return !databaseDirectory.equals(encryptedDatabaseDirectory());
	}

	public void createDirectories() throws IOException {
		Files.createDirectories(encryptedDatabaseDirectory());
		Files.createDirectories(databaseDirectory());
		Files.createDirectories(backupDirectory());
		Files.createDirectories(accountStatementsDirectory());
	}

	private static Path defaultDatabaseDirectory(Path tenantDirectory) {
		return Objects.requireNonNull(tenantDirectory, "tenantDirectory").resolve(DATABASE_DIRECTORY_NAME);
	}

	private static boolean hasName(Path path, String expectedName) {
		Path fileName = path != null ? path.getFileName() : null;
		return fileName != null && expectedName.equals(fileName.toString());
	}

	private static boolean isUuid(Path path) {
		if (path == null) {
			return false;
		}
		try {
			UUID.fromString(path.toString());
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
