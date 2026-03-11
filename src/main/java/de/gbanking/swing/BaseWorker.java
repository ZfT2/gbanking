package de.gbanking.swing;

import javax.swing.SwingWorker;

import de.gbanking.db.DBController;
import de.gbanking.messages.Messages;

public abstract class BaseWorker extends SwingWorker<Void, Void> {

	protected Messages messages;
	protected DBController dbController;

	protected String processingState;

	protected BaseWorker() {
		messages = Messages.getInstance();
		dbController = DBController.getInstance(".");
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
	}

	public int getWorkerProgress() {
		return getProgress();
	}

	public void setWorkerProgress(int progress) {
		setProgress(progress);
	}

}
