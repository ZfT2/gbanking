package de.gbanking.db.enu;

import java.util.HashMap;
import java.util.Map;

public final class IdTypeLookup {

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
		return value == null ? null : enumClass.cast(value);
	}
}
