package de.zft2.gbanking.file.imp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import de.zft2.fp3xmlextract.convert.Converter;
import de.zft2.fp3xmlextract.convert.Fp3XmlBookingProcessor;
import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.mapper.ImportDaoMapper;
import de.zft2.gbanking.rebooking.RebookingRules;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.util.AppPaths;

public class FileImportBean implements BaseMessagesDb {

	private static final Logger log = LogManager.getLogger(FileImportBean.class);
	private static final int PROGRESS_ACCOUNTS_START = 0;
	private static final int PROGRESS_ACCOUNTS_END = 5;
	private static final int PROGRESS_BOOKINGS_START = PROGRESS_ACCOUNTS_END;
	private static final int PROGRESS_BOOKINGS_END = 90;
	private static final int PROGRESS_RECIPIENTS_START = PROGRESS_BOOKINGS_END;
	private static final int PROGRESS_CATEGORIES_START = 98;
	private static final int PROGRESS_FINISHED = 100;

	private Collection<de.zft2.fp3xmlextract.data.Fp3XmlBankAccount> xml2CsvKontoList;
	private Collection<de.zft2.fp3xmlextract.data.Fp3XmlBooking> fp32CsvBookingList;

	private Map<String, Integer> accountIdMapByAccountname;
	private Map<String, Integer> crossAccountIdMapByIdentifier;

	private int totalAccounts = 0;
	private long totalBookings = 0L;

	private final BaseWorker worker;
	private final BankAccount contextAccount;
	private final boolean forceFp3Import;
	private final ImportedBookingReferenceWriter bookingReferenceWriter;
	private final ImportStatisticsCollector importStatistics = new ImportStatisticsCollector();
	private int currentProgress = -1;

	public FileImportBean(BaseWorker worker) {
		this(worker, null, false);
	}

	public FileImportBean(BaseWorker worker, BankAccount contextAccount, boolean forceFp3Import) {
		this.worker = worker;
		this.contextAccount = contextAccount;
		this.forceFp3Import = forceFp3Import;
		this.bookingReferenceWriter = new ImportedBookingReferenceWriter(dbController);
	}

	private void updateWorkerStateAccounts(long importedCount, String messageKey, Object... param) {
		updateWorkerState(importedCount, totalAccounts, PROGRESS_ACCOUNTS_START, PROGRESS_ACCOUNTS_END, messageKey, param);
	}

	private void updateWorkerStateBookings(long importedCount, String messageKey, Object... param) {
		updateWorkerState(importedCount, totalBookings, PROGRESS_BOOKINGS_START, PROGRESS_BOOKINGS_END, messageKey, param);
	}

	private void updateWorkerState(long importedCount, long totalCount, int percentageStart, int percentageStop, String messageKey, Object... param) {
		updateWorkerState(calculateProgress(importedCount, totalCount, percentageStart, percentageStop), messageKey, param);
	}

	private int calculateProgress(long importedCount, long totalCount, int percentageStart, int percentageStop) {
		if (totalCount <= 0) {
			return percentageStop;
		}
		long boundedCount = Math.max(0L, Math.min(importedCount, totalCount));
		double completedFraction = boundedCount / (double) totalCount;
		return percentageStart + (int) Math.round((percentageStop - percentageStart) * completedFraction);
	}

	private void updateWorkerState(int progress, String messageKey, Object... param) {
		if (worker == null) {
			return;
		}
		worker.setProcessingState(getText(messageKey, param));
		updateWorkerProgress(progress);
	}

	private void updateWorkerProgress(int progress) {
		if (worker == null) {
			return;
		}
		int boundedProgress = Math.max(0, Math.min(progress, PROGRESS_FINISHED));
		if (boundedProgress != currentProgress) {
			currentProgress = boundedProgress;
			worker.setWorkerProgress(boundedProgress);
		}
	}

