package de.gbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.gbanking.db.DBController;
import de.gbanking.db.DBControllerTestUtil;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.BusinessCase;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferExecutionServiceTest {

	private Path tempDir;
	@BeforeAll
	void setupDatabase() throws Exception {
		tempDir = Files.createTempDirectory("gb_test_");
		DBController.getInstance(tempDir.toString());
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
	void shouldRejectUnsupportedOrderTypeAndPersistErrorStatus() {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);

		BankAccount bankAccount = new BankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA")));

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.STANDING_ORDER);

		boolean result = service.executeTransfer(moneyTransfer, bankAccount, "1234".toCharArray());

		assertFalse(result);
		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());

	}

	@Test
	void shouldPersistErrorWhenBankAccessInitializationFails() {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);
		BankAccount bankAccount = new BankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("UebSEPA")));

		when(hbciSupport.initBankAccess(bankAccount, null)).thenReturn(null);

		MoneyTransfer moneyTransfer = createMoneyTransfer(OrderType.TRANSFER);

		boolean result = service.executeTransfer(moneyTransfer, bankAccount, null);

		assertFalse(result);
		assertEquals(MoneyTransferStatus.ERROR, moneyTransfer.getMoneytransferStatus());

	}

	@Test
	void supportsTransferOrderTypeShouldTrimAndIgnoreCaseBusinessCases() {
		GBankingBean hbciSupport = mock(GBankingBean.class);
		MoneyTransferExecutionService service = new MoneyTransferExecutionService(hbciSupport);

		BankAccount bankAccount = new BankAccount();
		bankAccount.setAllowedBusinessCases(List.of(createBusinessCase("  hkdse "), createBusinessCase(" instuebsepa ")));

		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.STANDING_ORDER));
		assertTrue(service.supportsTransferOrderType(bankAccount, OrderType.REALTIME_TRANSFER));
		assertFalse(service.supportsTransferOrderType(bankAccount, OrderType.TRANSFER));
	}

	private static MoneyTransfer createMoneyTransfer(OrderType orderType) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setOrderType(orderType);
		moneyTransfer.setAmount(BigDecimal.valueOf(12.34));
		moneyTransfer.setPurpose("Test");
		Recipient recipient = new Recipient();
		recipient.setName("Empfänger");
		recipient.setIban("DE12345678901234567890");
		recipient.setBic("TESTDEFFXXX");
		moneyTransfer.setRecipient(recipient);
		return moneyTransfer;
	}

	private static BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}
}
