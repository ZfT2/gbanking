package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.ImportHistory;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.exception.GBankingException;

class DBControllerInstituteBulkTest extends DBControllerIntegrationBaseTest {

	@Test
	void insertAllShouldAssignIdsPersistDetailsWithOneBatchIdLookup() {
		ImportHistory importHistory = db.insertOrUpdate(new ImportHistory("institute-bulk.csv"));
		Institute first = createInstitute(importHistory.getId(), "10000001", "FIRSTDE1XXX", "First Bank");
		Institute second = createInstitute(importHistory.getId(), "10000002", "SECOND2XXX", "Second Bank");
		Set<Institute> institutes = new LinkedHashSet<>(List.of(first, second));

		DatabaseQueryCounter.Measurement<Set<Institute>> measurement =
				DatabaseQueryCounter.measure(() -> db.insertAll(institutes));

		assertEquals(1, measurement.queryCount());
		assertTrue(first.getId() > 0);
		assertTrue(second.getId() > 0);
		assertNotEquals(first.getId(), second.getId());
		assertInstituteDetails(db.getById(Institute.class, first.getId()), "10000001", "First Bank", "DC-10000001",
				"DBB-10000001", "Address 10000001");
		assertInstituteDetails(db.getById(Institute.class, second.getId()), "10000002", "Second Bank", "DC-10000002",
				"DBB-10000002", "Address 10000002");

		first.setServiceScc(0);
		first.setAdditionalIbanRule("UPDATED-10000001");
		second.setServiceSct(null);
		second.setServiceCor(null);
		second.setServiceCor1(null);
		second.setServiceB2b(null);
		second.setServiceScc(null);
		second.setAdditionalBankNameShort(null);
		second.setAdditionalIbanRule(null);
		db.insertAll(institutes);

		Institute updatedFirst = db.getById(Institute.class, first.getId());
		assertEquals(0, updatedFirst.getServiceScc());
		assertEquals("UPDATED-10000001", updatedFirst.getAdditionalIbanRule());
		Institute secondWithoutReachableDetails = db.getById(Institute.class, second.getId());
		assertNull(secondWithoutReachableDetails.getServiceSct());
		assertNull(secondWithoutReachableDetails.getServiceCor());
		assertNull(secondWithoutReachableDetails.getServiceCor1());
		assertNull(secondWithoutReachableDetails.getServiceB2b());
		assertNull(secondWithoutReachableDetails.getServiceScc());
		assertNull(secondWithoutReachableDetails.getAdditionalBankNameShort());
		assertNull(secondWithoutReachableDetails.getAdditionalIbanRule());
	}

	@Test
	void insertAllShouldRollbackRowsAndIdsWhenADetailBatchFails() {
		ImportHistory importHistory = db.insertOrUpdate(new ImportHistory("institute-rollback.csv"));
		Institute first = createInstitute(importHistory.getId(), "10000003", "FIRSTDE3XXX", "First Bank");
		Institute duplicate = createInstitute(importHistory.getId(), "10000003", "SECOND3XXXX", "Duplicate Bank");
		Set<Institute> institutes = new LinkedHashSet<>(List.of(first, duplicate));

		assertThrows(GBankingException.class, () -> db.insertAll(institutes));

		assertEquals(0, first.getId());
		assertEquals(0, duplicate.getId());
		assertTrue(db.getAll(Institute.class).isEmpty());
	}

	@Test
	void singleUpdateShouldUpsertPreviouslyMissingDetailRows() {
		ImportHistory importHistory = db.insertOrUpdate(new ImportHistory("institute-single.csv"));
		Institute institute = createCoreInstitute(importHistory.getId(), "10000004", "SINGLE4XXXXX", "Single Bank");
		db.insertOrUpdate(institute);

		institute.setHbciDns("hbci.single.example");
		institute.setDatasetNumber("DBB-10000004");
		institute.setFeature(7);
		institute.setCountry("DE");
		institute.setServiceSct(1);
		institute.setServiceCor(0);
		institute.setServiceCor1(1);
		institute.setServiceB2b(0);
		institute.setServiceScc(1);
		institute.setAdditionalBankNameShort("Single");
		institute.setAdditionalIbanRule("SINGLE-RULE");
		db.insertOrUpdate(institute);

		Institute reloaded = db.getById(Institute.class, institute.getId());
		assertEquals("hbci.single.example", reloaded.getHbciDns());
		assertEquals("DBB-10000004", reloaded.getDatasetNumber());
		assertEquals(7, reloaded.getFeature());
		assertEquals("DE", reloaded.getCountry());
		assertEquals("Single", reloaded.getAdditionalBankNameShort());
		assertEquals("SINGLE-RULE", reloaded.getAdditionalIbanRule());
		assertReachableDetails(reloaded);
	}

