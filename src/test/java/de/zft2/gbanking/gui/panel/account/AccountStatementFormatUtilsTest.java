package de.zft2.gbanking.gui.panel.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.service.account.AccountStatement;

class AccountStatementFormatUtilsTest {

	@Test
	void formatStatementNumber_WithSingleDigitStatementNumber_PadsWithLeadingZero() {
		AccountStatement statement = createStatement(2026, 3);

		String formatted = AccountStatementFormatUtils.formatStatementNumber(statement);

		assertEquals("2026/03", formatted);
	}

	@Test
	void formatStatementNumber_WithTwoDigitStatementNumber_DoesNotChangeNumber() {
		AccountStatement statement = createStatement(2026, 12);

		String formatted = AccountStatementFormatUtils.formatStatementNumber(statement);

		assertEquals("2026/12", formatted);
	}

	private AccountStatement createStatement(int year, int number) {
		return new AccountStatement(0, 1, "Testkonto", null, "", "", null, null, null, null, year, number, 0L, "", "", "", false, false, null);
	}
}
