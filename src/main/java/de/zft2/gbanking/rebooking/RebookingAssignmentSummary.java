package de.zft2.gbanking.rebooking;

import java.util.List;

public record RebookingAssignmentSummary(List<DetectedRebookingPair> pairs, List<RebookingAccountSummary> accountSummaries) {

	public RebookingAssignmentSummary {
		pairs = pairs != null ? List.copyOf(pairs) : List.of();
		accountSummaries = accountSummaries != null ? List.copyOf(accountSummaries) : List.of();
	}

	public static RebookingAssignmentSummary empty() {
		return new RebookingAssignmentSummary(List.of(), List.of());
	}

	public boolean isEmpty() {
		return pairs.isEmpty();
	}

	public int pairCount() {
		return pairs.size();
	}
}
