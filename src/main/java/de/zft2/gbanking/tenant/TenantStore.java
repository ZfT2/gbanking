package de.zft2.gbanking.tenant;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import de.zft2.gbanking.messages.Messages;
import de.zft2.gbanking.util.AppPaths;

public class TenantStore {

	private static final String TENANT_IDS_KEY = "tenant.ids";
	private static final int SALT_LENGTH = 16;
	private static final int ITERATIONS = 65_536;
	private static final int KEY_LENGTH = 256;

	private final Path baseDirectory;
	private final Path registryFile;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Messages messages = Messages.getInstance();

	public TenantStore() {
		this(AppPaths.resolveInApplicationDirectory("db"));
	}

	public TenantStore(Path baseDirectory) {
		this.baseDirectory = baseDirectory;
		this.registryFile = baseDirectory.resolve("tenants.properties");
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

	public TenantProfile createTenant(String username, char[] password) {
		String normalizedUsername = normalizeUsername(username);
		validateNewPassword(password);

		Properties properties = loadProperties();
		requireUniqueUsername(properties, normalizedUsername, null);

		String tenantId = UUID.randomUUID().toString();
		TenantProfile tenant = createTenantProfile(tenantId, normalizedUsername, password);
		storeTenant(properties, tenant);
		persist(properties);
		return tenant;
	}

	public TenantProfile updateTenant(String tenantId, String newUsername, char[] oldPassword, char[] newPassword) {
		String normalizedUsername = normalizeUsername(newUsername);
		Properties properties = loadProperties();
		TenantProfile existingTenant = toTenant(properties, tenantId);

		if (existingTenant == null) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_NOT_FOUND"));
		}
		if (!matchesPassword(existingTenant, oldPassword)) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_OLD_PASSWORD_WRONG"));
		}

		requireUniqueUsername(properties, normalizedUsername, tenantId);

		TenantProfile updatedTenant = existingTenant;
		if (newPassword != null && newPassword.length > 0) {
			validateNewPassword(newPassword);
			updatedTenant = createTenantProfile(tenantId, normalizedUsername, newPassword);
		} else {
			updatedTenant = new TenantProfile(tenantId, normalizedUsername, existingTenant.passwordSalt(), existingTenant.passwordHash());
		}

		storeTenant(properties, updatedTenant);
		persist(properties);
		return updatedTenant;
	}

	public Optional<TenantProfile> authenticate(String tenantId, char[] password) {
		return findById(tenantId).filter(tenant -> matchesPassword(tenant, password));
	}

	public void deleteTenant(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			return;
		}

		Properties properties = loadProperties();
		List<String> tenantIds = new ArrayList<>(getTenantIds(properties));
		tenantIds.remove(tenantId);

		properties.setProperty(TENANT_IDS_KEY, String.join(",", tenantIds));
		properties.remove(getTenantKey(tenantId, "username"));
		properties.remove(getTenantKey(tenantId, "passwordSalt"));
		properties.remove(getTenantKey(tenantId, "passwordHash"));

		persist(properties);
	}

	public Path getTenantDirectory(String tenantId) {
		return baseDirectory.resolve(tenantId);
	}

	private TenantProfile createTenantProfile(String tenantId, String username, char[] password) {
		byte[] salt = new byte[SALT_LENGTH];
		secureRandom.nextBytes(salt);

		return new TenantProfile(tenantId, username, Base64.getEncoder().encodeToString(salt),
				Base64.getEncoder().encodeToString(hashPassword(password, salt)));
	}

	private boolean matchesPassword(TenantProfile tenant, char[] password) {
		if (tenant == null || password == null || password.length == 0) {
			return false;
		}

		byte[] salt = Base64.getDecoder().decode(tenant.passwordSalt());
		byte[] expectedHash = Base64.getDecoder().decode(tenant.passwordHash());
		byte[] actualHash = hashPassword(password, salt);
		return MessageDigest.isEqual(expectedHash, actualHash);
	}

	private byte[] hashPassword(char[] password, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_PASSWORD_HASH"), e);
		} finally {
			spec.clearPassword();
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
		String username = properties.getProperty(getTenantKey(tenantId, "username"));
		String passwordSalt = properties.getProperty(getTenantKey(tenantId, "passwordSalt"));
		String passwordHash = properties.getProperty(getTenantKey(tenantId, "passwordHash"));

		if (username == null || passwordSalt == null || passwordHash == null) {
			return null;
		}

		return new TenantProfile(tenantId, username, passwordSalt, passwordHash);
	}

	private void storeTenant(Properties properties, TenantProfile tenant) {
		List<String> tenantIds = new ArrayList<>(getTenantIds(properties));
		if (!tenantIds.contains(tenant.id())) {
			tenantIds.add(tenant.id());
		}

		properties.setProperty(TENANT_IDS_KEY, String.join(",", tenantIds));
		properties.setProperty(getTenantKey(tenant.id(), "username"), tenant.username());
		properties.setProperty(getTenantKey(tenant.id(), "passwordSalt"), tenant.passwordSalt());
		properties.setProperty(getTenantKey(tenant.id(), "passwordHash"), tenant.passwordHash());
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

	private String normalizeUsername(String username) {
		if (username == null || username.trim().isEmpty()) {
			throw new IllegalArgumentException(getText("UI_ERROR_TENANT_USERNAME_REQUIRED"));
		}
		return username.trim();
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
		try {
			Files.createDirectories(baseDirectory);
			try (Writer writer = Files.newBufferedWriter(registryFile, StandardCharsets.UTF_8)) {
				properties.store(writer, getText("UI_TENANT_STORE_COMMENT"));
			}
		} catch (IOException e) {
			throw new IllegalStateException(getText("UI_ERROR_TENANT_SAVE"), e);
		}
	}

	private String getText(String key) {
		return messages.getMessage(key);
	}
}
