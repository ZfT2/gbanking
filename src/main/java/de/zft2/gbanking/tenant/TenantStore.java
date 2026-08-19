package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.util.AppPaths;

public class TenantStore implements BaseMessages {

	public static final String DEMO_USERNAME = "demo";

	private static final Logger log = LogManager.getLogger(TenantStore.class);
	private static final String USERNAME = "username";
	private static final String ENCRYPTION_ITERATIONS = "encryptionIterations";
	private static final String ENCRYPTION_SALT = "encryptionSalt";
	private static final String WRAPPED_KEY_NONCE = "wrappedKeyNonce";
	private static final String WRAPPED_DATA_KEY = "wrappedDataKey";

	private static final String TENANT_IDS_KEY = "tenant.ids";

	private final Path dataDirectory;
	private final Path tenantsDirectory;
	private final Path registryFile;
	private final Path workDirectory;
	private final TenantEncryptionManager encryptionManager = new TenantEncryptionManager();

	public TenantStore() {
		this(AppPaths.resolveInApplicationDirectory("data"));
	}

	public TenantStore(Path dataDirectory) {
		this(dataDirectory, null);
	}

	public TenantStore(Path dataDirectory, Path workDirectory) {
		this.dataDirectory = dataDirectory;
		this.tenantsDirectory = dataDirectory.resolve(TenantPaths.TENANT_DIRECTORY_NAME);
		this.registryFile = dataDirectory.resolve("tenants.properties");
		this.workDirectory = workDirectory;
	}

	public List<TenantProfile> getTenants() {
		Properties properties = loadProperties();
		List<TenantProfile> tenants = new ArrayList<>();

		for (String tenantId : getTenantIds(properties)) {
			TenantProfile tenant = toTenant(properties, tenantId);
			if (tenant != null) {
				tenants.add(tenant);
			}
		}

		tenants.sort(Comparator.comparing(TenantProfile::username, String.CASE_INSENSITIVE_ORDER));
		return tenants;
	}

	public Optional<TenantProfile> findById(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			return Optional.empty();
		}

