package de.zft2.gbanking.service.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.service.Service;
import de.zft2.gbanking.service.ServiceStubbingUtil;

class MoneyTransferServiceTest {

	private static final List<Class<? extends Service>> SERVICES_TO_STUB = List.of(MoneyTransferExecutionService.class,
			MoneyTransferInventoryService.class);

	@BeforeEach
	void setUp() {
		ServiceStubbingUtil.initStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@AfterEach
	void tearDown() {
		ServiceStubbingUtil.unloadStubbedServicesInContext(SERVICES_TO_STUB);
	}

	@Test
	void getSenderAccountShouldMatchAccountNumberWhenHBCIAccountDoesNotContainIban() {
		HBCIPassport passport = mock(HBCIPassport.class);
		Konto konto = new Konto();
		konto.number = "123456789";
		konto.blz = "10000000";
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });

		BankAccount bankAccount = new BankAccount();
		bankAccount.setIban("DE00100000000012345678");
		bankAccount.setNumber("123456789");

		Konto senderAccount = new MoneyTransferService().getSenderAccount(passport, bankAccount);

		assertSame(konto, senderAccount);
	}

	@Test
	void getSenderAccountShouldRejectAccountWithSameNumberButDifferentIban() {
		HBCIPassport passport = mock(HBCIPassport.class);
		Konto konto = new Konto();
		konto.iban = "DE02120300000000202051";
		konto.number = "123456789";
		when(passport.getAccounts()).thenReturn(new Konto[] { konto });

		BankAccount bankAccount = new BankAccount();
		bankAccount.setIban("DE00100000000012345678");
		bankAccount.setNumber("123456789");
		MoneyTransferService service = new MoneyTransferService();

		GBankingException exception = assertThrows(GBankingException.class, () -> service.getSenderAccount(passport, bankAccount));

		assertTrue(exception.getMessage().contains(SensitiveDataMasker.maskIban(bankAccount.getIban())), exception.getMessage());
	}
}
