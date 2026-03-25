package de.gbanking.db.enu;

public interface IdType {

	int getDbStateId();

	static <E extends Enum<E> & IdType> E forId(Class<E> enumClass, int id) {
		return IdTypeLookup.forId(enumClass, id);
	}
}