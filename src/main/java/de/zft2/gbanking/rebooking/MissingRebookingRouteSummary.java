package de.zft2.gbanking.rebooking;

public record MissingRebookingRouteSummary(int sourceAccountId, String sourceAccountName, int targetAccountId, String targetAccountName,
		int missingRebookings) {
}
