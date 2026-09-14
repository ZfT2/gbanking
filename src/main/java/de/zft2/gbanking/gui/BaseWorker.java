package de.zft2.gbanking.gui;

import java.util.concurrent.CancellationException;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.concurrent.ProgressReporter;
import de.zft2.gbanking.db.DBController;
import javafx.concurrent.Task;

public abstract class BaseWorker extends Task<Void> implements ProgressReporter {

	protected final DBController dbController;
	protected String processingState;

	private volatile double workerProgress = 0.0;

	protected BaseWorker() {
		this.dbController = DBController.getInstance(".");
	}

	public String getProcessingState() {
		return processingState;
	}

	public void setProcessingState(String processingState) {
		checkCancelled();
		this.processingState = processingState;
		updateMessage(processingState);
	}

	@Override
	public void reportState(String state) {
		setProcessingState(state);
	}

	public int getWorkerProgress() {
		return (int) Math.round(workerProgress);
	}

	public double getWorkerProgressDouble() {
		return workerProgress;
	}

	public void setWorkerProgress(int progress) {
		checkCancelled();
		this.workerProgress = progress;
		updateProgress(progress, 100);
	}

	public void setWorkerProgress(double progress) {
		checkCancelled();
		this.workerProgress = progress;
		updateProgress(progress, 100.0);
	}

	@Override
	public void reportProgress(double progress) {
		setWorkerProgress(progress);
	}

	@Override
	public void checkCancelled() {
		CancellationSupport.throwIfCancellationRequested();
		if (isCancelled()) {
			throw new CancellationException("Background action was cancelled");
		}
	}
}
