package de.gbanking.db.dao.enu;

import java.math.BigDecimal;
import java.time.LocalDate;

import de.gbanking.db.enu.IdType;

public enum DataType implements IdType {

	INT(Integer.class, 1),
	DOUBLE(Double.class, 2),
	FLOAT(Float.class, 3),
	BIGDECIMAL(BigDecimal.class, 4),
	BOOLEAN(Boolean.class, 5),
	CALENDAR(LocalDate.class, 6),
	ENUM(IdType.class, 7),
	STRING(String.class, 8),
	CHAR(char.class, 9);

	public static DataType forType(Class<?> type) {
		for (DataType x : values()) {
			if (x.classType.equals(type))
				return x;
		}
		return null;
	}

	public static DataType forInt(int intValue) {
		return IdType.forId(DataType.class, intValue);
	}

	private final Class<?> classType;
	private final int dbStateId;

	private DataType(Class<?> classType, int dbStateId) {
		this.classType = classType;
		this.dbStateId = dbStateId;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	@Override
	public final String toString() {
		return this.name();
	}

}
