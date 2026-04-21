package de.gbanking.file.imp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.gbanking.BaseBean;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.exception.GBankingException;
import de.gbanking.gui.BaseWorker;
import de.gbanking.mapper.DaoMapper;
import de.zft2.fp3xmlextract.convert.BookingProcessor;
import de.zft2.fp3xmlextract.convert.Converter;

public class FileImportBean extends BaseBean {

	private static final Logger log = LogManager.getLogger(FileImportBean.class);

	private Collection<de.zft2.fp3xmlextract.data.BankAccount> xml2CsvKontoList;
	private Collection<de.zft2.fp3xmlextract.data.Booking> fp32CsvBookingList;

	private Map<String, Integer> accountIdMapByAccountname;
	private Map<String, Integer> crossAccountIdMapByIdentifier;

	private int totalAccounts = 0;
	private long totalBookings = 0L;

	private final BaseWorker worker;
	private double currentProgress = 0.0;
	private final Map<String, ImportAccountStatistics> importStatisticsByAccount = new LinkedHashMap<>();

	public FileImportBean(BaseWorker worker) {
		this.worker = worker;
	}

	private void updateWorkerStateAccounts(long importedCount, String message, Object... param) {
		updateWorkerState(importedCount, totalAccounts, 0, 10, message, param);
	}

	private void updateWorkerStateBookings(long importedCount, String message, Object... param) {
		updateWorkerState(importedCount, totalBookings, 10, 90, message, param);
	}

	private void updateWorkerState(long importedCount, long totalCount, int percentageStart, int percentageStop, String message, Object... param) {
		if (worker != null) {
			int progress;

			if (totalCount <= 0) {
				progress = percentageStart;
			} else {
				progress = (int) (importedCount / (double) totalCount * 100 * (percentageStop * 0.1));
				progress = progress < percentageStart ? progress + percentageStart : Math.min(progress, percentageStop);
			}

			currentProgress = progress;
			worker.setProcessingState(String.format(message, param));
			worker.setWorkerProgress(progress);
		}
	}

	private void updateWorkerState(int progress, boolean updateProgress, String message, Object... param) {
		if (worker != null) {
			worker.setProcessingState(String.format(message, param));

			currentProgress = updateProgress ? currentProgress + progress : progress;

			if (currentProgress < 0) {
				currentProgress = 0;
			} else if (currentProgress > 100) {
				currentProgress = 100;
			}

			worker.setWorkerProgress(currentProgress);
		}
	}

	public boolean importFile(String importFile) {
		boolean result = true;
		try {
			importFileToDatatbase(importFile);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			result = false;
			log.error("Error importing file: ", new GBankingException(e.getMessage(), true));
		}
		return result;
	}

	public void importFileToDatatbase(String importFile) throws ParserConfigurationException, SAXException, IOException {

		importStatisticsByAccount.clear();
		currentProgress = 0.0;
		updateWorkerState(1, false, "Konvertiere aus Datei: %s", importFile);

		long timeStart = System.currentTimeMillis();

		Converter converter = new Converter();

		if (!new File(importFile).exists()) {
			throw new GBankingException("File not found: " + importFile);
		}

		if (importFile.endsWith("fp3")) {
			fp32CsvBookingList = converter.convertFp3ToCsvEntries(importFile);
		} else {
			converter.checkAndCorrectInputFile(importFile);
			xml2CsvKontoList = converter.convertXmlToCsvEntries(importFile);
			BookingProcessor bookingProcessor = new BookingProcessor();
			try {
				bookingProcessor.generateCrossBookings(xml2CsvKontoList, false, 6);
			} catch (Exception e) {
				log.error("Error transforming XML cross bookings: ", e);
			}
		}

		log.info("Duration: {}s", (System.currentTimeMillis() - timeStart) / 1000);

		loadIntoDatabase();
	}

	private void loadIntoDatabase() {

		if (xml2CsvKontoList != null) {
			writeAccountsToDB(xml2CsvKontoList);
			writeBookingsToDB(xml2CsvKontoList);
		} else if (fp32CsvBookingList != null) {
			accountIdMapByAccountname = dbController.getAccountsIdsByAccountName();
			crossAccountIdMapByIdentifier = dbController.getCrossAccountsIdsByIbanOrNumber();
			String fallbackAccountName = resolveFallbackAccountName(fp32CsvBookingList);
			dbController.insertAccountBookings(DaoMapper.maptoBookingDaoList(fallbackAccountName, fp32CsvBookingList, accountIdMapByAccountname,
					crossAccountIdMapByIdentifier, Source.IMPORT_INITIAL));
		} else {
			log.error("xml2CsvKontoList and fp32CsvBookingList are both null!");
		}
	}

