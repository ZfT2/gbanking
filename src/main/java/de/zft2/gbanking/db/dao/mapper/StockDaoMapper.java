package de.zft2.gbanking.db.dao.mapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.dao.stock.StockDao;
import de.zft2.gbanking.db.dao.stock.StockDaoMetadata;
import de.zft2.gbanking.db.dao.stock.StockDaoMetadata.Column;
import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.util.TypeConverter;

public final class StockDaoMapper<T extends StockDao> extends AbstractDaoMapper<T, Void> {

	private final StockDaoMetadata<T> metadata;

	public StockDaoMapper(Class<T> type) {
		super(() -> StockDaoMetadata.of(type).newInstance());
		metadata = StockDaoMetadata.of(type);
	}

	@Override
	public void setParamsFull(T dao, PreparedStatement statement) throws SQLException {
		bind(dao, dao.getId() > 0 ? StatementType.UPDATE : StatementType.INSERT, statement);
	}

	public void bind(T dao, StatementType statementType, PreparedStatement statement) throws SQLException {
		LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
		prepareTimestamps(dao, statementType, now);
		int index = 1;
		if (statementType == StatementType.INSERT && shouldBindExplicitId(dao)) {
			statement.setInt(index++, dao.getId());
		}
		for (Column<T> column : metadata.columns()) {
			if (statementType != StatementType.UPDATE || !column.name().equals(metadata.idColumn())) {
				bindValue(statement, index++, column.get(dao));
			}
		}
		if (statementType == StatementType.INSERT && metadata.hasCreatedAt()) {
			statement.setString(index++, TypeConverter.toDateTimeString(dao.getCreatedAt()));
		}
		if (metadata.hasUpdatedAt()) {
			statement.setString(index++, TypeConverter.toDateTimeString(dao.getModifiedAt()));
		}
		if (statementType == StatementType.UPDATE) {
			statement.setInt(index, dao.getId());
		}
	}

	@Override
	void initDefaultFields(Dao dao, ResultSet resultSet) throws SQLException {
		StockDao stockDao = (StockDao) dao;
		if (!metadata.idColumn().isBlank()) {
			stockDao.setId(resultSet.getInt(metadata.idColumn()));
		}
		if (metadata.hasCreatedAt()) {
			stockDao.setCreatedAt(TypeConverter.toLocalDateTime(resultSet.getString("createdAt")));
		}
		if (metadata.hasUpdatedAt()) {
			stockDao.setModifiedAt(TypeConverter.toLocalDateTime(resultSet.getString("updatedAt")));
		}
		LocalDateTime effectiveUpdate = stockDao.getModifiedAt() != null ? stockDao.getModifiedAt() : stockDao.getCreatedAt();
		stockDao.setUpdatedAt(effectiveUpdate != null ? effectiveUpdate.toLocalDate() : null);
	}

	@Override
	void mapDao(T dao, ResultType resultType, ResultSet resultSet) throws SQLException {
		for (Column<T> column : metadata.columns()) {
			column.set(dao, readValue(resultSet, column.name(), column.type()));
		}
	}

	private void prepareTimestamps(T dao, StatementType statementType, LocalDateTime now) {
		if (statementType == StatementType.INSERT && metadata.hasCreatedAt() && dao.getCreatedAt() == null) {
			dao.setCreatedAt(now);
		}
		if (metadata.hasUpdatedAt()) {
			dao.setModifiedAt(now);
			dao.setUpdatedAt(now.toLocalDate());
		}
	}

	private boolean shouldBindExplicitId(T dao) {
		return !metadata.hasMappedIdColumn() && (!metadata.generatedId() || dao.getId() > 0);
	}

	private static void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
		if (value instanceof IdType idType) {
			statement.setInt(index, idType.getDbStateId());
		} else if (value instanceof LocalDateTime dateTime) {
			statement.setString(index, TypeConverter.toDateTimeString(dateTime));
		} else if (value instanceof LocalDate date) {
			statement.setString(index, date.toString());
		} else if (value instanceof byte[] bytes) {
			statement.setBytes(index, bytes);
		} else {
			statement.setObject(index, value);
		}
	}

	private static Object readValue(ResultSet resultSet, String column, Class<?> type) throws SQLException {
		if (IdType.class.isAssignableFrom(type)) {
			return readEnum(resultSet, column, type);
		}
		if (type == String.class) {
			return resultSet.getString(column);
		}
		if (type == byte[].class) {
			return resultSet.getBytes(column);
		}
		if (type == LocalDate.class) {
			String value = resultSet.getString(column);
			return value != null ? LocalDate.parse(value) : null;
		}
		if (type == LocalDateTime.class) {
			return TypeConverter.toLocalDateTime(resultSet.getString(column));
		}
		if (type == int.class || type == Integer.class) {
			int value = resultSet.getInt(column);
			return type == int.class || !resultSet.wasNull() ? value : null;
		}
		if (type == long.class || type == Long.class) {
			long value = resultSet.getLong(column);
			return type == long.class || !resultSet.wasNull() ? value : null;
		}
		if (type == boolean.class || type == Boolean.class) {
			boolean value = resultSet.getBoolean(column);
			return type == boolean.class || !resultSet.wasNull() ? value : null;
		}
		throw new SQLException("Unsupported stock DAO field type " + type.getName() + " for column " + column);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object readEnum(ResultSet resultSet, String column, Class<?> type) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : IdType.forId((Class) type, value);
	}
}
