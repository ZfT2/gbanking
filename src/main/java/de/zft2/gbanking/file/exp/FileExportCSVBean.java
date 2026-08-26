package de.zft2.gbanking.file.exp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.ExportException;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.gui.BaseWorker;

public class FileExportCSVBean extends FileExportBean {

	private static Logger log = LogManager.getLogger(FileExportCSVBean.class);

	public FileExportCSVBean(BaseWorker worker) {
		super(worker);
	}

	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) throws ExportException {

		if (accountList == null) {
			log.warn("Abort exportFileFromDatatbase execution. accountList is null!");
			return false;
		}

		try {
			List<Object[]> csvLines = createCSVContent(accountList);
			saveToExportFile(csvLines, fileName);
			log.info("Finished CSV booking export. file={}, accounts={}, records={}", () -> fileNameOnly(fileName), accountList::size,
					csvLines::size);
			return true;
		} catch (Exception e) {
			log.error("Error exporting CSV file {}", fileNameOnly(fileName), e);
			log.debug("CSV export path: {}", fileName);
			return false;
		}
	}

	private List<Object[]> createCSVContent(List<BankAccount> accountList) {

		List<Object[]> csvLines = new ArrayList<>();
		totalAccounts = accountList.size();
		updateWorkerState(1, true, "UI_PROGRESS_EXPORT_ACCOUNTS", totalAccounts);

		int exportedAccounts = 0;
		for (BankAccount account : accountList) {
			updateWorkerStateAccounts(exportedAccounts++, "UI_PROGRESS_EXPORT_BOOKINGS_ACCOUNT", account.getAccountName());
			for (Booking booking : getBookings(account)) {
				csvLines.add(createCsvRow(account, booking));
			}
		}

		return csvLines;
	}

	private Object[] createCsvRow(BankAccount account, Booking booking) {
		BookingSepaDetails sepaDetails = booking.getSepaDetails();
		BookingForeignCurrencyDetails foreign = booking.getForeignCurrencyDetails();
		BookingFee fee = booking.getFee();
		String sepaType = sepaDetails != null && sepaDetails.getType() != null ? sepaDetails.getType().name() : null;
		Object[] row = new Object[] {
				account.getAccountName(), account.getIban(), account.getNumber(), account.getBankName(), account.getBic(),
				BookingCsvFormat.formatDate(booking.getDateBooking()), BookingCsvFormat.formatDate(booking.getDateValue()),
				BookingCsvFormat.formatAmount(booking.getAmount()), account.getCurrency(), booking.getPurpose(),
				null, null, null, null, null, null,
				booking.getBookingType() != null ? booking.getBookingType().name() : null,
				booking.getCategory() != null ? booking.getCategory().getFullName() : null,
				booking.getSepaCustomerRef(), booking.getSepaCreditorId(), booking.getSepaEndToEnd(), booking.getSepaMandate(),
				booking.getSepaPersonId(), booking.getSepaPurpose(), sepaType,
				foreign != null ? BookingCsvFormat.formatAmount(foreign.getForeignAmount()) : null,
				foreign != null ? foreign.getForeignCurrency() : null,
				foreign != null ? foreign.getExchangeRateToBaseCurrency().toPlainString() : null,
				fee != null ? BookingCsvFormat.formatAmount(fee.getAmount()) : null,
				fee != null ? fee.getCurrency() : null
		};
		addRecipient(row, booking.getRecipient());
		return row;
	}

	private void addRecipient(Object[] row, Recipient recipient) {
		if (recipient == null) {
			return;
		}
		row[10] = recipient.getName();
		row[11] = recipient.getIban();
		row[12] = recipient.getBic();
		row[13] = recipient.getAccountNumber();
		row[14] = recipient.getBlz();
		row[15] = recipient.getBank();
	}

	private void saveToExportFile(List<Object[]> csvLines, String fileName) throws IOException {

		Path exportPath = prepareExportPath(fileName);
		try (BufferedWriter csvWriter = Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8);
				CSVPrinter csvPrinter = new CSVPrinter(csvWriter, BookingCsvFormat.exportFormat())) {
			for (Object[] recordArray : csvLines) {
				csvPrinter.printRecord(recordArray);
			}
			csvPrinter.flush();
		}
		updateWorkerState(99, false, "UI_PROGRESS_FINISH");
	}

}
