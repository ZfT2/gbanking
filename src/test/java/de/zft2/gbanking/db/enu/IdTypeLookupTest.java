package de.zft2.gbanking.db.enu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
		assertNull(IdTypeLookup.forId(SampleIdType.class, 0));
	}

	@Test
	void forId_shouldRejectEnumsWithoutIdType() {
		assertThrows(IllegalArgumentException.class, () -> lookup(NotAnIdType.class, 1));
	}

	@Test
	void forId_shouldRejectDuplicateDbStateIds() {
		assertThrows(IllegalStateException.class, () -> IdTypeLookup.forId(DuplicateIdType.class, 1));
	}

	@Test
	void forId_shouldRejectNonPositiveDbStateIds() {
		assertThrows(IllegalStateException.class, () -> IdTypeLookup.forId(ZeroIdType.class, 1));
	}

	@Test
	void forId_shouldResolveAllConstantsOfApplicationEnums() throws Exception {
		Path classesRoot = Path.of(IdType.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		List<Class<?>> idTypeEnums;
		// All IdType enums live below the db package; scanning all application classes would be much slower.
		try (Stream<Path> paths = Files.walk(classesRoot.resolve(Path.of("de", "zft2", "gbanking", "db")))) {
			idTypeEnums = paths.map(path -> classesRoot.relativize(path).toString())
					.filter(fileName -> fileName.endsWith(".class") && !fileName.endsWith("package-info.class"))
					.<Class<?>>map(fileName -> loadClass(fileName))
					.filter(type -> type.isEnum() && IdType.class.isAssignableFrom(type))
					.toList();
		}

		assertFalse(idTypeEnums.isEmpty());
		for (Class<?> type : idTypeEnums) {
			for (Object constant : type.getEnumConstants()) {
				assertSame(constant, lookup(type, ((IdType) constant).getDbStateId()), type.getName());
			}
		}
	}

	private static Class<?> loadClass(String fileName) {
		String className = fileName.substring(0, fileName.length() - ".class".length()).replace(File.separatorChar, '.');
		try {
			return Class.forName(className, false, IdTypeLookupTest.class.getClassLoader());
		} catch (ClassNotFoundException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object lookup(Class<?> type, int id) {
		return IdTypeLookup.forId((Class) type, id);
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

	private enum ZeroIdType implements IdType {
		ZERO;

		@Override
		public int getDbStateId() {
			return 0;
		}
	}

	private enum NotAnIdType {
		VALUE
	}
}