	private String resolveFallbackAccountName(Collection<de.zft2.fp3xmlextract.data.Booking> bookingList) {
		if (bookingList == null) {
			return null;
		}

		for (de.zft2.fp3xmlextract.data.Booking booking : bookingList) {
			if (booking != null && booking.getAccountNamePP() != null && !booking.getAccountNamePP().isBlank()) {
				return booking.getAccountNamePP();
			}
		}

		return null;
	}

	boolean writeAccountsToDB(Collection<de.zft2.fp3xmlextract.data.BankAccount> bankAccountList) {

		boolean result = false;

		totalAccounts = bankAccountList.size();
		int importedAccountsCount = 0;
		updateWorkerState(1, true, "Importiere Konten (Anzahl: %d)", totalAccounts);

		for (de.zft2.fp3xmlextract.data.BankAccount bankAccountXml : bankAccountList) {
			BankAccount bankAccount = DaoMapper.maptoBankAccountDao(bankAccountXml);
			if (bankAccountXml.getNamePP() == null) {
				bankAccountXml.setNamePP(bankAccount.getAccountName());
			}
			updateWorkerStateAccounts(importedAccountsCount++, "Importiere Buchungen für Konto: %s (Anzahl: %d)", bankAccount.getAccountName(), totalAccounts);
			result = dbController.insertOrUpdate(bankAccount) != null;
		}

		accountIdMapByAccountname = dbController.getAccountsIdsByAccountName();
		crossAccountIdMapByIdentifier = dbController.getCrossAccountsIdsByIbanOrNumber();

		log.info("{} Accounts writen to DB", totalAccounts);
		dbController.printAccountsInDB();

		return result;
	}

	boolean writeBookingsToDB(Collection<de.zft2.fp3xmlextract.data.BankAccount> bankAccountList) {

		boolean result = false;
		importStatisticsByAccount.clear();

		updateWorkerState(1, true, "Importiere Buchungen");

		Map<de.zft2.fp3xmlextract.data.Booking, Integer> crossBookingMap = new HashMap<>();

		totalBookings = bankAccountList.stream().flatMap(artifact -> artifact.getBookings().stream()).count();
		long importedBookingsCount = 0;

		Collection<Booking> allBookings = new ArrayList<>();

		for (de.zft2.fp3xmlextract.data.BankAccount bankAccountXml : bankAccountList) {
			String accountName = bankAccountXml.getNamePP();
			List<de.zft2.fp3xmlextract.data.Booking> bookingsList = bankAccountXml.getBookings();
			List<Booking> bookingDaoList = new ArrayList<>();
			Integer accountId = accountIdMapByAccountname.get(accountName);
			List<Booking> existingBookings = accountId == null ? List.of() : dbController.getAllByParentFull(Booking.class, accountId);
			ImportAccountStatistics accountStatistics = importStatisticsByAccount.computeIfAbsent(accountName,
					key -> new ImportAccountStatistics(accountName, existingBookings.size()));

			updateWorkerStateBookings(importedBookingsCount, "Importiere Buchungen für Konto: %s (Anzahl: %d)", accountName, bookingsList.size());

			for (de.zft2.fp3xmlextract.data.Booking xmlBooking : bookingsList) {
				Booking bookingDao = DaoMapper.maptoBookingDao(accountName, xmlBooking, accountIdMapByAccountname, crossAccountIdMapByIdentifier,
						Source.IMPORT_INITIAL);
				Booking existingBooking = findMatchingBooking(existingBookings, bookingDao);
				boolean existing = existingBooking != null;
				boolean updated = false;
				Booking resolvedBooking = existing ? existingBooking : dbController.insertOrUpdate(bookingDao);

				if (xmlBooking.getCrossBooking() != null && crossBookingMap.get(xmlBooking) == null && resolvedBooking != null) {
					crossBookingMap.put(xmlBooking.getCrossBooking(), resolvedBooking.getId());
				}

				if (resolvedBooking != null && crossBookingMap.get(xmlBooking) != null) {
					Booking crossBookingDao = dbController.getById(Booking.class, crossBookingMap.get(xmlBooking));
					DaoMapper.setCrossBooking(crossBookingDao, resolvedBooking.getId());
					dbController.insertOrUpdate(crossBookingDao);
					resolvedBooking.setCrossBookingId(crossBookingDao.getId());
					dbController.insertOrUpdate(resolvedBooking);
					updated = existing;
				}

				if (updated) {
					accountStatistics.incrementUpdated();
				} else if (existing) {
					accountStatistics.incrementSkipped();
				} else if (resolvedBooking != null) {
					bookingDaoList.add(resolvedBooking);
					accountStatistics.incrementAdded();
				}
				importedBookingsCount++;
			}

			log.info("Account: {}: {} Bookings writen to DB, {} skipped as duplicates", bankAccountXml.getNamePP(), bookingDaoList.size(),
					accountStatistics.getSkippedBookings());
			allBookings.addAll(bookingDaoList);
		}

		updateWorkerState(1, true, "Importiere Kontakte aus Buchungen");
		writeRecipientsToDB(allBookings);

		updateWorkerState(1, true, "Importiere Kategorien aus Buchungen");
		writeCategoriesToDB(allBookings);

		updateWorkerState(99, false, "beende...");

		return result;
	}

