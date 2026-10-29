package de.zft2.gbanking.db.enu;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class IdTypeLookup {

	private static final Logger log = LogManager.getLogger(IdTypeLookup.class);

	private IdTypeLookup() {
	}

	private static final ClassValue<Map<Integer, IdType>> CACHE = new ClassValue<>() {
		@Override
		protected Map<Integer, IdType> computeValue(Class<?> type) {
			if (!type.isEnum() || !IdType.class.isAssignableFrom(type)) {
				throw new IllegalArgumentException("Type must be an enum implementing IdType: " + type);
			}

			Map<Integer, IdType> map = new HashMap<>();
			Object[] enumConstants = type.getEnumConstants();

			for (Object enumConstant : enumConstants) {
				IdType idType = (IdType) enumConstant;

				// 0 is reserved for "no value": ResultSet.getInt() returns 0 for SQL NULL.
				if (idType.getDbStateId() <= 0) {
					throw new IllegalStateException("Non-positive dbStateId " + idType.getDbStateId() + " in enum " + type.getName());
				}
				IdType previous = map.put(idType.getDbStateId(), idType);
				if (previous != null) {
					throw new IllegalStateException("Duplicate dbStateId " + idType.getDbStateId() + " in enum " + type.getName());
				}
			}

			return Map.copyOf(map);
		}
	};

	public static <E extends Enum<E> & IdType> E forId(Class<E> enumClass, int id) {
		IdType value = CACHE.get(enumClass).get(id);
		if (value == null && id != 0) {
			log.warn("Unknown dbStateId {} for enum {}", id, enumClass.getName());
		}
		return enumClass.cast(value);
	}
}
