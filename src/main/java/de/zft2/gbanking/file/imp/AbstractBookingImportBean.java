package de.zft2.gbanking.file.imp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.core.dto.Booking.Typ;
import de.zft2.core.dto.Counterpart;
import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.mapper.ImportDaoMapper;
import de.zft2.gbanking.rebooking.RebookingRules;
import de.zft2.gbanking.service.importproperties.ImportPropertiesSynchronizationService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.util.AppPaths;

abstract class AbstractBookingImportBean implements BaseMessagesDb {

	private static final Logger log = LogManager.getLogger(AbstractBookingImportBean.class);

	private final BaseWorker worker;
	private final BankAccount contextAccount;
	private final ImportedBookingReferenceWriter bookingReferenceWriter;
	private final ImportStatisticsCollector importStatistics = new ImportStatisticsCollector();
	private final Map<Integer, List<Booking>> existingBookingsByAccountId = new HashMap<>();
	private final Map<String, BankAccount> accountsByIban = new HashMap<>();
	private final Map<String, BankAccount> accountsByNumber = new HashMap<>();
	private final Map<String, BankAccount> accountsByName = new HashMap<>();
	private final Map<String, BankAccount> accountsByBlzAndNumber = new HashMap<>();

	protected AbstractBookingImportBean(BaseWorker worker, BankAccount contextAccount) {
		this.worker = worker;
		this.contextAccount = contextAccount;
		this.bookingReferenceWriter = new ImportedBookingReferenceWriter(dbController);
	}

	protected Path prepareImportFile(String importFile, String messageKey) {
		importStatistics.clear();
		existingBookingsByAccountId.clear();
		updateWorkerState(1, messageKey, importFile);

		Path importPath = AppPaths.resolveInApplicationDirectory(importFile);
		log.info("Starting booking import. file={}", importPath.getFileName());
		log.debug("Booking import path: {}", importPath);
		if (!Files.exists(importPath)) {
			throw new GBankingException("File not found: " + importFile);
		}

		loadAccountLookupMaps();
		return importPath;
	}

	protected void finishImport() {
		updateWorkerState(100, "UI_PROGRESS_FINISH");
		log.info("Finished booking import. {}", importStatistics::summary);
	}

	protected boolean hasContextAccount() {
		return contextAccount != null;
	}

	protected BankAccount getContextAccount() {
		return contextAccount;
	}

	protected void registerAccount(BankAccount account) {
		if (account == null) {
			return;
		}
		putAccount(accountsByIban, account.getIban(), account);
		putAccount(accountsByNumber, account.getNumber(), account);
		putAccount(accountsByName, account.getAccountName(), account);
		putAccount(accountsByBlzAndNumber, blzAndNumber(account.getBlz(), account.getNumber()), account);
	}

	protected BankAccount lookupByIban(String iban) {
		return lookup(accountsByIban, iban);
	}

