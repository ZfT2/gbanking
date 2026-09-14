package de.zft2.gbanking.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.gui.dialog.CsvImportDialogSupport;
import de.zft2.gbanking.gui.dialog.MoneyTransferImportStatusDialog;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.gui.enu.FileType;
import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.model.AccountTableModel;
import de.zft2.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.zft2.gbanking.gui.panel.overview.MoneyTransferOverviewPanel;
import de.zft2.gbanking.gui.progress.FileExportProgressBarPanel;
import de.zft2.gbanking.gui.progress.FileImportProgressBarPanel;
import de.zft2.gbanking.gui.progress.MoneyTransferImportProgressBarPanel;
import de.zft2.gbanking.gui.util.FileChooserDirectorySupport;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class FileTransferCoordinator implements BaseGui {

	private static final Logger log = LogManager.getLogger(FileTransferCoordinator.class);

	private final Stage owner;
	private final FileChooser fileChooser = new FileChooser();

	FileTransferCoordinator(Stage owner) {
		this.owner = owner;
	}

	void processBookingImport(ExportType importType) {
		Path importFile = chooseImportFile(importType.getFileType());
		if (importFile == null) {
			log.debug("Booking import cancelled. type={}", importType);
			return;
		}
		try {
			BookingImportSelection selection = prepareBookingImport(importType, importFile);
			if (selection == null) {
				return;
			}
			log.info("Starting booking import. type={}, file={}", () -> importType, () -> fileName(importFile));
			log.debug("Booking import path: {}", importFile);
			startBookingImport(importFile, importType, selection);
		} catch (Exception exception) {
			log.error("Import failed. type={}, file={}", importType, fileName(importFile), exception);
		}
	}

	private BookingImportSelection prepareBookingImport(ExportType importType, Path importFile) throws IOException {
		if (importType != ExportType.BOOKINGS_CSV) {
			return new BookingImportSelection(null, null);
		}
		AccountsTransactionsOverviewPanel overviewPanel = accountsOverview();
		BankAccount suggestedAccount = overviewPanel != null ? overviewPanel.getSelectedAccount() : null;
		var selection = CsvImportDialogSupport.prepare(owner, importFile, null, suggestedAccount);
		return selection.map(value -> new BookingImportSelection(value.account(), value.definitionName())).orElse(null);
	}

	void processMoneyTransferImport(ExportType importType) {
		Path importFile = chooseImportFile(importType.getFileType());
		if (importFile == null) {
			log.debug("Money transfer import cancelled. type={}", importType);
			return;
		}
		Optional<MoneyTransferStatus> importStatus = MoneyTransferImportStatusDialog.show(owner);
		if (importStatus.isEmpty()) {
			log.debug("Money transfer import target selection cancelled. type={}", importType);
			return;
		}
		try {
			log.info("Starting money transfer import. type={}, file={}", () -> importType, () -> fileName(importFile));
			log.debug("Money transfer import path: {}", importFile);
			startMoneyTransferImport(importFile, importType, importStatus.get());
		} catch (Exception exception) {
			log.error("Money transfer import failed. type={}, file={}", importType, fileName(importFile), exception);
			showWarning(owner, exception.getMessage());
		}
	}

	void processExport(ExportType exportType) {
		AccountsTransactionsOverviewPanel overviewPanel = accountsOverview();
		if (overviewPanel == null || overviewPanel.getAccountListPanel() == null) {
			return;
		}

		AccountTableModel accountModel = overviewPanel.getAccountListPanel().getModelAccount();
		List<BankAccount> checkedAccounts = accountModel.getCheckedAccounts();
		if (exportType == ExportType.BOOKINGS_FP3) {
			checkedAccounts = resolveSingleAccountExport(checkedAccounts, overviewPanel);
			if (checkedAccounts.isEmpty()) {
				showWarning(owner, getText("ALERT_BOOKINGS_FP3_EXPORT_ACCOUNT_REQUIRED"));
				return;
			}
		}
		if (checkedAccounts.isEmpty()) {
			log.info("No accounts selected; exporting all accounts.");
			checkedAccounts = accountModel.getAccounts();
		}

		Path exportFile = chooseExportFile(exportType.getFileType());
		if (exportFile == null) {
			log.debug("Export cancelled. type={}", exportType);
			return;
		}
		try {
			int accountCount = checkedAccounts.size();
			log.info("Starting export. type={}, file={}, accounts={}", () -> exportType, () -> fileName(exportFile), () -> accountCount);
			log.debug("Export path: {}", exportFile);
			startExport(exportFile, checkedAccounts, exportType, overviewPanel);
		} catch (Exception exception) {
			log.error("Export failed. type={}, file={}", exportType, fileName(exportFile), exception);
		}
	}

	private List<BankAccount> resolveSingleAccountExport(List<BankAccount> checkedAccounts,
			AccountsTransactionsOverviewPanel overviewPanel) {
		if (checkedAccounts.size() == 1) {
			return checkedAccounts;
		}
		if (!checkedAccounts.isEmpty()) {
			return List.of();
		}
		BankAccount selectedAccount = overviewPanel.getAccountListPanel().getSelectedAccount();
		return selectedAccount != null ? List.of(selectedAccount) : List.of();
	}

	private Path chooseImportFile(FileType fileType) {
		configureFileChooser(fileType, EnvironmentOptions.DEFAULT_DIR_IMPORT);
		return FileChooserDirectorySupport.remember(fileChooser.showOpenDialog(owner), EnvironmentOptions.DEFAULT_DIR_IMPORT);
	}

	private Path chooseExportFile(FileType fileType) {
		configureFileChooser(fileType, EnvironmentOptions.DEFAULT_DIR_EXPORT);
		return FileChooserDirectorySupport.remember(fileChooser.showSaveDialog(owner), EnvironmentOptions.DEFAULT_DIR_EXPORT);
	}

	private void configureFileChooser(FileType fileType, String directoryOption) {
		FileChooserDirectorySupport.configure(fileChooser, directoryOption);
		fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(fileType.getDescription(), fileType.getExtensionPatterns()));
	}

	private void startBookingImport(Path importFile, ExportType importType, BookingImportSelection selection) {
		FileImportProgressBarPanel progressPanel = new FileImportProgressBarPanel(owner, selection.account(), null, selection.csvDefinitionName());
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		AccountsTransactionsOverviewPanel overviewPanel = accountsOverview();
		progressPanel.startTask(importFile.toString(), importType, overviewPanel != null ? overviewPanel.getAccountListPanel() : null);
		progressWindow.show();
	}

	private void startMoneyTransferImport(Path importFile, ExportType importType, MoneyTransferStatus importStatus) {
		MoneyTransferImportProgressBarPanel progressPanel = new MoneyTransferImportProgressBarPanel(owner, null, () -> {
			MoneyTransferOverviewPanel moneyTransferPanel = (MoneyTransferOverviewPanel) OverviewPanelFactory
					.retrievePanel(PageContext.ACCOUNTS_MONEYTRANSFERS.name());
			if (moneyTransferPanel != null) {
				moneyTransferPanel.refreshOnShow();
			}
		}, importType, importStatus);
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		AccountsTransactionsOverviewPanel overviewPanel = accountsOverview();
		progressPanel.startTask(importFile.toString(), importType, overviewPanel != null ? overviewPanel.getAccountListPanel() : null);
		progressWindow.show();
	}

	private void startExport(Path exportFile, List<BankAccount> accounts, ExportType exportType,
			AccountsTransactionsOverviewPanel overviewPanel) {
		FileExportProgressBarPanel progressPanel = new FileExportProgressBarPanel(owner, accounts);
		Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
		progressPanel.startTask(exportFile.toString(), exportType, overviewPanel.getAccountListPanel());
		progressWindow.show();
	}

	private AccountsTransactionsOverviewPanel accountsOverview() {
		return (AccountsTransactionsOverviewPanel) OverviewPanelFactory.retrievePanel(PageContext.ACCOUNTS_TRANSACTIONS.name());
	}

	private String fileName(Path path) {
		Path name = path != null ? path.getFileName() : null;
		return name != null ? name.toString() : null;
	}

	private record BookingImportSelection(BankAccount account, String csvDefinitionName) {
	}
}