	private Booking findMatchingBooking(Collection<Booking> existingBookings, Booking bookingToMatch) {
		if (bookingToMatch == null || existingBookings == null) {
			return null;
		}

		for (Booking existingBooking : existingBookings) {
			if (bookingToMatch.equals(existingBooking)) {
				return existingBooking;
			}
		}

		return null;
	}

	String getImportSummaryText() {
		if (importStatisticsByAccount.isEmpty()) {
			return "";
		}

		StringBuilder summary = new StringBuilder(getText("UI_IMPORT_SUMMARY_HEADER"));
		for (ImportAccountStatistics statistics : importStatisticsByAccount.values()) {
			summary.append(System.lineSeparator())
					.append(getText("UI_IMPORT_SUMMARY_ACCOUNT", statistics.getAccountName(), Integer.toString(statistics.getExistingBookings()),
							Integer.toString(statistics.getAddedBookings()), Integer.toString(statistics.getUpdatedBookings()),
							Integer.toString(statistics.getSkippedBookings()), Integer.toString(statistics.getTotalBookings())));
		}
		return summary.toString();
	}

	private int writeRecipientsToDB(Collection<Booking> bookingDaoList) {

		int result = 0;

		Map<Recipient, Set<Integer>> recipientBookingMap = new HashMap<>();
		Set<Recipient> recipientNewList = new HashSet<>();

		for (Booking booking : bookingDaoList) {
			Recipient recipient = booking.getRecipient();
			if (recipient != null) {
				Recipient recipientDb = dbController.find(Recipient.class, recipient);
				if (recipientDb == null) {
					recipientNewList.add(recipient);
				} else {
					recipient = recipientDb;
				}
				Set<Integer> existingBookingIds = recipientBookingMap.get(recipient);
				if (existingBookingIds == null) {
					recipientBookingMap.put(recipient, new HashSet<>(Arrays.asList(booking.getId())));
				} else {
					existingBookingIds.add(booking.getId());
					recipientBookingMap.put(recipient, existingBookingIds);
				}
			}
		}

		dbController.insertAll(recipientNewList);
		dbController.updateBookingsWithRecipients(recipientBookingMap);

		return result;
	}

	private int writeCategoriesToDB(Collection<Booking> bookingDaoList) {

		int result = 0;

		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();

		for (Booking booking : bookingDaoList) {
			Category category = booking.getCategory();
			if (category != null) {
				Set<Integer> existingBookingIds = categoryBookingMap.get(category);
				if (existingBookingIds == null) {
					categoryBookingMap.put(category, new HashSet<>(Arrays.asList(booking.getId())));
				} else {
					existingBookingIds.add(booking.getId());
					categoryBookingMap.put(category, existingBookingIds);
				}
			}
		}

		dbController.insertAll(categoryBookingMap.keySet());
		dbController.updateBookingsWithCategories(categoryBookingMap);

		return result;
	}

	private static final class ImportAccountStatistics {

		private final String accountName;
		private final int existingBookings;
		private int addedBookings;
		private int skippedBookings;
		private int updatedBookings;

		private ImportAccountStatistics(String accountName, int existingBookings) {
			this.accountName = accountName;
			this.existingBookings = existingBookings;
		}

		public void incrementUpdated() {
			updatedBookings++;
		}

		private void incrementAdded() {
			addedBookings++;
		}

		private void incrementSkipped() {
			skippedBookings++;
		}

		private String getAccountName() {
			return accountName;
		}

		private int getExistingBookings() {
			return existingBookings;
		}

		private int getAddedBookings() {
			return addedBookings;
		}

		private int getSkippedBookings() {
			return skippedBookings;
		}

		public int getUpdatedBookings() {
			return updatedBookings;
		}

		private int getTotalBookings() {
			return existingBookings + addedBookings;
		}
	}
}
