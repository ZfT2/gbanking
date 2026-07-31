package de.zft2.gbanking.service.account;

import java.util.List;

public record AccountStatementRetrievalResult(boolean successful, boolean wrongPin, List<AccountStatement> statements) {

	public static AccountStatementRetrievalResult success(List<AccountStatement> statements) {
		return new AccountStatementRetrievalResult(true, false, statements != null ? List.copyOf(statements) : List.of());
	}

	public static AccountStatementRetrievalResult failure() {
		return new AccountStatementRetrievalResult(false, false, List.of());
	}

	public static AccountStatementRetrievalResult wrongPinFailure() {
		return new AccountStatementRetrievalResult(false, true, List.of());
	}
}
