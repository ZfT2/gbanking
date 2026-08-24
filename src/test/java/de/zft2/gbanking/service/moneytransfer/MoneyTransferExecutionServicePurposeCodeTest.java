package de.zft2.gbanking.service.moneytransfer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.DBControllerTestUtil;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.ServiceRegistry;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferExecutionServicePurposeCodeTest {

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
	void createTransferJob_shouldForwardPurposeCodeToHbciJob() throws Exception {
		BankAccessService hbciSupport = ServiceRegistry.getService(BankAccessService.class);

		MoneyTransferExecutionService service = new MoneyTransferExecutionService();
		HBCIHandler handle = mock(HBCIHandler.class);
		@SuppressWarnings("unchecked")
		HBCIJob<HBCIJobResult> job = mock(HBCIJob.class);
		when(hbciSupport.newHbciJob(handle, "UebSEPA")).thenReturn(job);
		MoneyTransfer moneyTransfer = createMoneyTransfer();

		invokePrivate(service, "createTransferJob", new Class<?>[] { HBCIHandler.class, MoneyTransfer.class, Konto.class, Konto.class }, handle, moneyTransfer,
				new Konto(), new Konto());

		verify(job).setParam("usage", "Rechnung 4711");
		verify(job).setParam("purposecode", "GDDS");
		verify(job).setParam("endtoendid", "E2E-4711");
	}

	private static MoneyTransfer createMoneyTransfer() {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setOrderType(OrderType.TRANSFER);
		moneyTransfer.setAmount(new BigDecimal("12.34"));
		moneyTransfer.setPurpose("Rechnung 4711");
		moneyTransfer.setPurposeCode("GDDS");
		moneyTransfer.setEndToEndId("E2E-4711");
		return moneyTransfer;
	}

	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
		Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Exception exception) {
				throw exception;
			}
			throw e;
		}
	}
}
