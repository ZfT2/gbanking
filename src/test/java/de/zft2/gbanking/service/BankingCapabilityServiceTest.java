package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.BankCapabilityCache;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.DatabaseQueryCounter;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.testdata.TestDataFactory;

/**
 * Regression tests for the capability lookups of the {@link BankingCapabilityService}.
 * <p>
 * The user interface asks for the supported banking operations of an account for nearly every
 * interaction. Therefore a capability lookup must neither load the bookings of the account nor
 * read the parameter data of the bank access more than once.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankingCapabilityServiceTest {

	private static final String TRANSFER_CODE = "UEBSEPA";
	private static final String BOOKING_STATEMENT_FRAGMENT = "bookingFull";
	private static final String BPD_STATEMENT_FRAGMENT = "FROM BPD";
	private static final String UPD_STATEMENT_FRAGMENT = "FROM UPD";
	private static final int BOOKING_COUNT = 20;

	private DBController dbController;
	private Path tempDir;
	private BankingCapabilityService capabilityService;

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		dbController = DBController.getInstance(tempDir.toString());
		capabilityService = ServiceRegistry.getService(BankingCapabilityService.class);
	}

	@BeforeEach
	void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
		// The tables are cleared without using the write paths of the controller, therefore the
		// cached capability data has to be cleared as well.
		BankCapabilityCache.clear();
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@Test
	void capabilityCheck_shouldNotLoadTheBookingsOfTheAccount() {
		BankAccount account = createAccountWithTransferSupport();

		DatabaseQueryCounter.Measurement<Boolean> measurement = DatabaseQueryCounter
				.measure(() -> capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		assertTrue(measurement.result());
		assertFalse(measurement.executedStatementContaining(BOOKING_STATEMENT_FRAGMENT),
				"The bookings of the account must not be loaded for a capability check");
	}

	@Test
	void capabilityCheck_shouldReadTheParameterDataOnlyOnce() {
		BankAccount account = createAccountWithTransferSupport();

		DatabaseQueryCounter.Measurement<Boolean> measurement = DatabaseQueryCounter
				.measure(() -> capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		assertTrue(measurement.result());
		assertEquals(1, measurement.countOfStatementsContaining(BPD_STATEMENT_FRAGMENT));
		assertEquals(1, measurement.countOfStatementsContaining(UPD_STATEMENT_FRAGMENT));
	}

	@Test
	void repeatedCapabilityChecks_shouldNotQueryTheDatabase() {
		BankAccount account = createAccountWithTransferSupport();
		capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER);

		DatabaseQueryCounter.Measurement<Boolean> measurement = DatabaseQueryCounter
				.measure(() -> capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		assertTrue(measurement.result());
		assertEquals(0, measurement.queryCount());
	}

	@Test
	void capabilityCheck_shouldReflectChangedParameterData() {
		BankAccess bankAccess = createBankAccessWithTransferSupport();
		BankAccount account = createAccount(bankAccess);
		linkBusinessCase(account, TRANSFER_CODE);

		assertTrue(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		// The bank does not support the transfer any more.
		Properties bpd = new Properties();
		bpd.setProperty("Params_1.Allgemein", "KONTOAUSZUG");
		bankAccess.getFints().setBpd(bpd);
		dbController.insertOrUpdatePD(bankAccess);

		assertFalse(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));
	}

	@Test
	void capabilityCheck_shouldReflectNewBusinessCases() {
		BankAccess bankAccess = createBankAccessWithTransferSupport();
		BankAccount account = createAccount(bankAccess);

		assertFalse(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		linkBusinessCase(account, TRANSFER_CODE);

		assertTrue(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));
	}

	@Test
	void capabilityCheck_shouldReflectDeactivatedAccount() {
		BankAccount account = createAccountWithTransferSupport();

		assertTrue(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));

		account.setAccountState(AccountState.INACTIVE);
		dbController.insertOrUpdate(account);

		assertFalse(capabilityService.supportsTransferOrderType(account, OrderType.TRANSFER));
	}

	private BankAccess createBankAccessWithTransferSupport() {
		BankAccess bankAccess = dbController.insertOrUpdate(TestDataFactory.createSampleBankAccess("88888888"));
		Properties bpd = new Properties();
		bpd.setProperty("Params_1.Allgemein", TRANSFER_CODE);
		bankAccess.getFints().setBpd(bpd);
		dbController.insertOrUpdatePD(bankAccess);
		return bankAccess;
	}

	private BankAccount createAccount(BankAccess bankAccess) {
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(bankAccess.getId()));
		for (int index = 0; index < BOOKING_COUNT; index++) {
			dbController.insertOrUpdate(TestDataFactory.createSampleBooking(account.getId()));
		}
		return account;
	}

	private BankAccount createAccountWithTransferSupport() {
		BankAccess bankAccess = createBankAccessWithTransferSupport();
		BankAccount account = createAccount(bankAccess);
		linkBusinessCase(account, TRANSFER_CODE);
		return account;
	}

	private void linkBusinessCase(BankAccount account, String caseValue) {
		account.setAllowedBusinessCases(List.of(TestDataFactory.createBusinessCase(caseValue)));
		dbController.insertBusinessCases(account);
	}
}
