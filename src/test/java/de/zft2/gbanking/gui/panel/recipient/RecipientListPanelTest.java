package de.zft2.gbanking.gui.panel.recipient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Recipient;

class RecipientListPanelTest {

	@Test
	void recipientAccountIdentifier_shouldPreferIbanOverAccountNumber() {
		Recipient recipient = new Recipient();
		recipient.setIban("DE123");
		recipient.setAccountNumber("123456");

		assertEquals("DE123", RecipientListPanel.recipientAccountIdentifier(recipient));
	}

	@Test
	void recipientAccountIdentifier_shouldUseAccountNumberWhenIbanIsBlank() {
		Recipient recipient = new Recipient();
		recipient.setAccountNumber("123456");

		assertEquals("123456", RecipientListPanel.recipientAccountIdentifier(recipient));
	}

	@Test
	void recipientBankCode_shouldPreferBicOverBlz() {
		Recipient recipient = new Recipient();
		recipient.setBic("TESTDEFFXXX");
		recipient.setBlz("50010517");

		assertEquals("TESTDEFFXXX", RecipientListPanel.recipientBankCode(recipient));
	}

	@Test
	void recipientBankCode_shouldUseBlzWhenBicIsBlank() {
		Recipient recipient = new Recipient();
		recipient.setBlz("50010517");

		assertEquals("50010517", RecipientListPanel.recipientBankCode(recipient));
	}
}
