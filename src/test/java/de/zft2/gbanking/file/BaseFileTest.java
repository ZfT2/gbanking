package de.zft2.gbanking.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

public abstract class BaseFileTest {

	@TempDir
	protected Path tempDir;

	protected Path getExportFilePath(String path, String file) throws IOException {
		Path exportFile = tempDir.resolve(path).resolve(file);

		Path parent = exportFile.getParent();
		if (parent != null)
			Files.createDirectories(parent);

		return exportFile;
	}
}