	private void updateAccountProgress(long importedCount) {
		updateWorkerProgress(calculateProgress(importedCount, totalAccounts, PROGRESS_ACCOUNTS_START, PROGRESS_ACCOUNTS_END));
	}

	private void updateBookingProgress(long importedCount) {
		updateWorkerProgress(calculateProgress(importedCount, totalBookings, PROGRESS_BOOKINGS_START, PROGRESS_BOOKINGS_END));
	}

	public boolean importFile(String importFile) {
		boolean result = true;
		try {
			importFileToDatatbase(importFile);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			result = false;
			log.error("Error importing XML/FP3 file {}", fileName(importFile), new GBankingException(e.getMessage(), true));
		}
		return result;
	}

	public void importFileToDatatbase(String importFile) throws ParserConfigurationException, SAXException, IOException {

		importStatistics.clear();
		currentProgress = -1;
		updateWorkerState(PROGRESS_ACCOUNTS_START, "UI_PROGRESS_IMPORT_CONVERT_FILE", importFile);

		long timeStart = System.currentTimeMillis();
		Path importPath = AppPaths.resolveInApplicationDirectory(importFile);
		String importFilePath = importPath.toString();
		log.info("Starting XML/FP3 booking import. file={}", importPath.getFileName());
		log.debug("XML/FP3 booking import path: {}", importPath);

		if (!Files.exists(importPath)) {
			throw new GBankingException("File not found: " + importFile);
		}

		Converter converter = new Converter();

		xml2CsvKontoList = null;
		fp32CsvBookingList = null;

		if (isFp3Import(importFilePath)) {
			fp32CsvBookingList = converter.convertFp3ToCsvEntries(importFilePath);
		} else {
			converter.checkAndCorrectInputFile(importFilePath);
			xml2CsvKontoList = converter.convertXmlToCsvEntries(importFilePath);
			Fp3XmlBookingProcessor bookingProcessor = new Fp3XmlBookingProcessor();
			try {
				bookingProcessor.revertCancellationRebookings(xml2CsvKontoList);
				bookingProcessor.generateCrossBookings(xml2CsvKontoList, false, 6);
			} catch (Exception e) {
				log.error("Error transforming XML cross bookings: ", e);
			}
		}

		loadIntoDatabase();
		log.info("Finished XML/FP3 booking import. file={}, durationSeconds={}, {}", importPath::getFileName,
				() -> (System.currentTimeMillis() - timeStart) / 1000, importStatistics::summary);
	}

	private boolean isFp3Import(String importFilePath) {
		return forceFp3Import || importFilePath.toLowerCase(Locale.ROOT).endsWith(".fp3");
	}

	private void loadIntoDatabase() {

		if (xml2CsvKontoList != null) {
			long bookingCount = countBookings(xml2CsvKontoList);
			writeAccountsToDB(xml2CsvKontoList);
			ServiceRegistry.getService(ImportPropertiesSynchronizationService.class).initializeAndSynchronize();
			writeBookingsToDB(xml2CsvKontoList, bookingCount);
		} else if (fp32CsvBookingList != null) {
			accountIdMapByAccountname = dbController.getAccountsIdsByAccountName();
			crossAccountIdMapByIdentifier = dbController.getCrossAccountsIdsByIbanOrNumber();
			addContextAccountToLookupMaps();
			String fallbackAccountName = resolveFallbackAccountName(fp32CsvBookingList);
			if (fallbackAccountName == null && contextAccount != null) {
				fallbackAccountName = contextAccount.getAccountName();
			}
			Collection<Booking> bookingDaoList = ImportDaoMapper.maptoBookingDaoList(fallbackAccountName, fp32CsvBookingList, accountIdMapByAccountname,
					crossAccountIdMapByIdentifier, Source.IMPORT_INITIAL);
			postProcessImportedBookings(persistImportedBookings(bookingDaoList));
			updateWorkerState(PROGRESS_FINISHED, "UI_PROGRESS_FINISH");
		} else {
			log.error("xml2CsvKontoList and fp32CsvBookingList are both null!");
		}
	}

