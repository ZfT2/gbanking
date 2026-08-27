package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TempStore;

@EnabledIfSystemProperty(named = "gbanking.indexBenchmark", matches = "true")
class DbIndexBenchmarkTest {

	private static final String BENCHMARK_SQL_RESOURCE = "sql/test/index-benchmark.sql";
	private static final int CACHE_SIZE_KIB = 32 * 1024;
	private static final int SAMPLE_COUNT = 7;
	private static final int INSTITUTE_SAMPLE_COUNT = 3;
	private static final int INSTITUTE_REPETITIONS = 50;
	private static final int INSTITUTE_WARMUP_REPETITIONS = 3;
	private static final Logger log = LogManager.getLogger(DbIndexBenchmarkTest.class);
	private static final Map<String, String> BENCHMARK_SQL = loadBenchmarkSql();
	private static volatile int resultSink;

	@TempDir
	Path temporaryDirectory;

	@Test
	void performanceIndexes_shouldProvideMeasurableBenefits(TestReporter reporter) throws Exception {
		Path databaseFile = temporaryDirectory.resolve("index-benchmark.db");
		try (Connection connection = openConnection(databaseFile)) {
			createScaledDemoDatabase(connection);
			List<BenchmarkScenario> scenarios = createScenarios();
			List<String> results = new ArrayList<>();
			List<String> failures = new ArrayList<>();

			for (BenchmarkScenario scenario : scenarios) {
				assertScenarioReturnsRows(connection, scenario);
				BenchmarkResult result = benchmarkIndex(connection, scenario);
				addResult(reporter, results, failures, scenario.indexName(), result);
			}
			BenchmarkResult relationCleanup = benchmarkParameterDataCleanup(connection);
			addResult(reporter, results, failures, "idx_bankaccess_parameterdata_parameter", relationCleanup);

			assertTrue(failures.isEmpty(), () -> "Index benchmark results:\n"
					+ String.join("\n", results) + "\n\nNot significant:\n" + String.join("\n", failures));
		}
	}

	@Test
	void instituteIndexes_shouldUseTheFastestTriggerStrategy(TestReporter reporter) throws Exception {
		Path sourceDatabase = findProjectRoot().resolve("data/institute.db");
		assertTrue(Files.isRegularFile(sourceDatabase), () -> "Missing institute database: " + sourceDatabase);
		Path instituteDatabase = temporaryDirectory.resolve("institute.db");
		Files.copy(sourceDatabase, instituteDatabase, StandardCopyOption.REPLACE_EXISTING);

		try (Connection connection = openConnection(temporaryDirectory.resolve("institute-index-benchmark.db"))) {
			attachInstituteDatabase(connection, instituteDatabase);
			ensureInstituteIndexes(connection);
			scaleInstituteData(connection);
			InstituteFixture fixture = createInstituteFixture(connection);
			List<String> results = new ArrayList<>();
			Map<InstituteVariant, List<InstituteTiming>> timingSamples = new EnumMap<>(InstituteVariant.class);
			for (InstituteVariant variant : InstituteVariant.values()) {
				timingSamples.put(variant, new ArrayList<>());
			}

			int round = 0;
			for (List<InstituteVariant> order : instituteMeasurementOrders()) {
				round++;
				for (InstituteVariant variant : order) {
					configureInstituteIndexes(connection, variant);
					InstituteTiming timing = measureInstituteTriggers(connection, fixture);
					timingSamples.get(variant).add(timing);
					report(reporter, variant.name() + " round " + round, timing.format());
				}
			}

			Map<InstituteVariant, InstituteTiming> timings = new EnumMap<>(InstituteVariant.class);
			for (InstituteVariant variant : InstituteVariant.values()) {
				InstituteTiming timing = medianInstituteTiming(timingSamples.get(variant));
				timings.put(variant, timing);
				String line = variant + " median: " + timing.format();
				results.add(line);
				report(reporter, variant.name() + " median", timing.format());
			}

			InstituteTiming blzOnly = timings.get(InstituteVariant.BLZ_ONLY);
			assertTrue(blzOnly.isAtLeastTwiceAsFastAs(timings.get(InstituteVariant.NONE))
					&& blzOnly.totalNanos() < timings.get(InstituteVariant.DETAIL_ONLY).totalNanos()
					&& blzOnly.totalNanos() < timings.get(InstituteVariant.ALL).totalNanos(),
					() -> "Institute index benchmark results:\n" + String.join("\n", results));
		}
	}

