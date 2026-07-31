package de.zft2.gbanking.gui.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.time.Month;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;

class MoneyTransferFormTest {

	@Test
	void defaultConstructor_shouldCreateRegularTransferForToday() {
		BankAccount account = new BankAccount();
		LocalDate before = LocalDate.now(ZoneId.systemDefault());

		Recipient recipient = new Recipient("Max Mustermann", "DE123", "BIC", null, null, "Bank", Source.ONLINE);
		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.TRANSFER, recipient, new BigDecimal("12.34"), "Rechnung");

		assertSame(account, form.getBankAccount());
		assertEquals(OrderType.TRANSFER, form.getOrderType());
		assertEquals("Max Mustermann", form.getRecipientName());
		assertEquals(new BigDecimal("12.34"), form.getAmount());
		assertFalse(form.getExecutionDate().isBefore(before));
		assertFalse(form.getExecutionDate().isAfter(LocalDate.now(ZoneId.systemDefault())));
	}

	@Test
	void fullConstructor_shouldKeepScheduledAndStandingOrderData() {
		BankAccount account = new BankAccount();
		LocalDate executionDate = LocalDate.of(2026, Month.MAY, 10);

		Recipient recipient = new Recipient("Erika Mustermann", "DE456", "BIC2", null, null, "Bank 2", Source.ONLINE);
		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.STANDING_ORDER, recipient, new BigDecimal("99.99"), "Miete", executionDate);
		form.setStandingorderInfo(15, StandingorderMode.MONTHLY);

		assertSame(account, form.getBankAccount());
		assertEquals(OrderType.STANDING_ORDER, form.getOrderType());
		assertEquals("Erika Mustermann", form.getRecipientName());
		assertEquals("DE456", form.getIban());
		assertEquals("BIC2", form.getBic());
		assertEquals("Bank 2", form.getBank());
		assertEquals(new BigDecimal("99.99"), form.getAmount());
		assertEquals("EUR", form.getCurrency());
		assertEquals("Miete", form.getPurpose());
		assertEquals(executionDate, form.getExecutionDate());
		assertEquals(15, form.getExecutionDay());
		assertEquals(StandingorderMode.MONTHLY, form.getStandingorderMode());
	}

	@Test
	void extendedConstructor_shouldKeepForeignTransferCurrency() {
		Recipient foreignRecipient = new Recipient("Foreign Recipient", "GB29NWBK60161331926819", null, null, null, "Recipient Bank", Source.ONLINE);
		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setCurrency("GBP");
		foreignTransfer.setRecipientCountry("GB");
		MoneyTransferForm form = new MoneyTransferForm(new BankAccount(), OrderType.FOREIGN_TRANSFER, foreignRecipient, new BigDecimal("19.95"), "Invoice",
				LocalDate.now(ZoneId.systemDefault()), foreignTransfer);

		assertEquals("GBP", form.getCurrency());
	}

	@Test
	void extendedConstructor_shouldKeepForeignTransferDetails() {
		Recipient foreignRecipient = new Recipient("Foreign Recipient", "GB29NWBK60161331926819", null, null, null, "Recipient Bank", Source.ONLINE);
		MoneyTransferForeign foreignTransfer = new MoneyTransferForeign();
		foreignTransfer.setRecipientCountry("GB");
		foreignTransfer.setCurrency("GBP");

		MoneyTransferForm form = new MoneyTransferForm(new BankAccount(), OrderType.FOREIGN_TRANSFER, foreignRecipient, new BigDecimal("19.95"), "Invoice",
				LocalDate.now(ZoneId.systemDefault()), foreignTransfer);

		assertSame(foreignTransfer, form.getForeignTransfer());
		assertEquals("GBP", form.getCurrency());
		assertEquals("GB", form.getForeignTransfer().getRecipientCountry());
	}
}
