package de.zft2.gbanking.db.enu;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdTypeLookupTest {

	@Test
	void forId_shouldReturnMatchingEnumConstant() {
		assertSame(SampleIdType.ONE, IdTypeLookup.forId(SampleIdType.class, 1));
		assertSame(SampleIdType.TWO, IdType.forId(SampleIdType.class, 2));
	}

	@Test
	void forId_shouldReturnNullForUnknownId() {
		assertNull(IdTypeLookup.forId(SampleIdType.class, 99));
	}

	@Test
	void forId_shouldRejectEnumsWithoutIdType() {
		assertThrows(IllegalArgumentException.class, () -> lookupWithoutIdType());
	}

	@Test
	void forId_shouldRejectDuplicateDbStateIds() {
		assertThrows(IllegalStateException.class, () -> IdTypeLookup.forId(DuplicateIdType.class, 1));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object lookupWithoutIdType() {
		return IdTypeLookup.forId((Class) NotAnIdType.class, 1);
	}

	private enum SampleIdType implements IdType {
		ONE(1),
		TWO(2);

		private final int id;

		SampleIdType(int id) {
			this.id = id;
		}

		@Override
		public int getDbStateId() {
			return id;
		}
	}

	private enum DuplicateIdType implements IdType {
		FIRST(1),
		SECOND(1);

		private final int id;

		DuplicateIdType(int id) {
			this.id = id;
		}

		@Override
		public int getDbStateId() {
			return id;
		}
	}

	private enum NotAnIdType {
		VALUE
	}
}
