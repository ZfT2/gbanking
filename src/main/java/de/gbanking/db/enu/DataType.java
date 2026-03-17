package de.gbanking.db.enu;

import java.math.BigDecimal;
import java.util.Calendar;

public enum DataType implements IdType {

	INT(Integer.class, 1),
	DOUBLE(Double.class, 2),
	FLOAT(Float.class, 3),
	BIGDECIMAL(BigDecimal.class, 4),
	BOOLEAN(Boolean.class, 5),
	CALENDAR(Calendar.class, 6),
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

	public static IdType forInt(int intValue) {
		for (DataType x : values()) {
			if (x.dbStateId == intValue)
				return x;
		}
		return null;
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
