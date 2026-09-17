package de.zft2.gbanking.gui.panel.moneytransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.Source;

class MoneyTransferInputBasePanelTest {

	@Test
	void isAccountNumberCandidate_shouldAcceptOnlyOneToTenDigits() {
		assertTrue(MoneyTransferInputBasePanel.isAccountNumberCandidate("1"));
		assertTrue(MoneyTransferInputBasePanel.isAccountNumberCandidate("1234567890"));

		assertFalse(MoneyTransferInputBasePanel.isAccountNumberCandidate(null));
		assertFalse(MoneyTransferInputBasePanel.isAccountNumberCandidate(""));
		assertFalse(MoneyTransferInputBasePanel.isAccountNumberCandidate("12345678901"));
		assertFalse(MoneyTransferInputBasePanel.isAccountNumberCandidate("DE1234567890"));
		assertFalse(MoneyTransferInputBasePanel.isAccountNumberCandidate("123 456"));
	}

	@Test
	void isBlzCandidate_shouldAcceptOnlyEightDigits() {
		assertTrue(MoneyTransferInputBasePanel.isBlzCandidate("50010517"));

		assertFalse(MoneyTransferInputBasePanel.isBlzCandidate(null));
		assertFalse(MoneyTransferInputBasePanel.isBlzCandidate(""));
		assertFalse(MoneyTransferInputBasePanel.isBlzCandidate("5001051"));
		assertFalse(MoneyTransferInputBasePanel.isBlzCandidate("500105170"));
		assertFalse(MoneyTransferInputBasePanel.isBlzCandidate("MARKDEF1"));
	}

	@Test
	void isBicCandidate_shouldAcceptEightOrElevenCharacterBic() {
		assertTrue(MoneyTransferInputBasePanel.isBicCandidate("MARKDEF1"));
		assertTrue(MoneyTransferInputBasePanel.isBicCandidate("mark de f1 xxx"));

		assertFalse(MoneyTransferInputBasePanel.isBicCandidate(null));
		assertFalse(MoneyTransferInputBasePanel.isBicCandidate(""));
		assertFalse(MoneyTransferInputBasePanel.isBicCandidate("50010517"));
		assertFalse(MoneyTransferInputBasePanel.isBicCandidate("MARKDEF"));
		assertFalse(MoneyTransferInputBasePanel.isBicCandidate("MARKDEF1XXXX"));
	}

	@Test
	void parseAmountInput_shouldAcceptGermanCommaAndGrouping() {
		assertEquals(new BigDecimal("12.34"), MoneyTransferInputBasePanel.parseAmountInput("12,34"));
		assertEquals(new BigDecimal("1234.56"), MoneyTransferInputBasePanel.parseAmountInput("1.234,56"));
		assertEquals(new BigDecimal("1234.50"), MoneyTransferInputBasePanel.parseAmountInput("1234,5"));
		assertEquals(new BigDecimal("1234.00"), MoneyTransferInputBasePanel.parseAmountInput("1.234"));
		assertEquals(new BigDecimal("1234.00"), MoneyTransferInputBasePanel.parseAmountInput("1 234,"));
	}

	@Test
	void parseAmountInput_shouldKeepLegacyDotDecimalsReadable() {
		assertEquals(new BigDecimal("12.34"), MoneyTransferInputBasePanel.parseAmountInput("12.34"));
		assertEquals(new BigDecimal("1234.56"), MoneyTransferInputBasePanel.parseAmountInput("1234.56"));
	}

	@Test
	void parseAmountInput_shouldRejectInvalidAmounts() {
		assertThrows(NumberFormatException.class, () -> MoneyTransferInputBasePanel.parseAmountInput(null));
		assertThrows(NumberFormatException.class, () -> MoneyTransferInputBasePanel.parseAmountInput(""));
		assertThrows(NumberFormatException.class, () -> MoneyTransferInputBasePanel.parseAmountInput("12.34,56"));
		assertThrows(NumberFormatException.class, () -> MoneyTransferInputBasePanel.parseAmountInput("12,345"));
		assertThrows(NumberFormatException.class, () -> MoneyTransferInputBasePanel.parseAmountInput("abc"));
	}

	@Test
	void formatAmountForDisplay_shouldUseGermanFormatWithTwoDecimals() {
		assertEquals("", MoneyTransferInputBasePanel.formatAmountForDisplay(null));
		assertEquals("12,30", MoneyTransferInputBasePanel.formatAmountForDisplay(new BigDecimal("12.3")));
		assertEquals("1.234,56", MoneyTransferInputBasePanel.formatAmountForDisplay(new BigDecimal("1234.56")));
		assertEquals("-1.234,50", MoneyTransferInputBasePanel.formatAmountForDisplay(new BigDecimal("-1234.5")));
	}

	@Test
	void resolveAccountBalance_shouldPreferBankBalance() {
		BankAccount account = new BankAccount();
		account.setBalance(new BigDecimal("123.45"));
		account.setBookings(List.of(booking("999.99", Source.ONLINE)));

		assertEquals(new BigDecimal("123.45"), MoneyTransferInputBasePanel.resolveAccountBalance(account));
	}

	@Test
	void resolveAccountBalance_shouldCalculateFallbackWithoutPrenotifications() {
		BankAccount account = new BankAccount();
		account.setBookings(List.of(booking("100.00", Source.IMPORT), booking("-20.50", Source.ONLINE),
				booking("999.00", Source.ONLINE_PRENO)));

		BigDecimal balance = MoneyTransferInputBasePanel.resolveAccountBalance(account);

		assertEquals(new BigDecimal("79.50"), balance);
		account.setBaseCurrency(Currency.CHF);
		assertEquals("79,50 CHF", MoneyTransferInputBasePanel.formatAccountBalance(account, balance));
	}

	@Test
	void amountStyleClass_shouldReflectAmountSign() {
		assertEquals("amount-positive", MoneyTransferInputBasePanel.amountStyleClass(BigDecimal.ONE));
		assertEquals("amount-negative", MoneyTransferInputBasePanel.amountStyleClass(BigDecimal.ONE.negate()));
		assertEquals("amount-neutral", MoneyTransferInputBasePanel.amountStyleClass(BigDecimal.ZERO));
	}

	@Test
	void isArchivedMoneyTransfer_shouldDetectAllArchivedOrders() {
		MoneyTransfer sentTransfer = new MoneyTransfer();
		sentTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);
		MoneyTransfer supersededTransfer = new MoneyTransfer();
		supersededTransfer.setMoneytransferStatus(MoneyTransferStatus.SUPERSEDED);
		MoneyTransfer missingTransfer = new MoneyTransfer();
		missingTransfer.setMoneytransferStatus(MoneyTransferStatus.NOT_IN_BANK_INVENTORY);
		MoneyTransfer newTransfer = new MoneyTransfer();
		newTransfer.setMoneytransferStatus(MoneyTransferStatus.NEW);

		assertTrue(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(sentTransfer));
		assertTrue(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(supersededTransfer));
		assertTrue(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(missingTransfer));
		assertFalse(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(newTransfer));
		assertFalse(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(new MoneyTransfer()));
		assertFalse(MoneyTransferInputBasePanel.isArchivedMoneyTransfer(null));
	}

	private static Booking booking(String amount, Source source) {
		Booking booking = new Booking();
		booking.setAmount(new BigDecimal(amount));
		booking.setSource(source);
		return booking;
	}
}