	private static BenchmarkResult benchmarkIndex(Connection connection, BenchmarkScenario scenario) throws SQLException {
		String indexDdl = readIndexDdl(connection, scenario.indexName());
		assertNotNull(indexDdl, () -> "Missing index " + scenario.indexName());

		long indexedBefore = measureMedianNanos(connection, scenario);
		execute(connection, benchmarkSql(scenario.dropSqlKey()));
		analyze(connection);
		long withoutIndex = measureMedianNanos(connection, scenario);

		execute(connection, indexDdl);
		analyze(connection);
		long indexedAfter = measureMedianNanos(connection, scenario);
		long indexed = Math.round((indexedBefore + indexedAfter) / 2.0);
		return new BenchmarkResult(indexed, withoutIndex, scenario.minimumSpeedup(),
				scenario.minimumSavedNanos());
	}

	private static void assertScenarioReturnsRows(Connection connection, BenchmarkScenario scenario) throws SQLException {
		boolean hasRows;
		try (PreparedStatement statement = connection.prepareStatement(scenario.sql())) {
			scenario.binder().bind(statement);
			try (ResultSet resultSet = statement.executeQuery()) {
				hasRows = resultSet.next();
			}
		}
		assertTrue(hasRows, "Benchmark scenario for " + scenario.indexName() + " returned no rows");
	}

	private static BenchmarkResult benchmarkParameterDataCleanup(Connection connection) throws SQLException {
		String indexName = "idx_bankaccess_parameterdata_parameter";
		String indexDdl = readIndexDdl(connection, indexName);
		assertNotNull(indexDdl, () -> "Missing index " + indexName);
		long indexed = measureParameterDataCleanup(connection);
		execute(connection, benchmarkSql("SQL_BENCHMARK_DROP_BANKACCESS_PARAMETERDATA"));
		analyze(connection);
		long unindexed = measureParameterDataCleanup(connection);
		execute(connection, indexDdl);
		analyze(connection);
		return new BenchmarkResult(indexed, unindexed, 1.5, 1_000_000);
	}

	private static long measureParameterDataCleanup(Connection connection) throws SQLException {
		long[] samples = new long[5];
		connection.setAutoCommit(false);
		try (PreparedStatement statement = connection.prepareStatement(
				benchmarkSql("SQL_BENCHMARK_DELETE_BANKACCESS_PARAMETER_DATA"))) {
			for (int sample = 0; sample < samples.length; sample++) {
				Savepoint savepoint = connection.setSavepoint();
				statement.setInt(1, 100001);
				long start = System.nanoTime();
				statement.executeUpdate();
				samples[sample] = System.nanoTime() - start;
				connection.rollback(savepoint);
			}
		} finally {
			connection.rollback();
			connection.setAutoCommit(true);
		}
		return medianNanos(samples);
	}

	private static void addResult(TestReporter reporter, List<String> results, List<String> failures,
			String indexName, BenchmarkResult result) {
		report(reporter, indexName, result.format());
		String line = indexName + ": " + result.format();
		results.add(line);
		if (!result.isSignificant()) {
			failures.add(line);
		}
	}

	private static void report(TestReporter reporter, String name, String result) {
		reporter.publishEntry(name, result);
		log.info("{}: {}", name, result);
	}

