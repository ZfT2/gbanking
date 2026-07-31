package de.zft2.gbanking.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class DbMigrationRunner {

	private static final String LAST_STARTED_APP_VERSION = "Zuletzt gestartete Anwendungsversion";
	private static final String LAST_USED_SCHEMAVERSION_SUCCESS = "Zuletzt erfolgreich angewendete DB-Schemaversion";
	private static final String VERSION_0_0_0 = "0.0.0";
	private static final Logger log = LogManager.getLogger(DbMigrationRunner.class);
	private static final String SETTING_SCHEMA_VERSION = "db.schema.version";
	private static final String SETTING_LAST_APP_VERSION = "app.version.lastStarted";
	private static final int SETTING_DATATYPE_STRING = 8;
	private static final String BASELINE_SCHEMA_PROBE_TABLE = "bankAccess";

	private DbMigrationRunner() {
	}

	static void migrate(Connection connection) throws SQLException {
		migrate(connection, null);
	}

	static void migrate(Connection connection, DbMigrationProgressListener progressListener) throws SQLException {
		migrate(connection, progressListener, SqlTemplateRepository.getBaselineVersionScript(), SqlTemplateRepository.getVersionScripts());
	}

	static void migrate(Connection connection, DbMigrationProgressListener progressListener, List<SqlTemplateRepository.VersionScript> versionScripts)
			throws SQLException {
		migrate(connection, progressListener, null, versionScripts);
	}

	private static void migrate(Connection connection, DbMigrationProgressListener progressListener, SqlTemplateRepository.VersionScript baselineScript,
			List<SqlTemplateRepository.VersionScript> versionScripts) throws SQLException {
		ensureSettingTableExists(connection);
		if (baselineScript != null
				&& !isMigrationApplied(connection, baselineScript.getSettingKey())
				&& hasLegacySchema(connection)) {
			log.info("Existing database schema detected without migration markers. Marking baseline {} as already applied.",
					baselineScript.getVersion());
			markBaselineAsApplied(connection, baselineScript);
		}

		String appVersion = normalizeVersion(BuildInfo.getProgramVersion());
		List<SqlTemplateRepository.VersionScript> applicableScripts = versionScripts.stream()
				.sorted(Comparator.comparing(SqlTemplateRepository.VersionScript::getVersion, DbMigrationRunner::compareVersions)).toList();

		List<SqlTemplateRepository.VersionScript> pendingScripts = getPendingScripts(connection, applicableScripts);

		for (int i = 0; i < pendingScripts.size(); i++) {
			SqlTemplateRepository.VersionScript script = pendingScripts.get(i);
			notifyProgress(progressListener, script.getVersion(), false, i, pendingScripts.size());
			applyMigration(connection, script, appVersion);
			notifyProgress(progressListener, script.getVersion(), true, i + 1, pendingScripts.size());
		}

		upsertHiddenSetting(connection, SETTING_LAST_APP_VERSION, appVersion, LAST_STARTED_APP_VERSION);
	}

	private static List<SqlTemplateRepository.VersionScript> getPendingScripts(Connection connection,
			List<SqlTemplateRepository.VersionScript> applicableScripts) throws SQLException {
		List<SqlTemplateRepository.VersionScript> pendingScripts = new ArrayList<>();
		for (SqlTemplateRepository.VersionScript script : applicableScripts) {
			if (!isMigrationApplied(connection, script.getSettingKey())) {
				pendingScripts.add(script);
			}
		}
		return pendingScripts;
	}

	static boolean hasPendingMigrations(Connection connection) throws SQLException {
		return hasPendingMigrations(connection, SqlTemplateRepository.getBaselineVersionScript(), SqlTemplateRepository.getVersionScripts());
	}

	static boolean hasPendingMigrations(Connection connection, List<SqlTemplateRepository.VersionScript> versionScripts) throws SQLException {
		return hasPendingMigrations(connection, null, versionScripts);
	}

	private static boolean hasPendingMigrations(Connection connection, SqlTemplateRepository.VersionScript baselineScript,
			List<SqlTemplateRepository.VersionScript> versionScripts) throws SQLException {
		List<SqlTemplateRepository.VersionScript> scripts = versionScripts.stream()
				.sorted(Comparator.comparing(SqlTemplateRepository.VersionScript::getVersion, DbMigrationRunner::compareVersions)).toList();

		if (scripts.isEmpty()) {
			return false;
		}

		boolean settingTableExists = tableExists(connection, "setting");
		boolean legacySchema = hasLegacySchema(connection);

		for (SqlTemplateRepository.VersionScript script : scripts) {
			if (isLegacyBaselineMarkerOnly(script, baselineScript, legacySchema, settingTableExists, connection)) {
				continue;
			}
			if (!settingTableExists || !isMigrationApplied(connection, script.getSettingKey())) {
				return true;
			}
		}
		return false;
	}

	static void markBaselineAsApplied(Connection connection) throws SQLException {
		ensureSettingTableExists(connection);

		SqlTemplateRepository.VersionScript baselineScript = SqlTemplateRepository.getBaselineVersionScript();
		if (baselineScript == null) {
			return;
		}
		markBaselineAsApplied(connection, baselineScript);
	}

	private static void markBaselineAsApplied(Connection connection, SqlTemplateRepository.VersionScript baselineScript) throws SQLException {
		String appVersion = normalizeVersion(BuildInfo.getProgramVersion());
		upsertHiddenSetting(connection, baselineScript.getSettingKey(), appVersion,
				"Baseline-DB-Schema initial erstellt mit Version " + baselineScript.getVersion());
		upsertHiddenSetting(connection, SETTING_SCHEMA_VERSION, baselineScript.getVersion(), LAST_USED_SCHEMAVERSION_SUCCESS);
		upsertHiddenSetting(connection, SETTING_LAST_APP_VERSION, appVersion, LAST_STARTED_APP_VERSION);
	}

	static void markFreshSchemaAsApplied(Connection connection) throws SQLException {
		ensureSettingTableExists(connection);

		String appVersion = normalizeVersion(BuildInfo.getProgramVersion());
		List<SqlTemplateRepository.VersionScript> scripts = SqlTemplateRepository.getVersionScripts().stream()
				.sorted(Comparator.comparing(SqlTemplateRepository.VersionScript::getVersion, DbMigrationRunner::compareVersions)).toList();

		for (SqlTemplateRepository.VersionScript script : scripts) {
			upsertHiddenSetting(connection, script.getSettingKey(), appVersion, "DB-Schema bereits im Initialzustand enthalten: " + script.getVersion());
		}

		String schemaVersion = scripts.isEmpty() ? VERSION_0_0_0 : scripts.get(scripts.size() - 1).getVersion();
		upsertHiddenSetting(connection, SETTING_SCHEMA_VERSION, schemaVersion, LAST_USED_SCHEMAVERSION_SUCCESS);
		upsertHiddenSetting(connection, SETTING_LAST_APP_VERSION, appVersion, LAST_STARTED_APP_VERSION);
	}

	private static boolean hasLegacySchema(Connection connection) throws SQLException {
		return tableExists(connection, BASELINE_SCHEMA_PROBE_TABLE);
	}

	private static boolean tableExists(Connection connection, String tableName) throws SQLException {
		try (var ps = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
			ps.setString(1, tableName);
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean isLegacyBaselineMarkerOnly(SqlTemplateRepository.VersionScript script, SqlTemplateRepository.VersionScript baselineScript,
			boolean legacySchema, boolean settingTableExists, Connection connection) throws SQLException {
		return legacySchema
				&& baselineScript != null
				&& script.getSettingKey().equals(baselineScript.getSettingKey())
				&& (!settingTableExists || !isMigrationApplied(connection, script.getSettingKey()));
	}

	private static void notifyProgress(DbMigrationProgressListener progressListener, String migrationVersion, boolean completed, int completedSteps,
			int totalSteps) {
		if (progressListener != null) {
			progressListener.onProgress(migrationVersion, completed, completedSteps, Math.max(totalSteps, 1));
		}
	}

	private static void ensureSettingTableExists(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_SETTING"));
		}
	}

	private static boolean isMigrationApplied(Connection connection, String migrationKey) throws SQLException {
		try (var ps = connection.prepareStatement("SELECT 1 FROM setting WHERE attribute = ?")) {
			ps.setString(1, migrationKey);
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static void applyMigration(Connection connection, SqlTemplateRepository.VersionScript script, String appVersion) throws SQLException {
		boolean oldAutoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			for (String sql : script.getStatements()) {
				if (!sql.isBlank()) {
					statement.executeUpdate(sql);
				}
			}
			upsertHiddenSetting(connection, script.getSettingKey(), appVersion, "Automatisch angewendete DB-Migration " + script.getVersion());
			upsertHiddenSetting(connection, SETTING_SCHEMA_VERSION, script.getVersion(), LAST_USED_SCHEMAVERSION_SUCCESS);
			connection.commit();
			log.info("Applied DB migration {}", script.getResource());
		} catch (SQLException ex) {
			connection.rollback();
			throw ex;
		} finally {
			connection.setAutoCommit(oldAutoCommit);
		}
	}

	private static void upsertHiddenSetting(Connection connection, String attribute, String value, String comment) throws SQLException {
		try (var ps = connection.prepareStatement("""
				INSERT INTO setting (attribute, value, dataType, editable, visible, comment, updatedAt)
				VALUES (?, ?, ?, 0, 0, ?, datetime())
				ON CONFLICT(attribute) DO UPDATE SET
				    value = excluded.value,
				    dataType = excluded.dataType,
				    editable = excluded.editable,
				    visible = excluded.visible,
				    comment = excluded.comment,
				    updatedAt = excluded.updatedAt
				""")) {
			ps.setString(1, attribute);
			ps.setString(2, value);
			ps.setInt(3, SETTING_DATATYPE_STRING);
			ps.setString(4, comment);
			ps.executeUpdate();
		}
	}

	static int compareVersions(String left, String right) {
		List<Integer> leftParts = parseVersion(left);
		List<Integer> rightParts = parseVersion(right);
		int max = Math.max(leftParts.size(), rightParts.size());
		for (int i = 0; i < max; i++) {
			int l = i < leftParts.size() ? leftParts.get(i) : 0;
			int r = i < rightParts.size() ? rightParts.get(i) : 0;
			if (l != r) {
				return Integer.compare(l, r);
			}
		}
		return 0;
	}

	private static List<Integer> parseVersion(String version) {
		String[] split = normalizeVersion(version).split("\\.");
		List<Integer> parts = new ArrayList<>(split.length);
		for (String item : split) {
			try {
				parts.add(Integer.parseInt(item));
			} catch (NumberFormatException ex) {
				parts.add(0);
			}
		}
		return parts;
	}

	private static String normalizeVersion(String version) {
		if (version == null || version.isBlank()) {
			return VERSION_0_0_0;
		}
		String numericPart = version.trim().split("-", 2)[0];
		return numericPart.isBlank() ? VERSION_0_0_0 : numericPart;
	}
}
