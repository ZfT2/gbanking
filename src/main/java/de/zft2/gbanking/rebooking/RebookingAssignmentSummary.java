package de.zft2.gbanking.rebooking;

import java.util.List;

public record RebookingAssignmentSummary(List<DetectedRebookingPair> pairs, List<RebookingAccountSummary> accountSummaries,
		List<StockSettlementAssignment> stockAssignments) {

	public record StockSettlementAssignment(int transactionId, int bookingId, int depotAccountId, int settlementAccountId) {
	}

	public RebookingAssignmentSummary(List<DetectedRebookingPair> pairs, List<RebookingAccountSummary> accountSummaries) {
		this(pairs, accountSummaries, List.of());
	}

	public RebookingAssignmentSummary {
		pairs = pairs != null ? List.copyOf(pairs) : List.of();
		accountSummaries = accountSummaries != null ? List.copyOf(accountSummaries) : List.of();
		stockAssignments = stockAssignments != null ? List.copyOf(stockAssignments) : List.of();
	}

	public static RebookingAssignmentSummary empty() {
		return new RebookingAssignmentSummary(List.of(), List.of());
	}

	public boolean isEmpty() {
		return pairs.isEmpty() && stockAssignments.isEmpty();
	}

	public int pairCount() {
		return pairs.size() + stockAssignments.size();
	}
}
