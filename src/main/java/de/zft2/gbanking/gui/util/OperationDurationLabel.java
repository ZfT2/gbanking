package de.zft2.gbanking.gui.util;

import java.util.concurrent.TimeUnit;

import de.zft2.gbanking.BaseMessages;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

public final class OperationDurationLabel extends Label implements BaseMessages {

	private static final long SECONDS_PER_MINUTE = 60L;

	private final Timeline updateTimeline;
	private long startedAtNanos;
	private boolean running;

	public OperationDurationLabel() {
		updateTimeline = new Timeline(new KeyFrame(Duration.seconds(1d), event -> updateLabel()));
		updateTimeline.setCycleCount(Animation.INDEFINITE);
		setText(getText("UI_LABEL_DURATION", formatSeconds(0L)));
	}

	public void start() {
		if (running) {
			return;
		}
		startedAtNanos = System.nanoTime();
		running = true;
		updateLabel();
		updateTimeline.playFromStart();
	}

	public void stop() {
		if (!running) {
			return;
		}
		updateLabel();
		running = false;
		updateTimeline.stop();
	}

	static String formatSeconds(long elapsedSeconds) {
		long seconds = Math.max(0L, elapsedSeconds);
		if (seconds < SECONDS_PER_MINUTE) {
			return seconds + "s";
		}
		return seconds / SECONDS_PER_MINUTE + "m " + seconds % SECONDS_PER_MINUTE + "s";
	}

	private void updateLabel() {
		long elapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
		setText(getText("UI_LABEL_DURATION", formatSeconds(TimeUnit.NANOSECONDS.toSeconds(elapsedNanos))));
	}
}
