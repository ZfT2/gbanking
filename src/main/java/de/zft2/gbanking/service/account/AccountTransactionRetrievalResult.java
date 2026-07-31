package de.zft2.gbanking.service.account;

import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;

public record AccountTransactionRetrievalResult(AccountRetrievalStatus status, int newBookingCount, int pendingBookingCount, String errorMessage) {

	public boolean successful() {
		return status == AccountRetrievalStatus.SUCCESS;
	}

	public boolean wrongPin() {
		return status == AccountRetrievalStatus.WRONG_PIN;
	}

	public static AccountTransactionRetrievalResult success() {
		return success(0, 0);
	}

	public static AccountTransactionRetrievalResult success(int newBookingCount, int pendingBookingCount) {
		return new AccountTransactionRetrievalResult(AccountRetrievalStatus.SUCCESS, newBookingCount, pendingBookingCount, null);
	}

	public static AccountTransactionRetrievalResult failure() {
		return failure(null);
	}

	public static AccountTransactionRetrievalResult failure(String errorMessage) {
		return new AccountTransactionRetrievalResult(AccountRetrievalStatus.FAILED, 0, 0, errorMessage);
	}

	public static AccountTransactionRetrievalResult wrongPinFailure() {
		return wrongPinFailure(null);
	}

	public static AccountTransactionRetrievalResult wrongPinFailure(String errorMessage) {
		return new AccountTransactionRetrievalResult(AccountRetrievalStatus.WRONG_PIN, 0, 0, errorMessage);
	}

	public static AccountTransactionRetrievalResult cancelled(String errorMessage) {
		return new AccountTransactionRetrievalResult(AccountRetrievalStatus.CANCELLED, 0, 0, errorMessage);
	}
}
