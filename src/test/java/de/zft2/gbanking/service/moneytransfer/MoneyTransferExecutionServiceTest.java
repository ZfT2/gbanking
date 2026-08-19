package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.db.TestData;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.BankingCapabilityService;
import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.ServiceStubbingUtil;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferExecutionServiceTest {

	private Path tempDir;

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(GBankingService.class, BankAccessService.class,
			BankingCapabilityService.class);

	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
	}

	@BeforeEach
	void setup() throws Exception {
		clearDatabase();
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
	}

	private void clearDatabase() {
		DBControllerTestUtil.clearAllTables(DBController.getConnection());
	}

	@AfterAll
	void cleanupDatabase() throws Exception {
		DBControllerTestUtil.closeAndNullifyConnection();
		DBControllerTestUtil.deleteTemporaryDir(tempDir);
	}

	@AfterEach
	void tearDown() throws Exception {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@Test
	void shouldRejectUnsupportedOrderTypeAndPersistErrorStatus() {
		MoneyTransferExecutionService service = ServiceRegistry.getService(MoneyTransferExecutionService.class);
		BankingCapabilityService bankingCapabilityService = ServiceRegistry.getService(BankingCapabilityService.class);

		BankAccount bankAccount = insertBankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA")));
		when(bankingCapabilityService.supportsTransferOrderType(any(BankAccount.class), eq(OrderType.STANDING_ORDER))).thenReturn(false);

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER, bankAccount.getId());

		boolean result = service.executeTransfer(moneyTransfer, bankAccount, "1234".toCharArray());

		assertFalse(result);
		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());

	}

	@Test
	void shouldPersistErrorWhenBankAccessInitializationFails() {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);
		BankingCapabilityService bankingCapabilityService = ServiceRegistry.getService(BankingCapabilityService.class);
		MoneyTransferExecutionService service = ServiceRegistry.getService(MoneyTransferExecutionService.class);
		BankAccount bankAccount = insertBankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA")));

		when(bankingCapabilityService.supportsTransferOrderType(any(BankAccount.class), eq(OrderType.TRANSFER))).thenReturn(true);
		when(hbciSupport.initBankAccess(any(BankAccount.class), isNull())).thenReturn(null);

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER, bankAccount.getId());

		boolean result = service.executeTransfer(moneyTransfer, bankAccount, null);

		assertFalse(result);
		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());

	}

	@Test
	void supportsTransferOrderTypeShouldTrimAndIgnoreCaseBusinessCases() {
		ServiceRegistry.setService(BankingCapabilityService.class, new BankingCapabilityService());
		MoneyTransferExecutionService service = ServiceRegistry.getService(MoneyTransferExecutionService.class);

		BankAccess bankAccess = insertBankAccessWithBpd("HKCDE", "HKIPZ");
		BankAccount bankAccount = new BankAccount();
		bankAccount.setBankAccessId(bankAccess.getId());
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("  hkcde "), createBusinessCase(" instuebsepa ")));

		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.STANDING_ORDER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
	}

	private BankAccess insertBankAccessWithBpd(String... businessCases) {
		DBController dbController = DBController.getInstance(tempDir.toString());
		BankAccess bankAccess = dbController.insertOrUpdate(TestData.createSampleBankAccess("10020030"));
		bankAccess.setBpd(TestData.buildCapabilityBPD(businessCases));
		dbController.insertOrUpdatePD(bankAccess);
		return bankAccess;
	}

	private BankAccount insertBankAccount() {
		return DBController.getInstance(tempDir.toString()).insertOrUpdate(TestData.createSampleAccount(null));
	}

	private MoneyTransfer createMoneyTransfer(OrderType orderType, int accountId) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(accountId);
		moneyTransfer.setOrderType(orderType);
		moneyTransfer.setAmount(BigDecimal.valueOf(12.34));
		moneyTransfer.setPurpose("Test");
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.NEW);
		Recipient recipient = new Recipient();
		recipient.setName("Empfänger");
		recipient.setIban("DE12345678901234567890");
		recipient.setBic("TESTDEFFXXX");
		recipient.setSource(Source.MANUELL);
		recipient = DBController.getInstance(tempDir.toString()).insertOrUpdate(recipient);
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setRecipientId(recipient.getId());
		if (orderType == OrderType.STANDING_ORDER) {
			moneyTransfer.setExecutionDate(LocalDate.now());
			moneyTransfer.setExecutionDay(1);
			moneyTransfer.setStandingorderMode(StandingorderMode.MONTHLY);
		}
		return moneyTransfer;
	}

	private static BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}
}
