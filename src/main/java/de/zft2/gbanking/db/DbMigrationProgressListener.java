package de.zft2.gbanking.db;

@FunctionalInterface
public interface DbMigrationProgressListener {

	void onProgress(String migrationVersion, boolean completed, int completedSteps, int totalSteps);
}
