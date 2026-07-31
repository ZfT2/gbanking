package de.zft2.gbanking.gui.model;

import java.time.LocalDate;

public record PendingStatementReceipts(int count, LocalDate latestStatementDate) {
}