	protected BankAccount lookupByIbanSuffix(String iban) {
		String normalizedIban = normalizeKey(iban);
		if (normalizedIban == null) {
			return null;
		}
		for (Map.Entry<String, BankAccount> entry : accountsByNumber.entrySet()) {
			if (normalizedIban.endsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	protected BankAccount lookupByNumber(String number) {
		return lookup(accountsByNumber, number);
	}

	protected BankAccount lookupByName(String name) {
		return lookup(accountsByName, name);
	}

	protected BankAccount lookupByBlzAndNumber(String blz, String number) {
		return lookup(accountsByBlzAndNumber, blzAndNumber(blz, number));
	}

	protected String blzAndNumber(String blz, String number) {
		String normalizedBlz = normalizeKey(blz);
		String normalizedNumber = normalizeKey(number);
		return normalizedBlz != null && normalizedNumber != null ? normalizedBlz + "/" + normalizedNumber : null;
	}

	protected boolean matchesIdentifier(String left, String right) {
		String normalizedLeft = normalizeKey(left);
		String normalizedRight = normalizeKey(right);
		return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
	}

	protected boolean matchesIbanSuffix(String iban, String accountNumber) {
		String normalizedIban = normalizeKey(iban);
		String normalizedAccountNumber = normalizeKey(accountNumber);
		return normalizedIban != null && normalizedAccountNumber != null && normalizedIban.endsWith(normalizedAccountNumber);
	}

	protected boolean importBooking(BankAccount account, Booking booking, List<Booking> importedBookings) {
		return importBookingAndReturn(account, booking, importedBookings) != null;
	}

	protected Booking importBookingAndReturn(BankAccount account, Booking booking, List<Booking> importedBookings) {
		List<Booking> existingBookings = existingBookingsFor(account);
		FileImportBean.ImportAccountStatistics statistics = importStatistics.forAccount(account.getAccountName(), existingBookings.size());

		if (ImportedBookingMatcher.findMatchingBooking(existingBookings, booking) != null) {
			statistics.incrementSkipped();
			return null;
		}

		Booking persistedBooking = dbController.insertOrUpdate(booking);
		if (persistedBooking == null) {
			return null;
		}

		existingBookings.add(persistedBooking);
		if (importedBookings != null) {
			importedBookings.add(persistedBooking);
		}
		statistics.incrementAdded();
		return persistedBooking;
	}

	protected void importBookings(Collection<ImportBankAccount> importAccounts, Map<ImportBankAccount, BankAccount> accountsByImportAccount,
			List<Booking> importedBookings) {
		if (importAccounts == null || importAccounts.isEmpty()) {
			return;
		}

		processBookingsWithBookingCore(importAccounts);
		Map<de.zft2.core.dto.Booking, Integer> crossBookingMap = new IdentityHashMap<>();

		for (ImportBankAccount importAccount : importAccounts) {
			BankAccount account = accountsByImportAccount.get(importAccount);
			if (account == null) {
				throw new GBankingException("No database account resolved for imported account " + importAccount.getAccountName() + ".");
			}
			List<Booking> existingBookings = existingBookingsFor(account);
			FileImportBean.ImportAccountStatistics statistics = importStatistics.forAccount(account.getAccountName(), existingBookings.size());

			for (ImportBooking importBooking : importAccount.getBookings()) {
				Booking bookingDao = ImportDaoMapper.maptoBookingDao(importBooking, account.getId(), resolveCrossAccountId(importBooking, account.getId()),
						importBooking.getSource(), account.getBaseCurrency());
				importBooking(crossBookingMap, importBooking, bookingDao, existingBookings, importedBookings, statistics);
			}
		}
	}

	protected ImportBankAccount toImportAccount(BankAccount account) {
		ImportBankAccount importAccount = new ImportBankAccount();
		importAccount.setAccountName(account.getAccountName());
		importAccount.setNamePP(account.getAccountName());
		importAccount.setIban(account.getIban());
		importAccount.setBic(account.getBic());
		importAccount.setNumber(account.getNumber());
		importAccount.setSubnumber(account.getSubnumber());
		importAccount.setBankName(account.getBankName());
		importAccount.setBlz(account.getBlz());
		importAccount.setOwnerName(account.getOwnerName());
		importAccount.setOwnerName2(account.getOwnerName2());
		importAccount.setCountry(account.getCountry());
		importAccount.setCurrency(account.getCurrency());
		importAccount.setBalance(account.getBalance());
		importAccount.setAccountType(account.getAccountType());
		importAccount.setAccountState(account.getAccountState());
		importAccount.setSource(account.getSource());
		importAccount.setOfflineAccount(account.isOfflineAccount());
		return importAccount;
	}

	protected void postProcessImportedBookings(List<Booking> importedBookings) {
		if (importedBookings == null || importedBookings.isEmpty()) {
			return;
		}
		bookingReferenceWriter.writeRecipients(importedBookings);
		bookingReferenceWriter.writeCategories(importedBookings);
	}

	protected int progress(int current, int total) {
		if (total <= 0) {
			return 10;
		}
		return 10 + (int) (current / (double) total * 80);
	}

	protected void updateWorkerState(int progress, String messageKey, Object... param) {
		if (worker != null) {
			worker.setProcessingState(getText(messageKey, param));
			worker.setWorkerProgress(progress);
		}
	}

	protected String normalizeKey(String value) {
		return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
	}

	protected String fileName(String fileName) {
		if (fileName == null) {
			return null;
		}
		Path path = Path.of(fileName);
		Path pathFileName = path.getFileName();
		return pathFileName != null ? pathFileName.toString() : fileName;
	}

	private void processBookingsWithBookingCore(Collection<ImportBankAccount> importAccounts) {
		try {
			ServiceRegistry.getService(ImportPropertiesSynchronizationService.class).initializeAndSynchronize();
			ImportBookingProcessor bookingProcessor = new ImportBookingProcessor();
			bookingProcessor.revertCancellationRebookings(importAccounts);
			bookingProcessor.generateCrossBookings(importAccounts, 6, null);
		} catch (Exception e) {
			throw new GBankingException("Booking import configuration could not be loaded.", e);
		}
	}

	private Integer resolveCrossAccountId(ImportBooking importBooking, int sourceAccountId) {
		BankAccount crossAccount = lookupByName(importBooking.getCrossAccountName());
		Counterpart counterpart = importBooking.getCounterpart();
		if (crossAccount == null) {
			crossAccount = lookupByIban(Counterpart.ibanOf(counterpart));
		}
		if (crossAccount == null) {
			crossAccount = lookupByIbanSuffix(Counterpart.ibanOf(counterpart));
		}
		if (crossAccount == null) {
			crossAccount = lookupByBlzAndNumber(counterpart != null ? counterpart.getBlz() : null,
					counterpart != null ? counterpart.getAccountNumber() : null);
		}
		if (crossAccount == null) {
			crossAccount = lookupByNumber(counterpart != null ? counterpart.getAccountNumber() : null);
		}
		return resolveAllowedCrossAccountId(crossAccount, sourceAccountId, importBooking);
	}

	private Integer resolveAllowedCrossAccountId(BankAccount crossAccount, int sourceAccountId, ImportBooking importBooking) {
		if (crossAccount == null || RebookingRules.isForbiddenSameAccountRebooking(sourceAccountId, crossAccount.getId(), isCancellation(importBooking))) {
			return null;
		}
		return crossAccount.getId();
	}

	private boolean isCancellation(ImportBooking importBooking) {
		return importBooking != null && (importBooking.getTyp() == Typ.CANCEL || Boolean.TRUE.equals(importBooking.getAddIsStorno()));
	}

	private void importBooking(Map<de.zft2.core.dto.Booking, Integer> crossBookingMap, ImportBooking importBooking, Booking bookingDao,
			List<Booking> existingBookings, List<Booking> importedBookings, FileImportBean.ImportAccountStatistics statistics) {
		Booking existingBooking = ImportedBookingMatcher.findMatchingBooking(existingBookings, bookingDao);
		boolean existing = existingBooking != null;
		boolean updated = false;
		Booking resolvedBooking = existing ? existingBooking : dbController.insertOrUpdate(bookingDao);

		if (importBooking.getCrossBooking() != null && !crossBookingMap.containsKey(importBooking) && resolvedBooking != null) {
			crossBookingMap.put(importBooking.getCrossBooking(), resolvedBooking.getId());
		}

		Integer crossBookingId = crossBookingMap.get(importBooking);
		if (resolvedBooking != null && crossBookingId != null) {
			Booking crossBookingDao = dbController.getById(Booking.class, crossBookingId);
			if (crossBookingDao != null && canLinkCrossBooking(resolvedBooking, crossBookingDao)) {
				ImportDaoMapper.setCrossBooking(crossBookingDao, resolvedBooking.getId());
				dbController.insertOrUpdate(crossBookingDao);
				resolvedBooking.setCrossBookingId(crossBookingDao.getId());
				dbController.insertOrUpdate(resolvedBooking);
				updated = existing;
			}
		}

		if (updated) {
			statistics.incrementUpdated();
		} else if (existing) {
			statistics.incrementSkipped();
		} else if (resolvedBooking != null) {
			existingBookings.add(resolvedBooking);
			if (importedBookings != null) {
				importedBookings.add(resolvedBooking);
			}
			statistics.incrementAdded();
		}
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

	List<FileImportBean.ImportAccountStatistics> getImportStatistics() {
		return importStatistics.asList();
	}

	private void putAccount(Map<String, BankAccount> targetMap, String key, BankAccount account) {
		String normalizedKey = normalizeKey(key);
		if (normalizedKey != null) {
			targetMap.put(normalizedKey, account);
		}
	}

	private void loadAccountLookupMaps() {
		accountsByIban.clear();
		accountsByNumber.clear();
		accountsByName.clear();
		accountsByBlzAndNumber.clear();
		for (BankAccount account : dbController.getAll(BankAccount.class)) {
			registerAccount(account);
		}
		registerAccount(contextAccount);
	}

	private BankAccount lookup(Map<String, BankAccount> sourceMap, String key) {
		return sourceMap.get(normalizeKey(key));
	}

	private List<Booking> existingBookingsFor(BankAccount account) {
		return existingBookingsByAccountId.computeIfAbsent(account.getId(), id -> new ArrayList<>(dbController.getAllByParentFull(Booking.class, id)));
	}
}
