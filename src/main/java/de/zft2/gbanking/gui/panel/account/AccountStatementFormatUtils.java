package de.zft2.gbanking.gui.panel.account;

import de.zft2.gbanking.service.account.AccountStatement;

final class AccountStatementFormatUtils {

	private AccountStatementFormatUtils() {
	}

	static String formatStatementNumber(AccountStatement statement) {
		if (statement.year() <= 0 && statement.number() <= 0) {
			return "";
		}
		if (statement.year() <= 0) {
			return formatStatementSequence(statement.number());
		}
		if (statement.number() <= 0) {
			return Integer.toString(statement.year());
		}
		return statement.year() + "/" + formatStatementSequence(statement.number());
	}

	private static String formatStatementSequence(int number) {
		if (number <= 0) {
			return "";
		}
		return number < 10 ? "0" + number : Integer.toString(number);
	}
}