	@Test
	void insertAllShouldRejectUnknownUpdateIds() {
		ImportHistory importHistory = db.insertOrUpdate(new ImportHistory("institute-missing.csv"));
		Institute institute = createInstitute(importHistory.getId(), "10000005", "MISSING5XXXX", "Missing Bank");
		institute.setId(Integer.MAX_VALUE);

		assertThrows(GBankingException.class, () -> db.insertAll(Set.of(institute)));

		assertTrue(db.getAll(Institute.class).isEmpty());
	}

	@Test
	void importHistoryWriteShouldRestoreMissingLookupIndex() throws Exception {
		Path databaseDirectory = DbConnectionHandler.getSession().databaseFile().getParent();
		try (Statement statement = DBController.getConnection().createStatement()) {
			statement.executeUpdate("DROP INDEX institute_db.idx_institute_blz_state");
		}
		assertFalse(instituteLookupIndexExists());
		DBController.resetConnection();
		db = DBController.getInstance(databaseDirectory.toString());
		assertFalse(instituteLookupIndexExists());

		db.insertOrUpdate(new ImportHistory("institute-index.csv"));

		assertTrue(instituteLookupIndexExists());
	}

	@Test
	void rolledBackIndexSetupShouldBeRetried() throws Exception {
		Path databaseDirectory = DbConnectionHandler.getSession().databaseFile().getParent();
		try (Statement statement = DBController.getConnection().createStatement()) {
			statement.executeUpdate("DROP INDEX institute_db.idx_institute_blz_state");
		}
		DBController.resetConnection();
		db = DBController.getInstance(databaseDirectory.toString());

		assertThrows(GBankingException.class, () -> DbTransactionManager.inTransaction(() -> {
			db.insertOrUpdate(new ImportHistory("rolled-back-index.csv"));
			assertTrue(assertDoesNotThrow(() -> instituteLookupIndexExists()));
			throw new GBankingException("Force rollback after index setup");
		}));
		assertFalse(instituteLookupIndexExists());

		db.insertOrUpdate(new ImportHistory("retried-index.csv"));

		assertTrue(instituteLookupIndexExists());
	}

	private static Institute createInstitute(int importHistoryId, String blz, String bic, String bankName) {
		Institute institute = createCoreInstitute(importHistoryId, blz, bic, bankName);

		institute.setImportNumber(1);
		institute.setDataCenter("DC-" + blz);
		institute.setHbciVersion(3.0);
		institute.setLastChanged(LocalDate.of(2026, 7, 29));

		institute.setDatasetNumber("DBB-" + blz);
		institute.setPostcode("10115");

		institute.setCountry("DE");
		institute.setAddress("Address " + blz);
		institute.setServiceSct(1);
		institute.setServiceCor(0);
		institute.setServiceCor1(1);
		institute.setServiceB2b(0);
		institute.setServiceScc(1);
		institute.setAdditionalBankNameShort("Additional " + bankName);
		institute.setAdditionalIbanRule("IBAN-" + blz);
		return institute;
	}

	private static Institute createCoreInstitute(int importHistoryId, String blz, String bic, String bankName) {
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBic(bic);
		institute.setBankName(bankName);
		institute.setPlace("Berlin");
		institute.setStateType(InstituteStatus.ACTIVE);
		institute.setImportFile(importHistoryId);
		return institute;
	}

	private static void assertInstituteDetails(Institute institute, String blz, String bankName, String dataCenter,
			String datasetNumber, String address) {
		assertEquals(bankName, institute.getBankName());
		assertEquals(dataCenter, institute.getDataCenter());
		assertEquals(datasetNumber, institute.getDatasetNumber());
		assertEquals(address, institute.getAddress());
		assertEquals("Additional " + bankName, institute.getAdditionalBankNameShort());
		assertEquals("IBAN-" + blz, institute.getAdditionalIbanRule());
		assertEquals(LocalDate.of(2026, 7, 29), institute.getLastChanged());
		assertReachableDetails(institute);
	}

	private static void assertReachableDetails(Institute institute) {
		assertEquals(1, institute.getServiceSct());
		assertEquals(0, institute.getServiceCor());
		assertEquals(1, institute.getServiceCor1());
		assertEquals(0, institute.getServiceB2b());
		assertEquals(1, institute.getServiceScc());
	}

	private static boolean instituteLookupIndexExists() throws SQLException {
		try (Statement statement = DBController.getConnection().createStatement();
				var resultSet = statement.executeQuery("SELECT 1 FROM institute_db.sqlite_master "
						+ "WHERE type = 'index' AND name = 'idx_institute_blz_state'")) {
			return resultSet.next();
		}
	}
}
