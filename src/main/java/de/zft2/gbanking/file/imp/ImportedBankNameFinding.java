package de.zft2.gbanking.file.imp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportedBankNameFinding(int bookingId, LocalDate date, String accountName, String purpose, BigDecimal amount,
		String recipientName, String accountIdentifier, String bankIdentifier, String currentBankName,
		List<String> candidateBankNames, String suggestedBankName) {

	public ImportedBankNameFinding {
		candidateBankNames = List.copyOf(candidateBankNames);
	}
}