	private void addContextAccountToLookupMaps() {
		if (contextAccount == null) {
			return;
		}
		putIfPresent(accountIdMapByAccountname, contextAccount.getAccountName(), contextAccount.getId());
		putIfPresent(crossAccountIdMapByIdentifier, contextAccount.getIban(), contextAccount.getId());
		putIfPresent(crossAccountIdMapByIdentifier, contextAccount.getNumber(), contextAccount.getId());
	}

	private String resolveFallbackAccountName(Collection<de.zft2.fp3xmlextract.data.Fp3XmlBooking> bookingList) {
		if (bookingList == null) {
			return null;
		}

		for (de.zft2.fp3xmlextract.data.Fp3XmlBooking booking : bookingList) {
			if (booking != null && booking.getAccountName() != null && !booking.getAccountName().isBlank()) {
				return booking.getAccountName();
			}
		}

		return null;
	}

	boolean writeAccountsToDB(Collection<de.zft2.fp3xmlextract.data.Fp3XmlBankAccount> bankAccountList) {

		boolean result = false;
		Map<String, Integer> accountIdsByName = dbController.getAccountsIdsByAccountName();
		Map<String, Integer> accountIdsByIdentifier = dbController.getCrossAccountsIdsByIbanOrNumber();

		totalAccounts = bankAccountList.size();
		int importedAccountsCount = 0;
		updateWorkerStateAccounts(importedAccountsCount, "UI_PROGRESS_IMPORT_ACCOUNTS", totalAccounts);

		for (de.zft2.fp3xmlextract.data.Fp3XmlBankAccount bankAccountXml : bankAccountList) {
			BankAccount bankAccount = ImportDaoMapper.maptoBankAccountDao(bankAccountXml, Source.IMPORT_INITIAL);
			normalizeBankAccount(bankAccount);
			if (bankAccountXml.getNamePP() == null) {
				bankAccountXml.setNamePP(bankAccount.getAccountName());
			}
			Integer existingAccountId = resolveExistingAccountId(bankAccount, accountIdsByIdentifier, accountIdsByName);
			if (existingAccountId != null) {
				bankAccount.setId(existingAccountId);
			}
			updateWorkerStateAccounts(importedAccountsCount, "UI_PROGRESS_IMPORT_ACCOUNT", bankAccount.getAccountName());
			BankAccount persistedAccount = dbController.insertOrUpdate(bankAccount);
			result = persistedAccount != null;
			updateAccountLookupMaps(accountIdsByName, accountIdsByIdentifier, persistedAccount);
			updateAccountProgress(++importedAccountsCount);
		}

		accountIdMapByAccountname = dbController.getAccountsIdsByAccountName();
		crossAccountIdMapByIdentifier = dbController.getCrossAccountsIdsByIbanOrNumber();

		log.info("{} accounts written to DB during import.", totalAccounts);
		dbController.printAccountsInDB();

		return result;
	}

	private long countBookings(Collection<de.zft2.fp3xmlextract.data.Fp3XmlBankAccount> bankAccountList) {
		long bookingCount = 0L;
		for (de.zft2.fp3xmlextract.data.Fp3XmlBankAccount bankAccount : bankAccountList) {
			List<de.zft2.fp3xmlextract.data.Fp3XmlBooking> bookings = bankAccount.getBookings();
			if (bookings != null) {
				bookingCount += bookings.size();
			}
		}
		return bookingCount;
	}

	private void normalizeBankAccount(BankAccount bankAccount) {
		if (bankAccount == null) {
			return;
		}

		bankAccount.setAccountName(normalizeText(bankAccount.getAccountName()));
		bankAccount.setIban(normalizeText(bankAccount.getIban()));
		bankAccount.setNumber(normalizeText(bankAccount.getNumber()));
	}

