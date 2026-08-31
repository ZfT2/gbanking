package org.kapott.hbci.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.testdata.TestDataFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HBCIPassportPinTanDBTest {

	private DBController dbController;
	private Path tempDir;

	@BeforeAll
	void setupDatabase() throws Exception {
		HBCIUtils.init(new Properties(), new HBCICallbackConsole());
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void read_shouldLoadPersistedSynchronizationData() {
		Properties bpd = new Properties();
		bpd.setProperty("BPA.version", "12");
		Properties upd = new Properties();
		upd.setProperty("UPA.version", "34");
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("12345678");
		bankAccess.getFints().setBpd(bpd);
		bankAccess.getFints().setUpd(upd);
		bankAccess = dbController.insertOrUpdate(bankAccess);
		dbController.insertOrUpdatePD(bankAccess);

		HBCIPassportPinTanDB passport = new HBCIPassportPinTanDB("12345678");
		try {
			assertEquals(bankAccess.getFints().getSysId(), passport.getSysId());
			assertEquals(bankAccess.getFints().getHbciVersion(), passport.getHBCIVersion());
			assertEquals("946", passport.getCurrentTANMethod(false));
			assertEquals(bankAccess.getFints().getAllowedTwostepMechanisms(), passport.getAllowedTwostepMechanisms());
			assertDoesNotThrow(() -> passport.getAllowedTwostepMechanisms().clear());
			assertEquals("12", passport.getBPD().getProperty("BPA.version"));
			assertEquals("34", passport.getUPD().getProperty("UPA.version"));
		} finally {
			passport.close();
		}
	}

	@Test
	void read_shouldIgnoreBlankPersistedSynchronizationValues() {
		Properties bpd = new Properties();
		bpd.setProperty("BPA.version", "12");
		bpd.setProperty("BPA.kiname", "");
		Properties upd = new Properties();
		upd.setProperty("UPA.version", "34");
		upd.setProperty("KInfo.customerid", "");
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("87654321");
		bankAccess.getFints().setBpd(bpd);
		bankAccess.getFints().setUpd(upd);
		bankAccess = dbController.insertOrUpdate(bankAccess);
		dbController.insertOrUpdatePD(bankAccess);

		HBCIPassportPinTanDB passport = new HBCIPassportPinTanDB("87654321");
		try {
			assertNull(passport.getBPD().getProperty("BPA.kiname"));
			assertNull(passport.getUPD().getProperty("KInfo.customerid"));
			assertEquals(bankAccess.getFints().getUserId(), passport.getCustomerId(0));
		} finally {
			passport.close();
		}
	}

	@Test
	void readAndSaveChanges_shouldNormalizeBlankPassportValues() {
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("11223344");
		bankAccess.getFints().setCustomerId(" ");
		bankAccess.getFints().setSysId("");
		bankAccess.getFints().setHbciVersion(" ");
		bankAccess.getFints().setAllowedTwostepMechanisms(List.of("", "946", " "));
		bankAccess = dbController.insertOrUpdate(bankAccess);

		HBCIPassportPinTanDB passport = new HBCIPassportPinTanDB("11223344");
		try {
			assertEquals(bankAccess.getFints().getUserId(), passport.getCustomerId());
			assertEquals("0", passport.getSysId());
			assertEquals(List.of("946"), passport.getAllowedTwostepMechanisms());

			passport.saveChanges();
			BankAccess storedBankAccess = dbController.getBankAccessByBlz("11223344");
			assertEquals(bankAccess.getFints().getUserId(), storedBankAccess.getFints().getCustomerId());
			assertEquals("0", storedBankAccess.getFints().getSysId());
			assertEquals(List.of("946"), storedBankAccess.getFints().getAllowedTwostepMechanisms());
			assertNull(storedBankAccess.getFints().getHbciVersion());
		} finally {
			passport.close();
		}
	}
}
