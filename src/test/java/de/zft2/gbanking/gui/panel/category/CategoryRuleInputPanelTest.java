package de.zft2.gbanking.gui.panel.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.Recipient;

class CategoryRuleInputPanelTest {

	@Test
	void createRuleTemplateShouldPrefillBookingDataWithAndCombination() {
		BankAccount account = new BankAccount();
		Category category = new Category("Versicherungen");
		Recipient recipient = new Recipient("Max Mustermann", "DE02120300000000202051");
		recipient.setAccountNumber("202051");
		Booking booking = new Booking();
		booking.setDateBooking(LocalDate.of(2026, 9, 17));
		booking.setAmount(new BigDecimal("-123.45"));
		booking.setPurpose("Monatlicher Beitrag");
		booking.setCategory(category);
		booking.setRecipient(recipient);

		CategoryRule template = CategoryRuleInputPanel.createRuleTemplate(account, booking);

		assertEquals(CategoryRule.JoinType.AND, template.getJoinType());
		assertEquals(List.of(account), template.getBankAccountList());
		assertSame(category, template.getCategory());
		assertEquals(booking.getDateBooking(), template.getFilterDateFrom());
		assertEquals(booking.getDateBooking(), template.getFilterDateTo());
		assertEquals(booking.getAmount(), template.getFilterAmountFrom());
		assertEquals(booking.getAmount(), template.getFilterAmountTo());
		assertEquals(recipient.getName(), template.getFilterRecipientName());
		assertEquals(recipient.getIban(), template.getFilterRecipientIban());
		assertEquals(recipient.getAccountNumber(), template.getFilterRecipientAccountNumber());
		assertEquals(booking.getPurpose(), template.getFilterPurpose());
	}
}
