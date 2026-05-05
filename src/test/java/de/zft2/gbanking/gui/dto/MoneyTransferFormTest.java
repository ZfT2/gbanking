package de.zft2.gbanking.gui.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.StandingorderMode;

class MoneyTransferFormTest {

	@Test
	void defaultConstructor_shouldCreateRegularTransferForToday() {
		BankAccount account = new BankAccount();
		LocalDate before = LocalDate.now();

		MoneyTransferForm form = new MoneyTransferForm(account, "Max Mustermann", "DE123", "BIC", "Bank", new BigDecimal("12.34"), "Rechnung");

		assertSame(account, form.getBankAccount());
		assertEquals(OrderType.TRANSFER, form.getOrderType());
		assertEquals("Max Mustermann", form.getRecipientName());
		assertEquals(new BigDecimal("12.34"), form.getAmount());
		assertFalse(form.getExecutionDate().isBefore(before));
		assertFalse(form.getExecutionDate().isAfter(LocalDate.now()));
	}

	@Test
	void fullConstructor_shouldKeepScheduledAndStandingOrderData() {
		BankAccount account = new BankAccount();
		LocalDate executionDate = LocalDate.of(2026, 5, 10);

		MoneyTransferForm form = new MoneyTransferForm(account, OrderType.STANDING_ORDER, "Erika Mustermann", "DE456", "BIC2", "Bank 2",
				new BigDecimal("99.99"), "Miete", executionDate, 15, StandingorderMode.MONTHLY);

		assertSame(account, form.getBankAccount());
		assertEquals(OrderType.STANDING_ORDER, form.getOrderType());
		assertEquals("Erika Mustermann", form.getRecipientName());
		assertEquals("DE456", form.getIban());
		assertEquals("BIC2", form.getBic());
		assertEquals("Bank 2", form.getBank());
		assertEquals(new BigDecimal("99.99"), form.getAmount());
		assertEquals("Miete", form.getPurpose());
		assertEquals(executionDate, form.getExecutionDate());
		assertEquals(15, form.getExecutionDay());
		assertEquals(StandingorderMode.MONTHLY, form.getStandingorderMode());
	}
}
