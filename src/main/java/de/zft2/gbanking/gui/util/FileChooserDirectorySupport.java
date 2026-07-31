package de.zft2.gbanking.gui.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.util.AppPaths;
import javafx.stage.FileChooser;

public final class FileChooserDirectorySupport {

	private static final Logger log = LogManager.getLogger(FileChooserDirectorySupport.class);
	private static UnaryOperator<String> optionReader = key -> null;
	private static BiConsumer<String, String> optionWriter = (key, value) -> {
	};
	private static Runnable saver = () -> {
	};

	private FileChooserDirectorySupport() {
	}

	public static synchronized void initialize(UnaryOperator<String> reader, BiConsumer<String, String> writer, Runnable optionsSaver) {
		optionReader = Objects.requireNonNull(reader, "reader");
		optionWriter = Objects.requireNonNull(writer, "writer");
		saver = Objects.requireNonNull(optionsSaver, "optionsSaver");
	}

	public static void configure(FileChooser fileChooser, String optionKey) {
		fileChooser.setInitialDirectory(null);
		String directory;
		synchronized (FileChooserDirectorySupport.class) {
			directory = optionReader.apply(optionKey);
		}
		if (directory == null || directory.isBlank()) {
			return;
		}
		try {
			Path resolvedDirectory = AppPaths.resolveInApplicationDirectory(directory);
			if (resolvedDirectory.toFile().isDirectory()) {
				fileChooser.setInitialDirectory(resolvedDirectory.toFile());
			}
		} catch (InvalidPathException exception) {
			log.warn("Ignoring invalid stored file chooser directory for {}", optionKey);
		}
	}

	public static Path remember(File selectedFile, String optionKey) {
		if (selectedFile == null) {
			return null;
		}
		Runnable currentSaver = null;
		if (selectedFile.getParent() != null) {
			synchronized (FileChooserDirectorySupport.class) {
				optionWriter.accept(optionKey, selectedFile.getParent());
				currentSaver = saver;
			}
		}
		if (currentSaver != null) {
			currentSaver.run();
		}
		return selectedFile.toPath();
	}
}
