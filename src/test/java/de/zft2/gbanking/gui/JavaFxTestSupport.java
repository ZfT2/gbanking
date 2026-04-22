package de.zft2.gbanking.gui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

public final class JavaFxTestSupport {

	private static final AtomicBoolean STARTED = new AtomicBoolean(false);

	private JavaFxTestSupport() {
	}

	public static void runFx(Runnable action) {
		callFx(() -> {
			action.run();
			return null;
		});
	}

	public static <T> T callFx(Callable<T> action) {
		ensureStarted();

		if (Platform.isFxApplicationThread()) {
			return callDirect(action);
		}

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();

		Platform.runLater(() -> {
			try {
				result.set(action.call());
			} catch (Throwable ex) {
				error.set(ex);
			} finally {
				latch.countDown();
			}
		});

		await(latch);
		if (error.get() != null) {
			throw propagate(error.get());
		}
		return result.get();
	}

	private static void ensureStarted() {
		if (STARTED.get()) {
			return;
		}

		synchronized (STARTED) {
			if (STARTED.get()) {
				return;
			}

			CountDownLatch latch = new CountDownLatch(1);
			try {
				Platform.startup(() -> {
					Platform.setImplicitExit(false);
					latch.countDown();
				});
				await(latch);
			} catch (IllegalStateException ex) {
				Platform.setImplicitExit(false);
			}
			STARTED.set(true);
		}
	}

	private static <T> T callDirect(Callable<T> action) {
		try {
			return action.call();
		} catch (Exception ex) {
			throw propagate(ex);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException(new TimeoutException("JavaFX action timed out"));
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static IllegalStateException propagate(Throwable ex) {
		if (ex instanceof IllegalStateException stateException) {
			return stateException;
		}
		if (ex instanceof ExecutionException executionException && executionException.getCause() != null) {
			return propagate(executionException.getCause());
		}
		return new IllegalStateException(ex);
	}
}
