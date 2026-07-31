package de.zft2.gbanking.service.account;

public record AccountStatementAcknowledgementResult(boolean successful, boolean wrongPin, int acknowledgedCount) {

	public static AccountStatementAcknowledgementResult success(int acknowledgedCount) {
		return new AccountStatementAcknowledgementResult(true, false, acknowledgedCount);
	}

	public static AccountStatementAcknowledgementResult failure() {
		return new AccountStatementAcknowledgementResult(false, false, 0);
	}

	public static AccountStatementAcknowledgementResult wrongPinFailure() {
		return new AccountStatementAcknowledgementResult(false, true, 0);
	}
}
