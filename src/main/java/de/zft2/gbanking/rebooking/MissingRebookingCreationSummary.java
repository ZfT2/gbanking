package de.zft2.gbanking.rebooking;

import java.util.List;

public record MissingRebookingCreationSummary(List<MissingRebookingCandidate> candidates,
		List<MissingRebookingRouteSummary> routeSummaries) {

	public MissingRebookingCreationSummary {
		candidates = candidates != null ? List.copyOf(candidates) : List.of();
		routeSummaries = routeSummaries != null ? List.copyOf(routeSummaries) : List.of();
	}

	public static MissingRebookingCreationSummary empty() {
		return new MissingRebookingCreationSummary(List.of(), List.of());
	}

	public boolean isEmpty() {
		return candidates.isEmpty();
	}

	public int candidateCount() {
		return candidates.size();
	}
}
