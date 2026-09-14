package de.zft2.gbanking.concurrent;

public interface ProgressReporter {

	void reportState(String state);

	void reportProgress(double progress);

	void checkCancelled();
}
