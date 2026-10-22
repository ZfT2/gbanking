package de.zft2.gbanking.gui.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import de.zft2.gbanking.gui.enu.FileType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Central creation of the file choosers of the import and the export of files.
 *
 * <p>
 * The file chooser is not thread safe, therefore it has to be opened on the JavaFX thread. The
 * helper methods create the chooser, open it and remember the directory of the selected files.
 * </p>
 */
public final class FileChooserSupport {

	private FileChooserSupport() {
	}

	/**
	 * Opens a file chooser for a single file.
	 *
	 * @param owner           the owner window of the file chooser
	 * @param fileType        the type of the selected file
	 * @param directoryOption the option key of the directory of the file chooser
	 * @param saveDialog      true if a file has to be saved, false if a file has to be opened
	 * @return the selected file, null if the selection was cancelled
	 */
	public static Path chooseFile(Window owner, FileType fileType, String directoryOption, boolean saveDialog) {
		FileChooser chooser = createChooser(fileType, directoryOption);
		File selectedFile = saveDialog ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
		return FileChooserDirectorySupport.remember(selectedFile, directoryOption);
	}

	/**
	 * Opens a file chooser for multiple files.
	 *
	 * @param owner           the owner window of the file chooser
	 * @param fileType        the type of the selected files
	 * @param directoryOption the option key of the directory of the file chooser
	 * @return the selected files, an empty list if the selection was cancelled
	 */
	public static List<Path> chooseFiles(Window owner, FileType fileType, String directoryOption) {
		FileChooser chooser = createChooser(fileType, directoryOption);
		return FileChooserDirectorySupport.remember(chooser.showOpenMultipleDialog(owner), directoryOption);
	}

	private static FileChooser createChooser(FileType fileType, String directoryOption) {
		FileChooser chooser = new FileChooser();
		FileChooserDirectorySupport.configure(chooser, directoryOption);
		chooser.getExtensionFilters()
				.setAll(new FileChooser.ExtensionFilter(fileType.getDescription(), fileType.getExtensionPatterns()));
		return chooser;
	}
}
