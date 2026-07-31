package de.zft2.gbanking.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.gui.EnvironmentOptions;

class FileChooserDirectorySupportTest {

	@TempDir
	private Path tempDirectory;

	@AfterEach
	void resetSupport() {
		FileChooserDirectorySupport.initialize(key -> null, (key, value) -> {
		}, () -> {
		});
	}

	@Test
	void shouldRememberDirectoryAndPersistOptions() {
		Map<String, String> options = new HashMap<>();
		AtomicInteger saveCounter = new AtomicInteger();
		FileChooserDirectorySupport.initialize(key -> options.get(key), (key, value) -> options.put(key, value),
				() -> saveCounter.incrementAndGet());
		Path selectedFile = tempDirectory.resolve("booking.csv");

		Path result = FileChooserDirectorySupport.remember(selectedFile.toFile(), EnvironmentOptions.DEFAULT_DIR_IMPORT);

		assertEquals(selectedFile, result);
		assertEquals(tempDirectory.toString(), options.get(EnvironmentOptions.DEFAULT_DIR_IMPORT));
		assertEquals(1, saveCounter.get());
	}
}
