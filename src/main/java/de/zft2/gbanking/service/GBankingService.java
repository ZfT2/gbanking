package de.zft2.gbanking.service;

import java.io.Serializable;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.progress.InstituteFileImportProgressBarPanel;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.booking.BookingCategoryService;
import de.zft2.gbanking.service.institute.InstituteImportService.ImportDefinition;
import de.zft2.gbanking.service.institute.InstituteImportService;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
			} catch (Exception e) {
				log.error("Error starting startInstituteImportWithProgress()", e);
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
		Stage dialogStage = new Stage();
		dialogStage.initModality(Modality.APPLICATION_MODAL);

		for (ImportDefinition importDefinition : instituteImportService.getDefaultImports()) {
			startInstituteImportWithProgress(dialogStage, importDefinition);
		}
	}

	private void startInstituteImportWithProgress(Stage dialogStage, ImportDefinition importDefinition) {
		InstituteFileImportProgressBarPanel progressPanel = new InstituteFileImportProgressBarPanel(importDefinition.importType(), dialogStage);
		progressPanel.createNewFileImportProgressBarWindow().show();
		progressPanel.startTask(importDefinition.fileName(), null, null);
	}

}