	private Integer resolveExistingAccountId(BankAccount bankAccount, Map<String, Integer> accountIdsByIdentifier, Map<String, Integer> accountIdsByName) {
		if (bankAccount == null) {
			return null;
		}

		Integer existingAccountId = lookupAccountId(accountIdsByIdentifier, bankAccount.getIban());
		if (existingAccountId != null) {
			return existingAccountId;
		}

		existingAccountId = lookupAccountId(accountIdsByIdentifier, bankAccount.getNumber());
		if (existingAccountId != null) {
			return existingAccountId;
		}

		return lookupAccountId(accountIdsByName, bankAccount.getAccountName());
	}

	private Integer lookupAccountId(Map<String, Integer> accountIds, String key) {
		String normalizedKey = normalizeText(key);
		if (accountIds == null || normalizedKey == null) {
			return null;
		}
		return accountIds.get(normalizedKey);
	}

	private void updateAccountLookupMaps(Map<String, Integer> accountIdsByName, Map<String, Integer> accountIdsByIdentifier, BankAccount bankAccount) {
		if (bankAccount == null || bankAccount.getId() <= 0) {
			return;
		}

		putIfPresent(accountIdsByName, bankAccount.getAccountName(), bankAccount.getId());
		putIfPresent(accountIdsByIdentifier, bankAccount.getIban(), bankAccount.getId());
		putIfPresent(accountIdsByIdentifier, bankAccount.getNumber(), bankAccount.getId());
	}

	private void putIfPresent(Map<String, Integer> accountIds, String key, Integer id) {
		String normalizedKey = normalizeText(key);
		if (accountIds != null && normalizedKey != null && id != null) {
			accountIds.put(normalizedKey, id);
		}
	}

	private String normalizeText(String value) {
		if (value == null) {
			return null;
		}

		String normalizedValue = value.trim();
		return normalizedValue.isEmpty() ? null : normalizedValue;
	}

	boolean writeBookingsToDB(Collection<de.zft2.fp3xmlextract.data.Fp3XmlBankAccount> bankAccountList) {
		return writeBookingsToDB(bankAccountList, countBookings(bankAccountList));
	}

	private boolean writeBookingsToDB(Collection<de.zft2.fp3xmlextract.data.Fp3XmlBankAccount> bankAccountList, long bookingCount) {

		importStatistics.clear();

		Map<de.zft2.core.dto.Booking, Integer> crossBookingMap = new HashMap<>();

		totalBookings = bookingCount;
		long importedBookingsCount = 0;
		updateWorkerStateBookings(importedBookingsCount, "UI_PROGRESS_IMPORT_BOOKINGS");

		Collection<Booking> allBookings = new ArrayList<>();

		for (de.zft2.fp3xmlextract.data.Fp3XmlBankAccount bankAccountXml : bankAccountList) {
			String accountName = bankAccountXml.getNamePP();
			List<de.zft2.fp3xmlextract.data.Fp3XmlBooking> bookingsList = bankAccountXml.getBookings();
			List<Booking> bookingDaoList = new ArrayList<>();
			Integer accountId = accountIdMapByAccountname.get(accountName);
			List<Booking> existingBookings = accountId == null ? List.of() : dbController.getAllByParentFull(Booking.class, accountId);
			ImportAccountStatistics accountStatistics = importStatistics.forAccount(accountName, existingBookings.size());

			updateWorkerStateBookings(importedBookingsCount, "UI_PROGRESS_IMPORT_BOOKINGS_ACCOUNT_COUNT", accountName, bookingsList.size());

			importedBookingsCount = writeBookingsForAccount(crossBookingMap, accountName, bookingsList, bookingDaoList, existingBookings, accountStatistics,
					importedBookingsCount);

			log.info("Imported bookings for accountId={}: written={}, skippedDuplicates={}", accountId, bookingDaoList.size(),
					accountStatistics.getSkippedBookings());
			allBookings.addAll(bookingDaoList);
		}

		postProcessImportedBookings(allBookings);

		updateWorkerState(PROGRESS_FINISHED, "UI_PROGRESS_FINISH");

		return true;
	}