	private static long measureMedianNanos(Connection connection, BenchmarkScenario scenario) throws SQLException {
		measureNanos(connection, scenario, 2);
		long[] samples = new long[SAMPLE_COUNT];
		for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
			samples[sample] = measureNanos(connection, scenario, scenario.repetitions())
					/ scenario.repetitions();
		}
		return medianNanos(samples);
	}

	private static long measureNanos(Connection connection, BenchmarkScenario scenario, int repetitions)
			throws SQLException {
		long start = System.nanoTime();
		try (PreparedStatement statement = connection.prepareStatement(scenario.sql())) {
			for (int repetition = 0; repetition < repetitions; repetition++) {
				scenario.binder().bind(statement);
				consume(statement);
			}
		}
		return System.nanoTime() - start;
	}

	private static InstituteTiming measureInstituteTriggers(Connection connection, InstituteFixture fixture)
			throws SQLException {
		return new InstituteTiming(
				measureUpdateMedian(connection, "SQL_BENCHMARK_UPDATE_INSTITUTE_DK_IMPORT_NUMBER",
						statement -> bind(statement, fixture.uniqueImportNumber(), fixture.dkInstituteId()), false),
				measureUpdateMedian(connection, "SQL_BENCHMARK_UPDATE_INSTITUTE_DK_IMPORT_NUMBER",
						statement -> bind(statement, fixture.duplicateImportNumber(), fixture.dkInstituteId()), true),
				measureUpdateMedian(connection, "SQL_BENCHMARK_UPDATE_INSTITUTE_DBB_DATASET_NUMBER",
						statement -> bind(statement, fixture.uniqueDatasetNumber(), fixture.dbbInstituteId()), false),
				measureUpdateMedian(connection, "SQL_BENCHMARK_UPDATE_INSTITUTE_DBB_DATASET_NUMBER",
						statement -> bind(statement, fixture.duplicateDatasetNumber(), fixture.dbbInstituteId()), true));
	}

	private static long measureUpdateMedian(Connection connection, String sqlKey, StatementBinder binder,
			boolean expectFailure) throws SQLException {
		measureUpdates(connection, sqlKey, binder, expectFailure, INSTITUTE_WARMUP_REPETITIONS);
		long[] samples = new long[INSTITUTE_SAMPLE_COUNT];
		for (int sample = 0; sample < INSTITUTE_SAMPLE_COUNT; sample++) {
			samples[sample] = measureUpdates(connection, sqlKey, binder, expectFailure, INSTITUTE_REPETITIONS)
					/ INSTITUTE_REPETITIONS;
		}
		return medianNanos(samples);
	}

	private static long measureUpdates(Connection connection, String sqlKey, StatementBinder binder,
			boolean expectFailure, int repetitions) throws SQLException {
		String expectedFailureText = sqlKey.contains("_DK_")
				? "InstituteDk (UPDATE):"
				: "InstituteDbb (UPDATE):";
		long start = System.nanoTime();
		try (PreparedStatement statement = connection.prepareStatement(benchmarkSql(sqlKey))) {
			for (int repetition = 0; repetition < repetitions; repetition++) {
				binder.bind(statement);
				try {
					statement.executeUpdate();
					if (expectFailure) {
						throw new AssertionError("Institute duplicate trigger did not reject the update");
					}
				} catch (SQLException exception) {
					if (!expectFailure || !String.valueOf(exception.getMessage()).contains(expectedFailureText)) {
						throw exception;
					}
				}
			}
		}
		return System.nanoTime() - start;
	}

	private static void consume(PreparedStatement statement) throws SQLException {
		int checksum = 1;
		try (ResultSet resultSet = statement.executeQuery()) {
			ResultSetMetaData metadata = resultSet.getMetaData();
			int columnCount = metadata.getColumnCount();
			while (resultSet.next()) {
				for (int column = 1; column <= columnCount; column++) {
					Object value = resultSet.getObject(column);
					checksum = 31 * checksum + (value == null ? 0 : value.hashCode());
				}
			}
		}
		resultSink = checksum;
	}

	private static List<BenchmarkScenario> createScenarios() {
		return List.of(
				benchmarkScenario("idx_booking_account", "SQL_BENCHMARK_DROP_BOOKING_ACCOUNT",
						"SQL_BENCHMARK_LOOKUP_BOOKING_ACCOUNT_CHILDREN", 20,
						statement -> bind(statement, 110075)),
				benchmarkScenario("idx_booking_account_root_amount_date", "SQL_BENCHMARK_DROP_BOOKING_AMOUNT_DATE",
						"SQL_BENCHMARK_LOOKUP_BOOKING_AMOUNT_DATE", 100,
						statement -> bind(statement, 110075, 0.01, "2019-01-01 00:00:00.000")),
				dmlScenario("idx_booking_recipient_usage", "SQL_BENCHMARK_DROP_BOOKING_RECIPIENT_USAGE",
						"SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN", 5,
						statement -> bind(statement, "DEBENCHMARKTARGET", "DEBENCHMARKTARGET")),
				benchmarkScenario("idx_booking_cross_booking", "SQL_BENCHMARK_DROP_BOOKING_CROSS",
						"SQL_BENCHMARK_LOOKUP_BOOKING_CROSS", 100,
						statement -> bind(statement, 1000001)),
				benchmarkScenario("idx_booking_category_category", "SQL_BENCHMARK_DROP_BOOKING_CATEGORY",
						"SQL_BENCHMARK_LOOKUP_BOOKING_CATEGORY", 10,
						statement -> bind(statement, 920021)),
				benchmarkScenario("idx_categoryrule_bankaccount_account", "SQL_BENCHMARK_DROP_CATEGORYRULE_BANKACCOUNT",
						"SQL_BENCHMARK_LOOKUP_CATEGORY_RULE_ACCOUNT", 10,
						statement -> bind(statement, 110075)),
				dmlScenario("idx_moneytransfer_account_status", "SQL_BENCHMARK_DROP_MONEYTRANSFER_ACCOUNT_STATUS",
						"SQL_SELECT_ALL_MONEYTRANSFERS_WITH_RECIPIENTS_BY_ACCOUNT_AND_STATE", 10,
						statement -> bind(statement, 110075, 1)),
				dmlScenario("idx_moneytransfer_recipient_usage", "SQL_BENCHMARK_DROP_MONEYTRANSFER_RECIPIENT_USAGE",
						"SQL_SELECT_PREFERRED_RECIPIENT_BY_IBAN", 5,
						statement -> bind(statement, "DEBENCHMARKTARGET", "DEBENCHMARKTARGET")),
				benchmarkScenario("idx_moneytransfer_history", "SQL_BENCHMARK_DROP_MONEYTRANSFER_HISTORY",
						"SQL_BENCHMARK_LOOKUP_MONEY_TRANSFER_HISTORY", 100,
						statement -> bind(statement, 500099)),
				dmlScenario("idx_moneytransferprotocol_transfer", "SQL_BENCHMARK_DROP_MONEYTRANSFER_PROTOCOL",
						"SQL_SELECT_ALL_MONEYTRANSFER_PROTOCOLS_BY_MONEYTRANSFER", 50,
						statement -> bind(statement, 500001)));
	}

	private static BenchmarkScenario dmlScenario(String indexName, String dropSqlKey, String sqlKey,
			int repetitions, StatementBinder binder) {
		return new BenchmarkScenario(indexName, dropSqlKey, SqlTemplateRepository.getDml(sqlKey), repetitions,
				binder, 1.25, 10_000);
	}

	private static BenchmarkScenario benchmarkScenario(String indexName, String dropSqlKey, String sqlKey,
			int repetitions, StatementBinder binder) {
		return new BenchmarkScenario(indexName, dropSqlKey, benchmarkSql(sqlKey), repetitions,
				binder, 1.25, 10_000);
	}

	private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
		for (int index = 0; index < parameters.length; index++) {
			statement.setObject(index + 1, parameters[index]);
		}
	}

	private static void createScaledDemoDatabase(Connection connection) throws SQLException {
		connection.setAutoCommit(false);
		try {
			executeAll(connection, SqlTemplateRepository.getMainBaselineStatements());
			executeAll(connection, SqlTemplateRepository.getDemoStatements());
			for (Map.Entry<String, String> entry : BENCHMARK_SQL.entrySet()) {
				if (entry.getKey().startsWith("SQL_BENCHMARK_SCALE_")
						&& !entry.getKey().startsWith("SQL_BENCHMARK_SCALE_INSTITUTE_")) {
					try {
						execute(connection, entry.getValue());
					} catch (SQLException exception) {
						throw new SQLException("Could not execute " + entry.getKey(), exception);
					}
				}
			}
			connection.commit();
		} catch (SQLException exception) {
			connection.rollback();
			throw exception;
		} finally {
			connection.setAutoCommit(true);
		}
		analyze(connection);
	}

	private static void scaleInstituteData(Connection connection) throws SQLException {
		int dkIdOffset = readMaximumInstituteId(connection);
		executeScaleStatements(connection, "SQL_BENCHMARK_SCALE_INSTITUTE_DK_", dkIdOffset);
		int dbbIdOffset = readMaximumInstituteId(connection);
		executeScaleStatements(connection, "SQL_BENCHMARK_SCALE_INSTITUTE_DBB_", dbbIdOffset);
	}

	private static void executeScaleStatements(Connection connection, String keyPrefix, int idOffset) throws SQLException {
		for (Map.Entry<String, String> entry : BENCHMARK_SQL.entrySet()) {
			if (entry.getKey().startsWith(keyPrefix)) {
				try (PreparedStatement statement = connection.prepareStatement(entry.getValue())) {
					statement.setInt(1, idOffset);
					statement.execute();
				}
			}
		}
	}

	private static List<List<InstituteVariant>> instituteMeasurementOrders() {
		return List.of(
				List.of(InstituteVariant.NONE, InstituteVariant.BLZ_ONLY,
						InstituteVariant.DETAIL_ONLY, InstituteVariant.ALL),
				List.of(InstituteVariant.ALL, InstituteVariant.DETAIL_ONLY,
						InstituteVariant.BLZ_ONLY, InstituteVariant.NONE),
				List.of(InstituteVariant.DETAIL_ONLY, InstituteVariant.ALL,
						InstituteVariant.NONE, InstituteVariant.BLZ_ONLY),
				List.of(InstituteVariant.BLZ_ONLY, InstituteVariant.NONE,
						InstituteVariant.ALL, InstituteVariant.DETAIL_ONLY));
	}

	private static InstituteTiming medianInstituteTiming(List<InstituteTiming> timings) {
		return new InstituteTiming(
				medianNanos(timings.stream().mapToLong(timing -> timing.dkMissNanos()).toArray()),
				medianNanos(timings.stream().mapToLong(timing -> timing.dkHitNanos()).toArray()),
				medianNanos(timings.stream().mapToLong(timing -> timing.dbbMissNanos()).toArray()),
				medianNanos(timings.stream().mapToLong(timing -> timing.dbbHitNanos()).toArray()));
	}

	private static long medianNanos(long[] samples) {
		Arrays.sort(samples);
		int middle = samples.length / 2;
		if (samples.length % 2 != 0) {
			return samples[middle];
		}
		return samples[middle - 1] + (samples[middle] - samples[middle - 1]) / 2;
	}

	private static Connection openConnection(Path databaseFile) throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		config.setBusyTimeout(5_000);
		config.setCacheSize(-CACHE_SIZE_KIB);
		config.setTempStore(TempStore.MEMORY);
		config.setSynchronous(SynchronousMode.FULL);
		config.setJournalMode(JournalMode.WAL);
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile, config.toProperties());
		execute(connection, SqlTemplateRepository.getConfig("SQL_MAIN_MMAP_SIZE"));
		return connection;
	}

	private static void attachInstituteDatabase(Connection connection, Path instituteDatabase) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				SqlTemplateRepository.getConfig("SQL_ATTACH_INSTITUTE_DATABASE"))) {
			statement.setString(1, instituteDatabase.toString());
			statement.execute();
		}
	}

	private static void ensureInstituteIndexes(Connection connection) throws SQLException {
		execute(connection, SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_INDEX_INSTITUTE_BLZ_STATE"));
	}

	private static InstituteFixture createInstituteFixture(Connection connection) throws SQLException {
		InstitutePair dkPair = readInstitutePair(connection, "SQL_BENCHMARK_SELECT_INSTITUTE_DK_PAIR");
		InstitutePair dbbPair = readInstitutePair(connection, "SQL_BENCHMARK_SELECT_INSTITUTE_DBB_PAIR");
		int firstId = readMaximumInstituteId(connection) + 1;
		int secondId = firstId + 1;
		int uniqueImportNumber = Integer.MIN_VALUE;
		String uniqueDatasetNumber = "__GBANKING_INDEX_BENCHMARK__";

		executeUpdate(connection, "SQL_BENCHMARK_INSERT_INSTITUTE", statement -> bind(statement, firstId, dkPair.blz()));
		executeUpdate(connection, "SQL_BENCHMARK_INSERT_INSTITUTE_DK",
				statement -> bind(statement, firstId, uniqueImportNumber));
		executeUpdate(connection, "SQL_BENCHMARK_INSERT_INSTITUTE", statement -> bind(statement, secondId, dbbPair.blz()));
		executeUpdate(connection, "SQL_BENCHMARK_INSERT_INSTITUTE_DBB",
				statement -> bind(statement, secondId, uniqueDatasetNumber));
		return new InstituteFixture(firstId, secondId, uniqueImportNumber, dkPair.value(),
				uniqueDatasetNumber, dbbPair.value());
	}

	private static InstitutePair readInstitutePair(Connection connection, String sqlKey) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(benchmarkSql(sqlKey))) {
			if (!resultSet.next()) {
				throw new IllegalStateException("Institute database has no data for " + sqlKey);
			}
			return new InstitutePair(resultSet.getString(1), resultSet.getObject(2));
		}
	}

	private static int readMaximumInstituteId(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(SqlTemplateRepository.getDml("SQL_SELECT_MAX_INSTITUTE_ID"))) {
			return resultSet.next() ? resultSet.getInt(1) : 0;
		}
	}

	private static void executeUpdate(Connection connection, String sqlKey, StatementBinder binder) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(benchmarkSql(sqlKey))) {
			binder.bind(statement);
			statement.executeUpdate();
		}
	}

	private static void configureInstituteIndexes(Connection connection, InstituteVariant variant) throws SQLException {
		execute(connection, benchmarkSql("SQL_BENCHMARK_DROP_INSTITUTE_BLZ_STATE"));
		execute(connection, benchmarkSql("SQL_BENCHMARK_DROP_INSTITUTE_DK_IMPORT_NUMBER"));
		execute(connection, benchmarkSql("SQL_BENCHMARK_DROP_INSTITUTE_DBB_DATASET_NUMBER"));
		if (variant.useBlzIndex()) {
			execute(connection, SqlTemplateRepository.getDdl("SQL_SETUP_CREATE_INDEX_INSTITUTE_BLZ_STATE"));
		}
		if (variant.useDetailIndexes()) {
			execute(connection, benchmarkSql("SQL_BENCHMARK_CREATE_INSTITUTE_DK_IMPORT_NUMBER"));
			execute(connection, benchmarkSql("SQL_BENCHMARK_CREATE_INSTITUTE_DBB_DATASET_NUMBER"));
		}
		execute(connection, benchmarkSql("SQL_BENCHMARK_ANALYZE_INSTITUTE"));
	}

	private static Path findProjectRoot() throws URISyntaxException {
		Path classesDirectory = Path.of(DbIndexBenchmarkTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		return classesDirectory.getParent().getParent();
	}

	private static String readIndexDdl(Connection connection, String indexName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				benchmarkSql("SQL_BENCHMARK_SELECT_INDEX_DDL"))) {
			statement.setString(1, indexName);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getString(1) : null;
			}
		}
	}

	private static void analyze(Connection connection) throws SQLException {
		execute(connection, benchmarkSql("SQL_BENCHMARK_ANALYZE"));
	}

	private static void executeAll(Connection connection, Iterable<String> statements) throws SQLException {
		for (String sql : statements) {
			execute(connection, sql);
		}
	}

	private static void execute(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static String benchmarkSql(String key) {
		String sql = BENCHMARK_SQL.get(key);
		if (sql == null) {
			throw new IllegalArgumentException("Unknown benchmark SQL key: " + key);
		}
		return sql;
	}

	private static Map<String, String> loadBenchmarkSql() {
		InputStream input = DbIndexBenchmarkTest.class.getClassLoader().getResourceAsStream(BENCHMARK_SQL_RESOURCE);
		if (input == null) {
			throw new IllegalStateException("Missing benchmark SQL resource: " + BENCHMARK_SQL_RESOURCE);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			return parseBenchmarkSql(reader);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read benchmark SQL resource", exception);
		}
	}

	private static Map<String, String> parseBenchmarkSql(BufferedReader reader) throws IOException {
		Map<String, String> statements = new LinkedHashMap<>();
		String currentKey = null;
		StringBuilder currentSql = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			String trimmed = line.trim();
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				currentKey = trimmed.substring(1, trimmed.length() - 1);
				currentSql.setLength(0);
			} else if (!trimmed.isEmpty() && !trimmed.startsWith("#") && currentKey != null) {
				boolean terminated = trimmed.endsWith(";");
				currentSql.append(terminated ? line.substring(0, line.lastIndexOf(';')) : line)
						.append(System.lineSeparator());
				if (terminated) {
					statements.put(currentKey, currentSql.toString().strip());
					currentKey = null;
					currentSql.setLength(0);
				}
			}
		}
		return Collections.unmodifiableMap(statements);
	}

	@FunctionalInterface
	private interface StatementBinder {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private record BenchmarkScenario(String indexName, String dropSqlKey, String sql, int repetitions,
			StatementBinder binder, double minimumSpeedup, long minimumSavedNanos) {
	}

	private record BenchmarkResult(long indexedNanos, long unindexedNanos, double minimumSpeedup,
			long minimumSavedNanos) {

		private double speedup() {
			return (double) unindexedNanos / indexedNanos;
		}

		private boolean isSignificant() {
			return speedup() >= minimumSpeedup && unindexedNanos - indexedNanos >= minimumSavedNanos;
		}

		private String format() {
			return String.format(Locale.ROOT, "with=%7.3f ms, without=%7.3f ms, speedup=%5.2fx, saved=%7.3f ms",
					indexedNanos / 1_000_000.0, unindexedNanos / 1_000_000.0, speedup(),
					(unindexedNanos - indexedNanos) / 1_000_000.0);
		}
	}

	private record InstitutePair(String blz, Object value) {
	}

	private record InstituteFixture(int dkInstituteId, int dbbInstituteId, int uniqueImportNumber,
			Object duplicateImportNumber, String uniqueDatasetNumber, Object duplicateDatasetNumber) {
	}

	private record InstituteTiming(long dkMissNanos, long dkHitNanos, long dbbMissNanos, long dbbHitNanos) {

		private boolean isAtLeastTwiceAsFastAs(InstituteTiming other) {
			return dkMissNanos * 2 < other.dkMissNanos
					&& dkHitNanos * 2 < other.dkHitNanos
					&& dbbMissNanos * 2 < other.dbbMissNanos
					&& dbbHitNanos * 2 < other.dbbHitNanos;
		}

		private long totalNanos() {
			return dkMissNanos + dkHitNanos + dbbMissNanos + dbbHitNanos;
		}

		private String format() {
			return String.format(Locale.ROOT, "DK miss=%7.3f ms, DK hit=%7.3f ms, DBB miss=%7.3f ms, DBB hit=%7.3f ms",
					dkMissNanos / 1_000_000.0, dkHitNanos / 1_000_000.0,
					dbbMissNanos / 1_000_000.0, dbbHitNanos / 1_000_000.0);
		}
	}

	private enum InstituteVariant {
		NONE(false, false),
		BLZ_ONLY(true, false),
		DETAIL_ONLY(false, true),
		ALL(true, true);

		private final boolean useBlzIndex;
		private final boolean useDetailIndexes;

		InstituteVariant(boolean useBlzIndex, boolean useDetailIndexes) {
			this.useBlzIndex = useBlzIndex;
			this.useDetailIndexes = useDetailIndexes;
		}

		private boolean useBlzIndex() {
			return useBlzIndex;
		}

		private boolean useDetailIndexes() {
			return useDetailIndexes;
		}
	}
}
