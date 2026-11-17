package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

import de.zft2.gbanking.db.dao.stock.StockDao;
import de.zft2.gbanking.db.dao.stock.StockDaoMetadata;
import de.zft2.gbanking.db.dao.stock.StockDaoTypes;
import de.zft2.gbanking.db.dao.stock.StockImportRecord;
import de.zft2.gbanking.db.enu.IdType;

class StockDaoMetadataTest {

	@Test
	void mappedBinaryColumnsShouldUsePublicDefensiveAccessors() {
		var metadata = StockDaoMetadata.of(StockImportRecord.class);
		var column = metadata.columns().stream().filter(candidate -> candidate.name().equals("rawRecord")).findFirst().orElseThrow();
		StockImportRecord dao = metadata.newInstance();
		byte[] input = { 1, 2 };
		column.set(dao, input);
		input[0] = 9;
		byte[] output = (byte[]) column.get(dao);
		assertEquals(1, output[0]);
		output[0] = 8;
		assertEquals(1, dao.getRawRecord()[0]);
	}

	@Test
	void everyStockTableColumnShouldHaveExactlyOneDaoMapping() throws Exception {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties())) {
			DbDdlSetup.setupDB(connection);
			for (Class<? extends StockDao> type : StockDaoTypes.all()) {
				StockDaoMetadata<?> metadata = metadata(type);
				assertEquals(databaseColumns(connection, metadata.tableName()), mappedColumns(metadata), type::getName);
				Map<String, String> columnTypes = databaseColumnTypes(connection, metadata.tableName());
				metadata.columns().stream()
						.filter(column -> IdType.class.isAssignableFrom(column.type()))
						.forEach(column -> assertEquals("INTEGER", columnTypes.get(column.name()),
								() -> type.getName() + "." + column.name()));
			}
		}
	}

	private static Set<String> databaseColumns(Connection connection, String tableName) throws Exception {
		return new LinkedHashSet<>(databaseColumnTypes(connection, tableName).keySet());
	}

	private static Map<String, String> databaseColumnTypes(Connection connection, String tableName) throws Exception {
		Map<String, String> result = new LinkedHashMap<>();
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("PRAGMA table_info('" + tableName + "')")) {
			while (rows.next()) {
				result.put(rows.getString("name"), rows.getString("type"));
			}
		}
		return result;
	}

	private static Set<String> mappedColumns(StockDaoMetadata<?> metadata) {
		Set<String> result = new LinkedHashSet<>();
		if (!metadata.idColumn().isBlank()) {
			result.add(metadata.idColumn());
		}
		metadata.columns().forEach(column -> result.add(column.name()));
		if (metadata.hasCreatedAt()) {
			result.add("createdAt");
		}
		if (metadata.hasUpdatedAt()) {
			result.add("updatedAt");
		}
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static StockDaoMetadata<?> metadata(Class<? extends StockDao> type) {
		return StockDaoMetadata.of((Class) type);
	}
}
