package de.zft2.gbanking.concurrent;

/**
 * Calculates the progress of a processing phase. The result is always inside the interval of the
 * phase, independent of the number of the processed items.
 */
public final class ProgressCalculation {

	private ProgressCalculation() {
	}

	/**
	 * Calculates the progress of a phase for the given number of processed items. The number of the
	 * processed items is limited to the number of all items of the phase, therefore the result never
	 * leaves the interval of the phase.
	 *
	 * @param processedCount  the number of already processed items
	 * @param totalCount      the number of all items of the phase
	 * @param percentageStart the progress at the start of the phase in percent
	 * @param percentageStop  the progress at the end of the phase in percent
	 * @return the progress of the phase in percent, between percentageStart and percentageStop
	 */
	public static int calculate(long processedCount, long totalCount, int percentageStart, int percentageStop) {
		if (totalCount <= 0) {
			return percentageStop;
		}
		long boundedCount = Math.max(0L, Math.min(processedCount, totalCount));
		double completedFraction = boundedCount / (double) totalCount;
		return percentageStart + (int) Math.round((percentageStop - percentageStart) * completedFraction);
	}
}
