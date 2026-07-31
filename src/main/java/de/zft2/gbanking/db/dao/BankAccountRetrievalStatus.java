package de.zft2.gbanking.db.dao;

import java.time.LocalDateTime;
import java.util.Objects;

import de.zft2.gbanking.db.dao.enu.AccountRetrievalStatus;

public record BankAccountRetrievalStatus(int bankAccountId, LocalDateTime retrievedAt, AccountRetrievalStatus result, int newBookingCount,
		int pendingBookingCount, String lastError) {

	public BankAccountRetrievalStatus {
		if (bankAccountId <= 0) {
			throw new IllegalArgumentException("A persisted bank account is required");
		}
		Objects.requireNonNull(retrievedAt, "retrievedAt");
		Objects.requireNonNull(result, "result");
		if (newBookingCount < 0 || pendingBookingCount < 0) {
			throw new IllegalArgumentException("Booking counts must not be negative");
		}
	}
}
