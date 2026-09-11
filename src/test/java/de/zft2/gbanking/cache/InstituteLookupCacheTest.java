package de.zft2.gbanking.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;

class InstituteLookupCacheTest {

	private DBController db;
	private Path tempDir;
	private int importHistoryId;

	@BeforeEach
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		db = DBController.getInstance(tempDir.toString());
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		importHistoryId = db.insertOrUpdate(new ImportHistory("lookup-cache-test.csv")).getId();
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
	void getEntriesForBlz_shouldUseOnlyTheActiveViewEntry() {
		Institute duplicate = createInstitute("50010517", "Bank B", "BICBDEFFXXX", 2);
		duplicate.setStateType(InstituteStatus.DUPLICATE);
		db.insertOrUpdate(duplicate);
		db.insertOrUpdate(createInstitute("50010517", "Bank A", "BICADEFFXXX", 1));
		Institute archived = createInstitute("50010517", "Old Bank", "BICBOTHERXXX", 3);
		archived.setStateType(InstituteStatus.ARCHIVED);
		db.insertOrUpdate(archived);

		List<InstituteLookupCache.InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBlz("50010517");

		assertEquals(1, entries.size());
		assertEquals("Bank A", entries.get(0).bankName());
		assertEquals("BICADEFFXXX", entries.get(0).bic());
		assertTrue(InstituteLookupCache.getEntriesForBic("BICBDEFFXXX").isEmpty());
	}

	@Test
	void getEntriesForBlz_shouldReturnCachedDataUntilCacheIsCleared() {
		Institute bank = db.insertOrUpdate(createInstitute("50010517", "Bank A", "BICADEFFXXX", 1));
		List<InstituteLookupCache.InstituteLookupEntry> initialEntries = InstituteLookupCache.getEntriesForBlz("50010517");

		bank.setBankName("Bank B");
		bank.setBic("BICBDEFFXXX");
		db.insertOrUpdate(bank);

		List<InstituteLookupCache.InstituteLookupEntry> cachedEntries = InstituteLookupCache.getEntriesForBlz("50010517");
		assertEquals(initialEntries, cachedEntries);
		assertTrue(InstituteLookupCache.getEntriesForBic("BICBDEFFXXX").isEmpty());

		InstituteLookupCache.clear();
		List<InstituteLookupCache.InstituteLookupEntry> refreshedEntries = InstituteLookupCache.getEntriesForBlz("50010517");
		assertEquals(1, refreshedEntries.size());
		assertEquals("Bank B", refreshedEntries.get(0).bankName());
		assertEquals(refreshedEntries, InstituteLookupCache.getEntriesForBic("BICBDEFFXXX"));
	}

	@Test
	void getEntriesForBic_shouldNormalizeSortAndDeduplicateBankNames() {
		db.insertOrUpdate(createInstitute("50010517", "Bank B", "BICBDEFFXXX", 2));
		db.insertOrUpdate(createInstitute("50010518", "Bank A", "BICBDEFFXXX", 1));
		db.insertOrUpdate(createInstitute("50010519", "Bank B", "BICBDEFFXXX", 3));

		List<InstituteLookupCache.InstituteLookupEntry> entries = InstituteLookupCache.getEntriesForBic(" bicb deff xxx ");

		assertEquals(2, entries.size());
		assertEquals("Bank A", entries.get(0).bankName());
		assertEquals("BICBDEFFXXX", entries.get(0).bic());
		assertEquals("Bank B", entries.get(1).bankName());
		assertEquals("BICBDEFFXXX", entries.get(1).bic());
		assertTrue(InstituteLookupCache.getEntriesForBic(" ").isEmpty());
	}

	@Test
	void findBankNameForBankData_shouldTreatBic8AndPrimaryOfficeBic11AsEquivalent() {
		db.insertOrUpdate(createInstitute("11111111", "Bank with BIC8", "CHASLULX", 1));
		db.insertOrUpdate(createInstitute("22222222", "Bank with BIC11", "BICADEFFXXX", 1));

		assertEquals("Bank with BIC8", InstituteLookupCache.findBankNameForBankData("CHASLULXXXX", null).orElseThrow());
		assertEquals("Bank with BIC11", InstituteLookupCache.findBankNameForBankData("BICADEFF", null).orElseThrow());
	}

