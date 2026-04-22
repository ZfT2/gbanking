package de.zft2.gbanking.file.exp;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseBean;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.gui.BaseWorker;

public abstract class FileExportBean extends BaseBean {

	private static final Logger log = LogManager.getLogger(FileExportBean.class);

	protected static final String TAG_KONTO = "KONTO";
	protected static final String TAG_IBAN = "IBAN";
	protected static final String TAG_BIC = "BIC";
	protected static final String TAG_KONTONR = "KONTONR";
	protected static final String TAG_BLZ = "BLZ";
	protected static final String TAG_TYPE = "KONTOART";
	protected static final String TAG_BANKNAME = "BANKNAME";
	protected static final String TAG_BEZEICHNUNG = "BEZEICHNUNG";
	protected static final String TAG_WAEHRUNG = "WAEHRUNG";
	protected static final String TAG_KONTOSTAND = "KONTOSTAND";

	protected enum ExportConstants {
		ACCOUNT(TAG_KONTO, "Konto"), IBAN(TAG_IBAN, "IBAN"), BIC(TAG_BIC, "BIC"), ACCOUNT_NUMBER(TAG_KONTONR, "Konto-Nr."), BLZ(TAG_BLZ, "BLZ"),
		ACCOUNT_TYPE(TAG_TYPE, "Konto-Art"), BANK_NAME(TAG_BANKNAME, "Kreditinstitut"), DESCRIPTION(TAG_BEZEICHNUNG, "Bezeichnung"),
		CURRECNCY(TAG_WAEHRUNG, "Währung"), BALANCE(TAG_KONTOSTAND, "Kontostand"), DATE(null, "Datum"), DATE_BOOKING(null, "Buchungsdatum"),
		DATE_VALUE(null, "Wertstellung"), NOTICE(null, "Notiz"), AMOUNT(null, "Wert"), REFERENCE_ACCOUNT_NAME(null, "Gegenkonto Nr."),
		SOURCE_FILENAME(null, "Quelle Datei"), SOURCE_ACCOUNT("KONTO (Quelle)", "KONTO (Quelle)");

		protected static ExportConstants forString(String strValue) {
			for (ExportConstants x : values()) {
				if (x.description.equals(strValue)) {
					return x;
				}
			}
			return null;
		}

		private final String tag;
		private final String description;

		ExportConstants(String tag, String description) {
			this.tag = tag;
			this.description = description;
		}

		public String getTag() {
			return tag;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	protected int totalAccounts = 0;
	protected long totalBookings = 0L;

	protected final BaseWorker worker;
	private double currentProgress = 0.0;

	protected FileExportBean(BaseWorker worker) {
		this.worker = worker;
	}

	protected void updateWorkerStateAccounts(long importedCount, String message, Object... param) {
		updateWorkerState(importedCount, totalAccounts, 0, 10, message, param);
	}

	protected void updateWorkerStateBookings(long importedCount, String message, Object... param) {
		updateWorkerState(importedCount, totalBookings, 10, 90, message, param);
	}

	private void updateWorkerState(long importedCount, long totalCount, int percentageStart, int percentageStop, String message, Object... param) {
		if (worker != null) {
			int progress;

			if (totalCount <= 0) {
				progress = percentageStart;
			} else {
				progress = (int) (importedCount / (double) totalCount * 100 * (percentageStop * 0.1));
				progress = progress > percentageStop ? percentageStop : progress;
				progress = progress < percentageStart ? progress + percentageStart : progress;
			}

			currentProgress = progress;
			worker.setProcessingState(String.format(message, param));
			worker.setWorkerProgress(progress);
		} else {
			log.warn("no worker instantiated.");
		}
	}

	protected void updateWorkerState(int progress, boolean updateProgress, String message, Object... param) {
		if (worker != null) {
			worker.setProcessingState(String.format(message, param));

			currentProgress = updateProgress ? currentProgress + progress : progress;

			if (currentProgress < 0) {
				currentProgress = 0;
			} else if (currentProgress > 100) {
				currentProgress = 100;
			}

			worker.setWorkerProgress(currentProgress);
		} else {
			log.warn("no worker instantiated.");
		}
	}

	public abstract boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName);
}