	private long writeBookingsForAccount(Map<de.zft2.core.dto.Booking, Integer> crossBookingMap, String accountName,
			List<de.zft2.fp3xmlextract.data.Fp3XmlBooking> bookingsList, List<Booking> bookingDaoList, List<Booking> existingBookings,
			ImportAccountStatistics accountStatistics, long importedBookingsCount) {

		for (de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking : bookingsList) {
			ImportedBookingResult importResult = writeBookingForAccount(crossBookingMap, accountName, existingBookings, xmlBooking);
			applyImportResult(bookingDaoList, accountStatistics, importResult);
			updateBookingProgress(++importedBookingsCount);
		}
		return importedBookingsCount;
	}

	private ImportedBookingResult writeBookingForAccount(Map<de.zft2.core.dto.Booking, Integer> crossBookingMap, String accountName,
			List<Booking> existingBookings, de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking) {

		int accountId = ImportedAccountResolver.resolveAccountId(accountName, xmlBooking, accountIdMapByAccountname);
		Integer crossAccountId = ImportedAccountResolver.resolveCrossAccountId(xmlBooking, accountId, accountIdMapByAccountname,
				crossAccountIdMapByIdentifier);
		Booking bookingDao = ImportDaoMapper.maptoBookingDao(xmlBooking, accountId, crossAccountId, Source.IMPORT_INITIAL);
		Booking existingBooking = ImportedBookingMatcher.findMatchingBooking(existingBookings, bookingDao);
		boolean existing = existingBooking != null;
		Booking resolvedBooking = existing ? existingBooking : dbController.insertOrUpdate(bookingDao);

		registerPendingCrossBooking(crossBookingMap, xmlBooking, resolvedBooking);
		boolean updated = linkCrossBookingIfPossible(crossBookingMap, xmlBooking, resolvedBooking) && existing;
		return new ImportedBookingResult(existing, updated, resolvedBooking);
	}

	private void registerPendingCrossBooking(Map<de.zft2.core.dto.Booking, Integer> crossBookingMap,
			de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking, Booking resolvedBooking) {

		if (xmlBooking.getCrossBooking() != null && crossBookingMap.get(xmlBooking) == null && resolvedBooking != null) {
			crossBookingMap.put(xmlBooking.getCrossBooking(), resolvedBooking.getId());
		}
	}

	private boolean linkCrossBookingIfPossible(Map<de.zft2.core.dto.Booking, Integer> crossBookingMap,
			de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking, Booking resolvedBooking) {

		Integer crossBookingId = crossBookingMap.get(xmlBooking);
		if (resolvedBooking == null || crossBookingId == null) {
			return false;
		}

		Booking crossBookingDao = dbController.getById(Booking.class, crossBookingId);
		if (crossBookingDao == null || !canLinkCrossBooking(resolvedBooking, crossBookingDao)) {
			return false;
		}

		ImportDaoMapper.setCrossBooking(crossBookingDao, resolvedBooking.getId());
		dbController.insertOrUpdate(crossBookingDao);
		resolvedBooking.setCrossBookingId(crossBookingDao.getId());
		dbController.insertOrUpdate(resolvedBooking);
		return true;
	}

	private void applyImportResult(List<Booking> bookingDaoList, ImportAccountStatistics accountStatistics, ImportedBookingResult importResult) {
		if (importResult.updated()) {
			accountStatistics.incrementUpdated();
		} else if (importResult.existing()) {
			accountStatistics.incrementSkipped();
		} else if (importResult.resolvedBooking() != null) {
			bookingDaoList.add(importResult.resolvedBooking());
			accountStatistics.incrementAdded();
		}
	}

