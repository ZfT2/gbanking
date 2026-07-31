package de.zft2.gbanking.service.account;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountStatement(
		int id,
		int accountId,
		String accountName,
		Path file,
		String fileName,
		String format,
		LocalDateTime retrievedAt,
		LocalDate statementDate,
		LocalDate startDate,
		LocalDate endDate,
		int year,
		int number,
		long size,
		String iban,
		String bic,
		String sourceJob,
		boolean receiptAvailable,
		boolean acknowledged,
		LocalDateTime acknowledgedAt) {
}
