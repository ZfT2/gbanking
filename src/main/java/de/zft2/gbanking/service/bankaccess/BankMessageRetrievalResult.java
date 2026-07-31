package de.zft2.gbanking.service.bankaccess;

import java.util.List;

import de.zft2.gbanking.db.dao.BankMessage;

public record BankMessageRetrievalResult(boolean successful, boolean wrongPin, List<BankMessage> messages) {

	public static BankMessageRetrievalResult success(List<BankMessage> messages) {
		return new BankMessageRetrievalResult(true, false, messages != null ? List.copyOf(messages) : List.of());
	}

	public static BankMessageRetrievalResult failure() {
		return failure(List.of());
	}

	public static BankMessageRetrievalResult failure(List<BankMessage> messages) {
		return new BankMessageRetrievalResult(false, false, messages != null ? List.copyOf(messages) : List.of());
	}

	public static BankMessageRetrievalResult wrongPinFailure() {
		return new BankMessageRetrievalResult(false, true, List.of());
	}
}
