package de.zft2.gbanking.gui.panel.account;

import java.time.LocalDate;

import de.zft2.gbanking.gui.util.DateFormatUtils;
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

	static String formatPeriod(LocalDate start, LocalDate end) {
		if (start == null && end == null) {
			return "";
		}
		if (start == null) {
			return DateFormatUtils.formatLong(end);
		}
		if (end == null) {
			return DateFormatUtils.formatLong(start);
		}
		return DateFormatUtils.formatLong(start) + " - " + DateFormatUtils.formatLong(end);
	}

	static String formatFileSize(long size) {
		if (size <= 0) {
			return "";
		}
		return size < 1024 ? size + " B" : (size / 1024) + " KB";
	}

	private static String formatStatementSequence(int number) {
		if (number <= 0) {
			return "";
		}
		return number < 10 ? "0" + number : Integer.toString(number);
	}
}