		return getTenants().stream().filter(tenant -> tenant.id().equals(tenantId)).findFirst();
	}

	public Optional<TenantProfile> findByUsername(String username) {
		if (username == null || username.isBlank()) {
			return Optional.empty();
		}
		String normalizedUsername = username.trim();
		return getTenants().stream().filter(tenant -> tenant.username().equalsIgnoreCase(normalizedUsername)).findFirst();
	}

	public TenantProfile createTenant(String username, char[] password) {
		return createTenant(username, password, false);
	}

	public TenantProfile createDemoTenant(char[] password) {
		return createTenant(DEMO_USERNAME, password, true);
	}

	private TenantProfile createTenant(String username, char[] password, boolean allowDemoUsername) {
		String normalizedUsername = normalizeUsername(username, allowDemoUsername);
		validateNewPassword(password);

		Properties properties = loadProperties();
		requireUniqueUsername(properties, normalizedUsername, null);

		String tenantId = UUID.randomUUID().toString();
		TenantProfile tenant = createTenantProfile(tenantId, normalizedUsername, password);
		createTenantDirectories(tenantId);
		storeTenant(properties, tenant);
		persist(properties);
		log.info("Created tenant. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(tenantId));
		return tenant;
	}

	public TenantProfile updateTenant(String tenantId, String newUsername, char[] oldPassword, char[] newPassword) {
		Properties properties = loadProperties();
		TenantProfile existingTenant = toTenant(properties, tenantId);

		if (existingTenant == null) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_NOT_FOUND"));
		}
		String normalizedUsername = normalizeUsername(newUsername, isDemoUsername(existingTenant.username()));
		if (!matchesPassword(existingTenant, oldPassword)) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_OLD_PASSWORD_WRONG"));
		}

		requireUniqueUsername(properties, normalizedUsername, tenantId);

		if (newPassword != null && newPassword.length > 0) {
			validateNewPassword(newPassword);
			return updateTenantPassword(properties, existingTenant, normalizedUsername, oldPassword, newPassword);
		}

		TenantProfile updatedTenant = new TenantProfile(tenantId, normalizedUsername, existingTenant.encryptionIterations(),
				existingTenant.encryptionSalt(), existingTenant.wrappedKeyNonce(), existingTenant.wrappedDataKey());
		storeTenant(properties, updatedTenant);
		persist(properties);
		log.info("Updated tenant. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(tenantId));
		return updatedTenant;
	}

	private TenantProfile updateTenantPassword(Properties properties, TenantProfile existingTenant, String normalizedUsername, char[] oldPassword,
			char[] newPassword) {
		try (TenantDataKey dataKey = encryptionManager.unlockDataKey(existingTenant, oldPassword)) {
			TenantKeyEnvelope envelope = encryptionManager.createEnvelope(newPassword, dataKey);
			TenantProfile updatedTenant = createTenantProfile(existingTenant.id(), normalizedUsername, envelope);
			try {
				encryptionManager.updateContainerEnvelopes(getTenantPaths(existingTenant.id()), updatedTenant, dataKey);
				storeTenant(properties, updatedTenant);
				persist(properties);
				log.info("Updated tenant. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(existingTenant.id()));
				return updatedTenant;
			} catch (IOException | RuntimeException e) {
				rollbackContainerEnvelopes(existingTenant, dataKey, e);
				if (e instanceof IOException) {
					throw new IllegalStateException(getText("UI_ERROR_TENANT_ENCRYPTION_UPDATE"), e);
				}
				throw (RuntimeException) e;
			}
		}
	}

	private void rollbackContainerEnvelopes(TenantProfile existingTenant, TenantDataKey dataKey, Exception originalFailure) {
		try {
			encryptionManager.updateContainerEnvelopes(getTenantPaths(existingTenant.id()), existingTenant, dataKey);
		} catch (IOException | RuntimeException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			log.error("Could not roll back tenant backup key envelopes", rollbackFailure);
		}
	}

	public Optional<TenantProfile> authenticate(String tenantId, char[] password) {
		Optional<TenantProfile> result = findById(tenantId).filter(tenant -> matchesPassword(tenant, password));
		if (log.isInfoEnabled()) {
			log.info("Tenant authentication {}. tenantId={}", result.isPresent() ? "succeeded" : "failed",
					SensitiveDataMasker.maskIdentifier(tenantId));
		}
		return result;
	}

	public Optional<TenantSession> authenticateSession(String tenantId, char[] password) {
		Optional<TenantProfile> tenant = findById(tenantId);
		if (tenant.isEmpty() || password == null || password.length == 0) {
			return Optional.empty();
		}
		try {
			TenantProfile profile = tenant.get();
			return Optional.of(new TenantSession(profile, getTenantPaths(profile.id()), encryptionManager.unlockDataKey(profile, password)));
		} catch (IllegalStateException e) {
			return Optional.empty();
		}
	}

	public void deleteTenant(String tenantId, char[] password) {
		if (tenantId == null || tenantId.isBlank()) {
			return;
		}

		Properties properties = loadProperties();
		requireTenantForDeletion(properties, tenantId, password);
		deleteTenantMetadata(properties, tenantId);
	}

	public void deleteTenantAndData(String tenantId, char[] password) {
		if (tenantId == null || tenantId.isBlank()) {
			return;
		}

		Properties properties = loadProperties();
		requireTenantForDeletion(properties, tenantId, password);
		deleteTenantDirectories(getTenantPaths(tenantId));
		deleteTenantMetadata(properties, tenantId);
	}

	private void requireTenantForDeletion(Properties properties, String tenantId, char[] password) {
		TenantProfile existingTenant = toTenant(properties, tenantId);
		if (existingTenant == null) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_NOT_FOUND"));
		}
		if (!matchesPassword(existingTenant, password)) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_DELETE_PASSWORD_WRONG"));
		}
	}

	private void deleteTenantMetadata(Properties properties, String tenantId) {
		List<String> tenantIds = new ArrayList<>(getTenantIds(properties));
		tenantIds.remove(tenantId);

		properties.setProperty(TENANT_IDS_KEY, String.join(",", tenantIds));
		properties.remove(getTenantKey(tenantId, USERNAME));
		properties.remove(getTenantKey(tenantId, ENCRYPTION_ITERATIONS));
		properties.remove(getTenantKey(tenantId, ENCRYPTION_SALT));
		properties.remove(getTenantKey(tenantId, WRAPPED_KEY_NONCE));
		properties.remove(getTenantKey(tenantId, WRAPPED_DATA_KEY));

		persist(properties);
		log.info("Deleted tenant metadata. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(tenantId));
	}

	private void deleteTenantDirectories(TenantPaths paths) {
		deleteDirectory(paths.tenantDirectory(), tenantsDirectory);
		if (!paths.usesSeparateDatabaseDirectory()) {
			return;
		}

		Path workTenantDirectory = paths.databaseDirectory().getParent();
		Path workTenantsDirectory = workTenantDirectory != null ? workTenantDirectory.getParent() : null;
		deleteDirectory(workTenantDirectory, workTenantsDirectory);
	}

	private void deleteDirectory(Path directory, Path expectedParent) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}

		Path normalizedDirectory = directory.toAbsolutePath().normalize();
		Path normalizedParent = expectedParent != null ? expectedParent.toAbsolutePath().normalize() : null;
		if (normalizedParent == null || !normalizedParent.equals(normalizedDirectory.getParent())) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_DELETE_DIRECTORY"));
		}

		try {
			log.debug("Deleting tenant directory: {}", normalizedDirectory);
			Files.walkFileTree(normalizedDirectory, new DeleteDirectoryVisitor());
		} catch (IOException exception) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_DELETE_DIRECTORY"), exception);
		}
	}

	public Path getTenantDirectory(String tenantId) {
		return getTenantPaths(tenantId).tenantDirectory();
	}

	public TenantPaths getTenantPaths(String tenantId) {
		Path tenantDirectory = tenantsDirectory.resolve(tenantId);
		if (workDirectory == null) {
			return new TenantPaths(tenantDirectory);
		}
		Path databaseDirectory = workDirectory.resolve(TenantPaths.TENANT_DIRECTORY_NAME).resolve(tenantId)
				.resolve(TenantPaths.DATABASE_DIRECTORY_NAME);
		return new TenantPaths(tenantDirectory, databaseDirectory);
	}

	private void createTenantDirectories(String tenantId) {
		try {
			getTenantPaths(tenantId).createDirectories();
		} catch (IOException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_SAVE"), e);
		}
	}

	private TenantProfile createTenantProfile(String tenantId, String username, char[] password) {
		return createTenantProfile(tenantId, username, encryptionManager.createEnvelope(password));
	}

	private TenantProfile createTenantProfile(String tenantId, String username, TenantKeyEnvelope envelope) {
		return new TenantProfile(tenantId, username, envelope.iterations(), envelope.salt(), envelope.nonce(), envelope.wrappedKey());
	}

	private boolean matchesPassword(TenantProfile tenant, char[] password) {
		if (tenant == null || password == null || password.length == 0) {
			return false;
		}

		try (TenantDataKey ignored = encryptionManager.unlockDataKey(tenant, password)) {
			return true;
		} catch (IllegalStateException e) {
			return false;
		}
	}

	private void validateNewPassword(char[] password) {
		if (password == null || password.length == 0) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_PASSWORD_REQUIRED"));
		}
	}

	private void requireUniqueUsername(Properties properties, String username, String currentTenantId) {
		for (String tenantId : getTenantIds(properties)) {
			if (tenantId.equals(currentTenantId)) {
				continue;
			}

			TenantProfile existingTenant = toTenant(properties, tenantId);
			if (existingTenant != null && existingTenant.username().equalsIgnoreCase(username)) {
				throw new IllegalArgumentException(getText("UI_ERROR_TENANT_USERNAME_DUPLICATE"));
			}
		}
	}

	private TenantProfile toTenant(Properties properties, String tenantId) {
		String username = properties.getProperty(getTenantKey(tenantId, USERNAME));
		String encryptionIterations = properties.getProperty(getTenantKey(tenantId, ENCRYPTION_ITERATIONS));
		String encryptionSalt = properties.getProperty(getTenantKey(tenantId, ENCRYPTION_SALT));
		String wrappedKeyNonce = properties.getProperty(getTenantKey(tenantId, WRAPPED_KEY_NONCE));
		String wrappedDataKey = properties.getProperty(getTenantKey(tenantId, WRAPPED_DATA_KEY));

		if (username == null || encryptionIterations == null || encryptionSalt == null || wrappedKeyNonce == null || wrappedDataKey == null) {
			return null;
		}

		try {
			return new TenantProfile(tenantId, username, Integer.parseInt(encryptionIterations), encryptionSalt, wrappedKeyNonce, wrappedDataKey);
		} catch (NumberFormatException e) {
			log.warn("Ignoring tenant with invalid encryption metadata. tenantId={}", () -> SensitiveDataMasker.maskIdentifier(tenantId));
			return null;
		}
	}

	private void storeTenant(Properties properties, TenantProfile tenant) {
		List<String> tenantIds = new ArrayList<>(getTenantIds(properties));
		if (!tenantIds.contains(tenant.id())) {
			tenantIds.add(tenant.id());
		}

		properties.setProperty(TENANT_IDS_KEY, String.join(",", tenantIds));
		properties.setProperty(getTenantKey(tenant.id(), USERNAME), tenant.username());
		properties.setProperty(getTenantKey(tenant.id(), ENCRYPTION_ITERATIONS), Integer.toString(tenant.encryptionIterations()));
		properties.setProperty(getTenantKey(tenant.id(), ENCRYPTION_SALT), tenant.encryptionSalt());
		properties.setProperty(getTenantKey(tenant.id(), WRAPPED_KEY_NONCE), tenant.wrappedKeyNonce());
		properties.setProperty(getTenantKey(tenant.id(), WRAPPED_DATA_KEY), tenant.wrappedDataKey());
	}

	private List<String> getTenantIds(Properties properties) {
		String ids = properties.getProperty(TENANT_IDS_KEY, "");
		if (ids.isBlank()) {
			return List.of();
		}

		List<String> tenantIds = new ArrayList<>();
		for (String tenantId : ids.split(",")) {
			if (!tenantId.isBlank()) {
				tenantIds.add(tenantId.trim());
			}
		}
		return tenantIds;
	}

	private String normalizeUsername(String username, boolean allowDemoUsername) {
		if (username == null || username.trim().isEmpty()) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_USERNAME_REQUIRED"));
		}
		String normalizedUsername = username.trim();
		if (!allowDemoUsername && isDemoUsername(normalizedUsername)) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_USERNAME_RESERVED_DEMO"));
		}
		return normalizedUsername;
	}

	private static boolean isDemoUsername(String username) {
		return username != null && DEMO_USERNAME.equalsIgnoreCase(username.trim());
	}

	private String getTenantKey(String tenantId, String attribute) {
		return "tenant." + tenantId + "." + attribute;
	}

	private Properties loadProperties() {
		Properties properties = new Properties();
		if (!Files.exists(registryFile)) {
			return properties;
		}

		try (Reader reader = Files.newBufferedReader(registryFile, StandardCharsets.UTF_8)) {
			properties.load(reader);
			return properties;
		} catch (IOException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_LOAD"), e);
		}
	}

	private void persist(Properties properties) {
		Path tempRegistryFile = registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
		try {
			Files.createDirectories(dataDirectory);
			Files.deleteIfExists(tempRegistryFile);
			try (Writer writer = Files.newBufferedWriter(tempRegistryFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
				properties.store(writer, getText("UI_TENANT_STORE_COMMENT"));
			}
			try (FileChannel channel = FileChannel.open(tempRegistryFile, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			moveRegistryFile(tempRegistryFile);
		} catch (IOException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_SAVE"), e);
		} finally {
			try {
				Files.deleteIfExists(tempRegistryFile);
			} catch (IOException e) {
				log.warn("Could not delete temporary tenant registry file", e);
			}
		}
	}

	private void moveRegistryFile(Path tempRegistryFile) throws IOException {
		try {
			Files.move(tempRegistryFile, registryFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			throw new IOException("Atomic tenant registry replacement is not supported", e);
		}
	}

	private static final class DeleteDirectoryVisitor extends SimpleFileVisitor<Path> {

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.deleteIfExists(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
			if (exception != null) {
				throw exception;
			}
			Files.deleteIfExists(dir);
			return FileVisitResult.CONTINUE;
		}
	}

}
