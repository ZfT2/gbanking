package de.zft2.gbanking.service;

import java.io.Serializable;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.progress.InstituteFileImportProgressBarPanel;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.booking.BookingCategoryService;
import de.zft2.gbanking.service.institute.InstituteImportService.ImportDefinition;
import de.zft2.gbanking.service.institute.InstituteImportService;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

public class GBankingService extends AbstractDbService implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6144828924996356319L;

	private static final Logger log = LogManager.getLogger(GBankingService.class);

	private final transient AccountTransactionService accountTransactionService;
	private final transient BookingCategoryService bookingCategoryService;
	private final transient InstituteImportService instituteImportService;

	public GBankingService() {
		this.accountTransactionService = ServiceRegistry.getService(AccountTransactionService.class);
		this.bookingCategoryService = ServiceRegistry.getService(BookingCategoryService.class);
		this.instituteImportService = ServiceRegistry.getService(InstituteImportService.class);
	}

	public List<BankAccount> getAllAccounts() {
		return dbController.getAll(BankAccount.class);
	}

	public void setup() {
		Platform.runLater(() -> {
			try {
				startInstituteImportWithProgress();
			} catch (RuntimeException exception) {
				log.error("Could not start institute imports", exception);
			}
		});

	}

	public void postRetriveActions(List<BankAccount> accountsList) {
		for (BankAccount account : accountsList) {
			accountTransactionService.adjustRebookings(account);
		}
		bookingCategoryService.applyCategoryRules(accountsList);
	}

	private void startInstituteImportWithProgress() {
		Window owner = DialogWindowSupport.findBestOwnerWindow().orElse(null);
		showInstituteImportsSequentially(owner, instituteImportService.getDefaultImports());
	}

	static void showInstituteImportsSequentially(Window owner, List<ImportDefinition> importDefinitions) {
		for (ImportDefinition importDefinition : importDefinitions) {
			InstituteFileImportProgressBarPanel progressPanel = new InstituteFileImportProgressBarPanel(importDefinition.importType(), owner);
			Stage progressWindow = progressPanel.createNewFileImportProgressBarWindow();
			progressWindow.setOnShown(event -> startInstituteImport(progressPanel, progressWindow, importDefinition));
			progressWindow.showAndWait();
		}
	}

	private static void startInstituteImport(InstituteFileImportProgressBarPanel progressPanel, Stage progressWindow,
			ImportDefinition importDefinition) {
		try {
			progressPanel.startTask(importDefinition.fileName(), null, null);
		} catch (RuntimeException exception) {
			log.error("Could not start institute import for {}", importDefinition.fileName(), exception);
			progressWindow.close();
		}
	}

}
