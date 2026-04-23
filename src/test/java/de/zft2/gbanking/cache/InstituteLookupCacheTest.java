package de.zft2.gbanking.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

class InstituteLookupCacheTest {

	private DBController db;
	private Path tempDir;

	@BeforeEach
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		db = DBController.getInstance(tempDir.toString());
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		InstituteLookupCache.clear();
	}

	@AfterEach
	void cleanupDatabase() throws Exception {
		InstituteLookupCache.clear();
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
		DbRuntimeContext.setCurrentDbDirectory(".");
	}

	@Test
	void getEntriesForBlz_shouldSortByImportNumberAndDeduplicateBankNames() {
		db.insertOrUpdate(createInstitute("50010517", "Bank B", "BICBDEFFXXX", 2));
		db.insertOrUpdate(createInstitute("50010517", "Bank A", "BICADEFFXXX", 1));
		db.insertOrUpdate(createInstitute("50010517", "Bank B", "BICBOTHERXXX", 3));

		List<InstituteLookupCache.InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBlz("50010517");

		assertEquals(2, entries.size());
		assertEquals("Bank A", entries.get(0).bankName());
		assertEquals("BICADEFFXXX", entries.get(0).bic());
		assertEquals(1, entries.get(0).importNumber());
		assertEquals("Bank B", entries.get(1).bankName());
		assertEquals("BICBDEFFXXX", entries.get(1).bic());
		assertEquals(2, entries.get(1).importNumber());
	}

	@Test
	void getEntriesForBlz_shouldReturnCachedDataUntilCacheIsCleared() {
		db.insertOrUpdate(createInstitute("50010517", "Bank A", "BICADEFFXXX", 1));
		List<InstituteLookupCache.InstituteLookupEntry> initialEntries = InstituteLookupCache.getEntriesForBlz("50010517");

		db.insertOrUpdate(createInstitute("50010517", "Bank B", "BICBDEFFXXX", 2));

		List<InstituteLookupCache.InstituteLookupEntry> cachedEntries = InstituteLookupCache.getEntriesForBlz("50010517");
		assertEquals(initialEntries, cachedEntries);

		InstituteLookupCache.clear();
		List<InstituteLookupCache.InstituteLookupEntry> refreshedEntries = InstituteLookupCache.getEntriesForBlz("50010517");
		assertEquals(2, refreshedEntries.size());
	}

	@Test
	void extractGermanBlzFromIban_shouldReturnExpectedBankCode() {
		assertEquals("50010517", InstituteLookupCache.extractGermanBlzFromIban("DE44 5001 0517 5407 3249 31"));
		assertEquals("50010517", InstituteLookupCache.extractGermanBlzFromIban("de44500105175407324931"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban("FR7630006000011234567890189"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban("DE12"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban(null));
		assertTrue(InstituteLookupCache.getEntriesForBlz(" ").isEmpty());
	}

	private static Institute createInstitute(String blz, String bankName, String bic, int importNumber) {
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBankName(bankName);
		institute.setBic(bic);
		institute.setImportNumber(importNumber);
		institute.setLastChanged(LocalDate.of(2026, 4, 10));
		institute.setStateType(InstituteStatus.ACTIVE);
		return institute;
	}
}
