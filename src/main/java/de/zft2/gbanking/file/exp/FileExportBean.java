package de.zft2.gbanking.file.exp;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.concurrent.ProgressReporter;
import de.zft2.gbanking.concurrent.ProgressReporters;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.util.AppPaths;

public abstract class FileExportBean implements BaseMessagesDb {

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

	public enum ExportConstants {
		ACCOUNT(TAG_KONTO, "Konto"),
		IBAN(TAG_IBAN, "IBAN"),
		BIC(TAG_BIC, "BIC"),
		ACCOUNT_NUMBER(TAG_KONTONR, "Konto-Nr."),
		BLZ(TAG_BLZ, "BLZ"),
		ACCOUNT_TYPE(TAG_TYPE, "Konto-Art"),
		BANK_NAME(TAG_BANKNAME, "Kreditinstitut"),
		DESCRIPTION(TAG_BEZEICHNUNG, "Bezeichnung"),
		CURRECNCY(TAG_WAEHRUNG, "Währung"),
		BALANCE(TAG_KONTOSTAND, "Kontostand"),
		DATE(null, "Datum"),
		DATE_BOOKING(null, "Buchungsdatum"),
		DATE_VALUE(null, "Wertstellung"),
		NOTICE(null, "Notiz"),
		AMOUNT(null, "Wert"),
		REFERENCE_ACCOUNT_NAME(null, "Gegenkonto Nr."),
		SOURCE_FILENAME(null, "Quelle Datei"),
		SOURCE_ACCOUNT("KONTO (Quelle)", "KONTO (Quelle)"),
		SOURCE_MONEYTRANSFER(null, "Quelle Zahlungsauftrag"),
		TYP(null, "Typ"),
		PURPOSE(null, "Verwendungszweck"),
		PURPOSE_CODE(null, "Purpose Code"),
		END_TO_END_ID(null, "End-to-End-ID"),
		EXECUTION_DATE(null, "Datum Ausführung"),
		STATE(null, "Status"),
		RECIPIENT_NAME(null, "Empfänger Name"),
		RECIPIENT_IBAN(null, "Empfänger IBAN"),
		RECIPIENT_BIC(null, "Empfänger BIC"),
		RECIPIENT_ACCOUNT_NUMBER(null, "Empfänger Kontonr."),
		RECIPIENT_BLZ(null, "Empfänger BLZ"),
		RECIPIENT_NOTE(null, "Empfänger Notiz"),
		RECIPIENT_SOURCE(null, "Empfänger Quelle"),
		PROTOCOL_STATUS(null, "Protokoll Status"),
		PROTOCOL_RESULT_STATUS(null, "Protokoll Ergebnisstatus"),
		PROTOCOL_PIN_OK(null, "Protokoll PIN OK"),
		PROTOCOL_SCA_REQUIRED(null, "Protokoll SCA erforderlich"),
		PROTOCOL_VOP_REQUIRED(null, "Protokoll VOP erforderlich"),
		PROTOCOL_VOP_RESULT(null, "Protokoll VOP-Ergebnis"),
		PROTOCOL_RECIPIENT_NAME_CORRECTED(null, "Protokoll Name korrigiert"),
		PROTOCOL_TIME_START(null, "Protokoll Start"),
		PROTOCOL_TIME_FINISH(null, "Protokoll Ende"),
		PROTOCOL_BANK_ORDER_ID(null, "Protokoll Bankauftragskennung"),
		PROTOCOL_HBCI_JOB_ID(null, "Protokoll HBCI-Auftragskennung"),
		PROTOCOL_SEPA_ORDER_STATUS(null, "Protokoll SEPA-Auftragsstatus"),
		PROTOCOL_SEPA_CANCELLATION_CODE(null, "Protokoll SEPA-C-Code"),
		PROTOCOL_TEXT(null, "Protokoll Text");

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

	protected final ProgressReporter progressReporter;
	private double currentProgress = 0.0;

	protected FileExportBean(ProgressReporter progressReporter) {
		this.progressReporter = ProgressReporters.orNone(progressReporter);
	}

	protected void updateWorkerStateAccounts(long importedCount, String messageKey, Object... param) {
		updateWorkerState(importedCount, totalAccounts, 0, 10, messageKey, param);
	}

	protected void updateWorkerStateBookings(long importedCount, String messageKey, Object... param) {
		updateWorkerState(importedCount, totalBookings, 10, 90, messageKey, param);
	}

	private void updateWorkerState(long importedCount, long totalCount, int percentageStart, int percentageStop, String messageKey, Object... param) {
		int progress;
		if (totalCount <= 0) {
			progress = percentageStart;
		} else {
			progress = (int) (importedCount / (double) totalCount * 100 * (percentageStop * 0.1));
			progress = progress > percentageStop ? percentageStop : progress;
			progress = progress < percentageStart ? progress + percentageStart : progress;
		}

		currentProgress = progress;
		progressReporter.reportState(getText(messageKey, param));
		progressReporter.reportProgress(progress);
	}

	protected void updateWorkerState(int progress, boolean updateProgress, String messageKey, Object... param) {
		progressReporter.reportState(getText(messageKey, param));
		currentProgress = updateProgress ? currentProgress + progress : progress;
		if (currentProgress < 0) {
			currentProgress = 0;
		} else if (currentProgress > 100) {
			currentProgress = 100;
		}
		progressReporter.reportProgress(currentProgress);
	}

	protected Path prepareExportPath(String fileName) throws IOException {
		Path requestedPath = Path.of(fileName);
		Path exportPath = AppPaths.resolveInApplicationDirectory(requestedPath);
		log.debug("Preparing export path {}", exportPath);
		if (requestedPath.getRoot() == null && exportPath.getParent() != null && exportPath.getParent() != null)
				Files.createDirectories(exportPath.getParent());

		return exportPath;
	}

	protected String fileNameOnly(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "";
		}
		try {
			Path name = Path.of(fileName).getFileName();
			return name != null ? name.toString() : fileName;
		} catch (InvalidPathException e) {
			return fileName;
		}
	}

	protected List<Booking> getBookings(BankAccount account) {
		return dbController.getAllByParentFull(Booking.class, account.getId());
	}

	protected BigDecimal endBalance(BankAccount account, List<Booking> bookings) {
		if (account.getBalance() != null) {
			return account.getBalance();
		}
		if (!bookings.isEmpty() && bookings.get(bookings.size() - 1).getBalance() != null) {
			return bookings.get(bookings.size() - 1).getBalance();
		}
		return sumBookings(bookings);
	}

	protected BigDecimal sumBookings(List<Booking> bookings) {
		return bookings.stream().map(Booking::getAmount).map(this::safeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	protected BigDecimal safeAmount(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}

	public abstract boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName);
}
