package de.zft2.gbanking.db.dao.stock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StockDaoMetadata<T extends StockDao> {

	private static final Map<Class<? extends StockDao>, StockDaoMetadata<?>> CACHE = new ConcurrentHashMap<>();

	private final Class<T> type;
	private final Constructor<T> constructor;
	private final StockTable table;
	private final List<Column<T>> columns;

	private StockDaoMetadata(Class<T> type) {
		this.type = type;
		table = requireTable(type);
		constructor = requireConstructor(type);
		columns = findColumns(type);
		validateColumns();
	}

	@SuppressWarnings("unchecked")
	public static <T extends StockDao> StockDaoMetadata<T> of(Class<T> type) {
		return (StockDaoMetadata<T>) CACHE.computeIfAbsent(type, StockDaoMetadata::new);
	}

	public T newInstance() {
		try {
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not instantiate stock DAO " + type.getName(), exception);
		}
	}

	public String tableName() {
		return table.name();
	}

	public String idColumn() {
		return table.idColumn();
	}

	public String parentColumn() {
		return table.parentColumn();
	}

	public boolean generatedId() {
		return table.generatedId();
	}

	public boolean hasCreatedAt() {
		return table.createdAt();
	}

	public boolean hasUpdatedAt() {
		return table.updatedAt();
	}

	public StockWriteMode writeMode() {
		return table.writeMode();
	}

	public List<Column<T>> columns() {
		return List.copyOf(columns);
	}

	public boolean hasMappedIdColumn() {
		return columns.stream().anyMatch(column -> column.name().equals(idColumn()));
	}

	private void validateColumns() {
		Set<String> names = new HashSet<>();
		for (Column<T> column : columns) {
			if (!names.add(column.name())) {
				throw new IllegalStateException("Duplicate stock column " + column.name() + " on " + type.getName());
			}
		}
		if (idColumn().isBlank() && writeMode() != StockWriteMode.READ_ONLY) {
			throw new IllegalStateException("Writable stock DAO needs a primary-key column: " + type.getName());
		}
	}

	private static StockTable requireTable(Class<? extends StockDao> type) {
		StockTable result = type.getAnnotation(StockTable.class);
		if (result == null) {
			throw new IllegalArgumentException("Missing @StockTable on " + type.getName());
		}
		return result;
	}

	private static <T extends StockDao> Constructor<T> requireConstructor(Class<T> type) {
		try {
			return type.getConstructor();
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Stock DAO needs a no-argument constructor: " + type.getName(), exception);
		}
	}

	private static <T extends StockDao> List<Column<T>> findColumns(Class<T> type) {
		List<Column<T>> result = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
				continue;
			}
			result.add(new Column<>(toColumnName(field.getName()), field));
		}
		return List.copyOf(result);
	}

	private static String toColumnName(String fieldName) {
		return fieldName.endsWith("Id") ? fieldName.substring(0, fieldName.length() - 2) + "_id" : fieldName;
	}

	public static final class Column<T extends StockDao> {

		private final String name;
		private final Field field;
		private final Method getter;
		private final Method setter;

		private Column(String name, Field field) {
			this.name = name;
			this.field = field;
			String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
			Class<?> owner = field.getDeclaringClass();
			try {
				getter = owner.getMethod((field.getType() == boolean.class ? "is" : "get") + suffix);
				setter = owner.getMethod("set" + suffix, field.getType());
			} catch (NoSuchMethodException exception) {
				throw new IllegalStateException("Stock DAO field needs public accessors: " + field, exception);
			}
		}

		public String name() {
			return name;
		}

		public Class<?> type() {
			return field.getType();
		}

		public Object get(T dao) {
			try {
				return getter.invoke(dao);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Could not read stock DAO field " + field, exception);
			}
		}

		public void set(T dao, Object value) {
			try {
				setter.invoke(dao, value);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Could not write stock DAO field " + field, exception);
			}
		}
	}
}
