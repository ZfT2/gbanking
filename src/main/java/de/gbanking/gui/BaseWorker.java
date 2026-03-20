package de.gbanking.gui;

import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;
import javafx.concurrent.Task;

public abstract class BaseWorker extends Task<Void> {

	protected final Messages messages;
	protected final DBController dbController;
	protected String processingState;

	private double workerProgress = 0.0;

	protected BaseWorker() {
		this.messages = Messages.getInstance();
		this.dbController = DBController.getInstance(".");
	}

	protected String getText(String key) {
		return messages.getMessage(key);
	}

	protected String getText(String key, int value) {
		return messages.getFormattedMessage(key, value);
	}

	protected String getText(String key, String value1) {
		return messages.getFormattedMessage(key, value1);
	}

	public String getProcessingState() {
		return processingState;
	}

	public void setProcessingState(String processingState) {
		this.processingState = processingState;
		updateMessage(processingState);
	}

	public int getWorkerProgress() {
		return (int) Math.round(workerProgress);
	}

	public double getWorkerProgressDouble() {
		return workerProgress;
	}

	public void setWorkerProgress(int progress) {
		this.workerProgress = progress;
		updateProgress(progress, 100);
	}

	public void setWorkerProgress(double progress) {
		this.workerProgress = progress;
		updateProgress(progress, 100.0);
	}
}