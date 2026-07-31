package de.zft2.gbanking.concurrent;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class CancellationSupport {

	private static final ThreadLocal<BooleanSupplier> CANCELLATION_REQUESTED = new ThreadLocal<>();

	private CancellationSupport() {
	}

	public static void runWithCancellation(BooleanSupplier cancellationRequested, Runnable operation) {
		Objects.requireNonNull(cancellationRequested, "cancellationRequested");
		Objects.requireNonNull(operation, "operation");
		BooleanSupplier previousCancellationRequested = CANCELLATION_REQUESTED.get();
		CANCELLATION_REQUESTED.set(cancellationRequested);
		try {
			throwIfCancellationRequested();
			operation.run();
			throwIfCancellationRequested();
		} finally {
			if (previousCancellationRequested == null) {
				CANCELLATION_REQUESTED.remove();
			} else {
				CANCELLATION_REQUESTED.set(previousCancellationRequested);
			}
		}
	}

	public static void throwIfCancellationRequested() {
		BooleanSupplier cancellationRequested = CANCELLATION_REQUESTED.get();
		if ((cancellationRequested != null && cancellationRequested.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Background action was cancelled");
		}
	}
}
