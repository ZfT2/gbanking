package de.zft2.gbanking.gui.util;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.CsvImportDialogSupport;
import de.zft2.gbanking.gui.dialog.CsvImportDialogSupport.Selection;
import de.zft2.gbanking.gui.EnvironmentOptions;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.panel.account.AccountListPanel;
import de.zft2.gbanking.gui.progress.FileExportProgressBarPanel;
import de.zft2.gbanking.gui.progress.FileImportProgressBarPanel;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public final class BookingFileActionSupport {

	private static final Logger log = LogManager.getLogger(BookingFileActionSupport.class);

	private BookingFileActionSupport() {
	}

	public static void exportBookings(Window owner, BankAccount account, ExportType exportType, AccountListPanel accountListPanel) {
		if (account == null) {
			return;
		}
		Path exportFile = chooseFile(owner, exportType.getFileType(), true);
		if (exportFile == null) {
			return;
		}
		try {
			FileExportProgressBarPanel progressPanel = new FileExportProgressBarPanel(owner, List.of(account));
			var progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressPanel.startTask(exportFile.toString(), exportType, accountListPanel);
			progressWindow.show();
		} catch (Exception e) {
			log.error("Booking export failed", e);
			showWarning(owner, e.getMessage());
		}
	}

	public static void importBookings(Window owner, BankAccount account, ExportType importType, AccountListPanel accountListPanel, Runnable successCallback) {
		if (account == null) {
			return;
		}
		Path importFile = chooseFile(owner, importType.getFileType(), false);
		if (importFile == null) {
			return;
		}
		try {
			Selection selection = new Selection(null, account);
			if (importType == ExportType.BOOKINGS_CSV) {
				var preparedImport = CsvImportDialogSupport.prepare(owner, importFile, account, account);
				if (preparedImport.isEmpty()) {
					return;
				}
				selection = preparedImport.get();
			}
			FileImportProgressBarPanel progressPanel = new FileImportProgressBarPanel(owner, selection.account(), successCallback,
					selection.definitionName());
			var progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressPanel.startTask(importFile.toString(), importType, accountListPanel);
			progressWindow.show();
		} catch (Exception e) {
			log.error("Booking import failed", e);
			showWarning(owner, e.getMessage());
		}
	}

	private static Path chooseFile(Window owner, FileType fileType, boolean saveDialog) {
		FileChooser chooser = new FileChooser();
		String optionKey = saveDialog ? EnvironmentOptions.DEFAULT_DIR_EXPORT : EnvironmentOptions.DEFAULT_DIR_IMPORT;
		FileChooserDirectorySupport.configure(chooser, optionKey);
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(fileType.getDescription(), fileType.getExtensionPatterns()));
		var selectedFile = saveDialog ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
		return FileChooserDirectorySupport.remember(selectedFile, optionKey);
	}

	private static void showWarning(Window owner, String text) {
		DialogWindowSupport.showAlert(owner, Alert.AlertType.WARNING, text);
	}
}