	private record ImportedBookingResult(boolean existing, boolean updated, Booking resolvedBooking) {
	}

	private boolean canLinkCrossBooking(Booking booking, Booking crossBooking) {
		return !RebookingRules.isForbiddenSameAccountRebooking(booking.getAccountId(), crossBooking.getAccountId(),
				isCancellation(booking) || isCancellation(crossBooking));
	}

	private boolean isCancellation(Booking booking) {
		if (booking == null) {
			return false;
		}
		BookingAdditionalDetails details = booking.getAdditionalDetails();
		return booking.getBookingType() == BookingType.CANCEL || (details != null && Boolean.TRUE.equals(details.getStorno()));
	}

	private Collection<Booking> persistImportedBookings(Collection<Booking> bookingDaoList) {
		Collection<Booking> persistedBookings = new ArrayList<>();
		totalBookings = bookingDaoList.size();
		long importedBookingsCount = 0L;
		updateWorkerStateBookings(importedBookingsCount, "UI_PROGRESS_IMPORT_BOOKINGS");

		for (Booking booking : bookingDaoList) {
			Booking persistedBooking = dbController.insertOrUpdate(booking);
			if (persistedBooking != null) {
				persistedBookings.add(persistedBooking);
			}
			updateBookingProgress(++importedBookingsCount);
		}

		return persistedBookings;
	}

	private void postProcessImportedBookings(Collection<Booking> importedBookings) {
		if (importedBookings == null || importedBookings.isEmpty()) {
			return;
		}

		updateWorkerState(PROGRESS_RECIPIENTS_START, "UI_PROGRESS_IMPORT_CONTACTS");
		bookingReferenceWriter.writeRecipients(importedBookings);

		updateWorkerState(PROGRESS_CATEGORIES_START, "UI_PROGRESS_IMPORT_CATEGORIES");
		bookingReferenceWriter.writeCategories(importedBookings);
	}

	String getImportSummaryText() {
		if (importStatistics.isEmpty()) {
			return "";
		}

		StringBuilder summary = new StringBuilder(getText("UI_IMPORT_SUMMARY_HEADER"));
		for (ImportAccountStatistics statistics : importStatistics.asList()) {
			summary.append(System.lineSeparator())
					.append(getText("UI_IMPORT_SUMMARY_ACCOUNT", statistics.getAccountName(), Integer.toString(statistics.getExistingBookings()),
							Integer.toString(statistics.getAddedBookings()), Integer.toString(statistics.getUpdatedBookings()),
							Integer.toString(statistics.getSkippedBookings()), Integer.toString(statistics.getTotalBookings())));
		}
		return summary.toString();
	}

	List<ImportAccountStatistics> getImportStatistics() {
		return importStatistics.asList();
	}

	private String fileName(String fileName) {
		if (fileName == null) {
			return null;
		}
		Path path = Path.of(fileName);
		Path pathFileName = path.getFileName();
		return pathFileName != null ? pathFileName.toString() : fileName;
	}

	public static final class ImportAccountStatistics {

		private final String accountName;
		private final int existingBookings;
		private int addedBookings;
		private int skippedBookings;
		private int updatedBookings;

		ImportAccountStatistics(String accountName, int existingBookings) {
			this.accountName = accountName;
			this.existingBookings = existingBookings;
		}

		public void incrementUpdated() {
			updatedBookings++;
		}

		void incrementAdded() {
			addedBookings++;
		}

		void incrementSkipped() {
			skippedBookings++;
		}

		public String getAccountName() {
			return accountName;
		}

		public int getExistingBookings() {
			return existingBookings;
		}

		public int getAddedBookings() {
			return addedBookings;
		}

		public int getSkippedBookings() {
			return skippedBookings;
		}

		public int getUpdatedBookings() {
			return updatedBookings;
		}

		public int getTotalBookings() {
			return existingBookings + addedBookings;
		}
	}
}