	@Test
	void getEntriesForBic_shouldKeepSourcePriority() {
		Institute dbb = createInstitute("20000000", "DBB Bank", "BICADEFFXXX", 0);
		dbb.setLastChanged(null);
		dbb.setDatasetNumber("000001");
		db.insertOrUpdate(dbb);
		db.insertOrUpdate(createInstitute("10000000", "DK Bank", "BICADEFFXXX", 20));

		assertEquals(List.of("DK Bank", "DBB Bank"), InstituteLookupCache.getEntriesForBic("BICADEFF").stream()
				.map(entry -> entry.bankName()).toList());
		assertEquals("DBB Bank", InstituteLookupCache.findBankNameForBankData(null, "20000000").orElseThrow());
	}

	@Test
	void getEntriesForBic_shouldKeepSpecificBranchCodesDistinct() {
		db.insertOrUpdate(createInstitute("33333333", "Head office", "CHASLULX", 1));
		db.insertOrUpdate(createInstitute("44444444", "Specific branch", "CHASLULX123", 1));

		assertEquals("Specific branch", InstituteLookupCache.getEntriesForBic("CHASLULX123").get(0).bankName());
		assertTrue(InstituteLookupCache.getEntriesForBic("CHASLULX999").isEmpty());
	}

	@Test
	void getEntriesForBankCode_shouldResolveBlzAndBic() {
		db.insertOrUpdate(createInstitute("50010517", "Bank A", "BICADEFFXXX", 1));

		assertEquals("Bank A", InstituteLookupCache.getEntriesForBankCode("50010517").get(0).bankName());
		assertEquals("Bank A", InstituteLookupCache.getEntriesForBankCode(" bica deff xxx ").get(0).bankName());
		assertTrue(InstituteLookupCache.getEntriesForBankCode("invalid").isEmpty());
	}

	@Test
	void findBankNameForBankData_shouldPreferBlzOverBic() {
		db.insertOrUpdate(createInstitute("50010517", "Bank by BLZ", "BICBDEFFXXX", 1));
		db.insertOrUpdate(createInstitute("50010518", "Bank by BIC", "BICADEFFXXX", 1));

		assertEquals("Bank by BLZ", InstituteLookupCache.findBankNameForBankData("BICADEFFXXX", "50010517").orElseThrow());
		assertEquals("Bank by BIC", InstituteLookupCache.findBankNameForBankData("BICADEFFXXX", "00000000").orElseThrow());
		assertEquals("Bank by BIC", InstituteLookupCache.findBankNameForBankData("BICADEFFXXX", null).orElseThrow());
		assertTrue(InstituteLookupCache.findBankNameForBankData(null, null).isEmpty());
	}

	@Test
	void findBicForBlz_shouldNotMixDataFromDiscardedRows() {
		db.insertOrUpdate(createInstitute("50010517", "Bank without BIC", null, 1));
		db.insertOrUpdate(createInstitute("50010517", "Bank with BIC", "BICADEFFXXX", 2));

		assertTrue(InstituteLookupCache.findBicForBlz("50010517").isEmpty());
		assertTrue(InstituteLookupCache.findBicForBlz("00000000").isEmpty());
	}

	@Test
	void extractGermanBlzFromIban_shouldReturnExpectedBankCode() {
		assertEquals("50010517", InstituteLookupCache.extractGermanBlzFromIban("DE44 5001 0517 5407 3249 31"));
		assertEquals("50010517", InstituteLookupCache.extractGermanBlzFromIban("de44500105175407324931"));
		assertEquals("5407324931", InstituteLookupCache.extractGermanAccountNumberFromIban("DE44 5001 0517 5407 3249 31"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban("FR7630006000011234567890189"));
		assertNull(InstituteLookupCache.extractGermanAccountNumberFromIban("FR7630006000011234567890189"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban("DE12"));
		assertNull(InstituteLookupCache.extractGermanBlzFromIban(null));
		assertTrue(InstituteLookupCache.getEntriesForBlz(" ").isEmpty());
	}

	private Institute createInstitute(String blz, String bankName, String bic, int importNumber) {
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBankName(bankName);
		institute.setBic(bic);
		institute.setImportNumber(importNumber);
		institute.setLastChanged(LocalDate.of(2026, Month.APRIL, 10));
		institute.setImportFile(importHistoryId);
		institute.setStateType(InstituteStatus.ACTIVE);
		return institute;
	}
}
