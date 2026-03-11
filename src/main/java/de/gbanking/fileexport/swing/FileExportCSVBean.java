package de.gbanking.fileexport.swing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.swing.BaseWorker;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.exception.ExportException;
import de.gbanking.util.TypeConverter;

public class FileExportCSVBean extends FileExportBean {

	private static Logger log = LogManager.getLogger(FileExportCSVBean.class);

	public FileExportCSVBean(BaseWorker worker) {
		super(worker);
	}
	
	@Override
	public boolean exportFileFromDatatbase(List<BankAccount> accountList, String fileName) throws ExportException {

		boolean result = true;

		Set<Object[]> csvLines = null;
		try {
			csvLines = createCSVContent(accountList);
			saveToExportFile(csvLines, fileName);

		} catch (Exception e) {
			result = false;
			e.printStackTrace();
		}

		return result;
	}

	private Set<Object[]> createCSVContent(List<BankAccount> accountList) {

		Set<Object[]> csvLines = new HashSet<>();

		for (BankAccount account : accountList) {
			for (Booking booking : dbController.getAllByParentFull(Booking.class, account.getId())) {
				Object[] recordArray = null;

				Recipient recipient = booking.getRecipient();

				// @formatter:off
					recordArray = new Object[] { 
							booking.getSource(), 
							
							TypeConverter.toDateStringShort(booking.getDateBooking()), 
							TypeConverter.toDateStringShort(booking.getDateValue()),
							booking.getPurpose(), 
							booking.getAmountStr(), 
							booking.getCurrency(), 
							
							recipient.getName(), 
							recipient.getIban(), 
							recipient.getBic(),
							recipient.getNote(), 
							recipient.getSource(), 
							
							booking.getCategory(), 
							booking.getBookingType(),
							booking.getCrossAccountName(), 
							
							booking.getSepaCreditorId(),
							booking.getSepaCustomerRef(),
							booking.getSepaEndToEnd(),
							booking.getSepaMandate(),
							booking.getSepaPersonId(),
							booking.getSepaPurpose(),
							booking.getSepaTyp(),
							
							booking.getAccountName() 
							};
				// @formatter:on

				csvLines.add(recordArray);
			}
		}

		return csvLines;
	}

	private void saveToExportFile(Set<Object[]> csvLines, String fileName) {

		File file = new File(fileName);

		Builder builder = buildHeader();
		try (BufferedWriter csvWriter = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()));
				CSVPrinter csvPrinter = new CSVPrinter(csvWriter, builder.get());) {
			for (Object[] recordArray : csvLines) {
				csvPrinter.printRecord(recordArray);
			}
			csvPrinter.flush();
		} catch (IOException e) {
			log.error("Error writing CSV File {}", file.getName(), e);
		}
		log.info("CSV file was written successfully !!!");
	}

	private static Builder buildHeader() {
		Builder builder = Builder.create(CSVFormat.DEFAULT).setDelimiter(';');

		builder.setHeader(ExportConstants.DATE.toString(), ExportConstants.DATE_BOOKING.toString(), ExportConstants.DATE_VALUE.toString(),
				ExportConstants.NOTICE.toString(), ExportConstants.AMOUNT.toString(), ExportConstants.BIC.toString(),
				ExportConstants.ACCOUNT_TYPE.toString(), ExportConstants.REFERENCE_ACCOUNT_NAME.toString(),
				ExportConstants.SOURCE_FILENAME.toString(), ExportConstants.SOURCE_ACCOUNT.toString());
		return builder;
	}

}
