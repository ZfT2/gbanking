package de.zft2.gbanking.rebooking;

public final class RebookingRules {

	private RebookingRules() {
	}

	public static boolean isForbiddenSameAccountRebooking(int sourceAccountId, int targetAccountId, boolean cancellation) {
		return sourceAccountId > 0 && targetAccountId > 0 && sourceAccountId == targetAccountId && !cancellation;
	}

	public static boolean isForbiddenSameAccountRebooking(int sourceAccountId, Integer targetAccountId, boolean cancellation) {
		return targetAccountId != null && isForbiddenSameAccountRebooking(sourceAccountId, targetAccountId.intValue(), cancellation);
	}
}
