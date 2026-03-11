package de.gbanking.db.enu;

import java.math.BigDecimal;
import java.util.Calendar;

public enum DataType implements IdType {

	INT(Integer.class, 1), 
	DOUBLE(Double.class, 2), 
	FLOAT(Float.class, 3),
	BIGDECIMAL(BigDecimal.class, 4),
	CALENDAR(Calendar.class, 5),
	ENUM(IdType.class, 6),
	STRING(String.class, 7),
	CHAR(char.class, 8);

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
