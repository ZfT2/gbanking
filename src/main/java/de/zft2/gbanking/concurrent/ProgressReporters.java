package de.zft2.gbanking.concurrent;

public final class ProgressReporters {

	private static final ProgressReporter NONE = new NoOpProgressReporter();

	private ProgressReporters() {
	}

	public static ProgressReporter orNone(ProgressReporter reporter) {
		return reporter != null ? reporter : NONE;
	}

	private static final class NoOpProgressReporter implements ProgressReporter {

		@Override
		public void reportState(String state) {
			// No progress target is attached.
		}

		@Override
		public void reportProgress(double progress) {
			// No progress target is attached.
		}

		@Override
		public void checkCancelled() {
			// No cancellable operation is attached.
		}
	}
}
