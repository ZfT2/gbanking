package de.zft2.gbanking.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionComparatorTest {

	@Test
	void isNewer_shouldCompareSemanticVersionNumbers() {
		assertTrue(VersionComparator.isNewer("v0.5.2", "0.5.1"));
		assertTrue(VersionComparator.isNewer("1.0.0", "0.9.9"));
		assertFalse(VersionComparator.isNewer("0.5.1", "0.5.2"));
		assertFalse(VersionComparator.isNewer("0.5.1", "0.5.1"));
	}

	@Test
	void isNewer_shouldTreatReleaseAsNewerThanSameSnapshot() {
		assertTrue(VersionComparator.isNewer("0.5.1", "0.5.1-SNAPSHOT"));
		assertFalse(VersionComparator.isNewer("0.5.1-SNAPSHOT", "0.5.1"));
	}
}
