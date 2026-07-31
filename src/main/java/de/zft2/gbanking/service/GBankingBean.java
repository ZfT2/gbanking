package de.zft2.gbanking.service;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.BankingCapabilityService;
import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankMessage;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.CategoryRule;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.MoneyTransferForeign;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDbb;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportDk;
import de.zft2.gbanking.file.imp.institute.InstituteFileImportEpc;
import de.zft2.gbanking.gui.dto.MoneyTransferForm;
import de.zft2.gbanking.gui.progress.InstituteFileImportProgressBarPanel;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.paypal.PaypalAccountTransactionService;
import de.zft2.gbanking.paypal.PaypalSupport;
import de.zft2.gbanking.rebooking.MissingRebookingCreationSummary;
import de.zft2.gbanking.rebooking.RebookingAssignmentSummary;
import de.zft2.gbanking.service.account.AccountTransactionRetrievalResult;
import de.zft2.gbanking.service.account.AccountTransactionService;
import de.zft2.gbanking.service.account.AccountStatement;
import de.zft2.gbanking.service.account.AccountStatementAcknowledgementResult;
import de.zft2.gbanking.service.account.AccountStatementRetrievalResult;
import de.zft2.gbanking.service.account.AccountStatementService;
import de.zft2.gbanking.service.bankaccess.BankMessageRetrievalResult;
import de.zft2.gbanking.service.bankaccess.BankMessageService;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.service.booking.BookingSplitService;
import de.zft2.gbanking.service.moneytransfer.BankOrderOperation;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferExecutionService;
import de.zft2.gbanking.service.moneytransfer.MoneyTransferInventoryService;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GBankingBean implements BaseMessagesDb, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6144828924996356319L;

	public record CategoryDeleteImpact(int bookingCount, int categoryRuleCount) {
	}

	private static Logger log = LogManager.getLogger(GBankingBean.class);

	private static final List<MoneyTransferStatus> EXECUTABLE_MONEY_TRANSFER_STATUSES = List.of(MoneyTransferStatus.NEW, MoneyTransferStatus.CHANGED,
			MoneyTransferStatus.ERROR, MoneyTransferStatus.DELETE_PENDING);
	private static final String ERROR_BANK_DELETE_NOT_AVAILABLE = "ERROR_MONEYTRANSFER_BANK_DELETE_NOT_AVAILABLE";
	
	private static GBankingLoggingHandler logHandler = GBankingLoggingHandler.getInstance();
	private final transient BankAccessService bankAccessService = new BankAccessService();
	private final transient MoneyTransferExecutionService moneyTransferExecutionService = new MoneyTransferExecutionService(this, bankAccessService);
	private final transient MoneyTransferInventoryService moneyTransferInventoryService = new MoneyTransferInventoryService(this, bankAccessService);
	private final transient BankingCapabilityService bankingCapabilityService = new BankingCapabilityService();
	private final transient AccountTransactionService accountTransactionService = new AccountTransactionService(bankAccessService, logHandler);
	private final transient PaypalAccountTransactionService paypalAccountTransactionService = new PaypalAccountTransactionService(accountTransactionService);
	private final transient AccountStatementService accountStatementService = new AccountStatementService(bankAccessService, logHandler);
	private final transient BankMessageService bankMessageService = new BankMessageService(bankAccessService, logHandler);
	private final transient BookingSplitService bookingSplitService = new BookingSplitService(dbController);
	
	
	
	public boolean retrieveAccountTransactions(BankAccount bankAccount, char[] pin) {
		return retrieveAccountTransactionsWithResult(bankAccount, pin).successful();
	}

	public AccountTransactionRetrievalResult retrieveAccountTransactionsWithResult(BankAccount bankAccount, char[] pin) {
		BankAccess bankAccess = bankAccount != null && bankAccount.getBankAccessId() != null
				? dbController.getBankAccessById(bankAccount.getBankAccessId()) : null;
		return PaypalSupport.isPaypal(bankAccess) ? paypalAccountTransactionService.retrieve(bankAccount, pin)
				: accountTransactionService.retrieveAccountTransactionsWithResult(bankAccount, pin);
	}

	public AccountStatementRetrievalResult retrieveAccountStatementsWithResult(BankAccount bankAccount, char[] pin) {
		return accountStatementService.retrieveAccountStatementsWithResult(bankAccount, pin);
	}

	public AccountStatementAcknowledgementResult acknowledgeAccountStatementsWithResult(BankAccount bankAccount, char[] pin) {
		return accountStatementService.acknowledgeAccountStatementsWithResult(bankAccount, pin);
	}

	public List<AccountStatement> getAccountStatements(BankAccount bankAccount) {
		return accountStatementService.listAccountStatements(bankAccount);
	}

	public Path prepareAccountStatementForOpening(AccountStatement statement) {
		return accountStatementService.prepareForOpening(statement);
	}

	public void updateAccountStatementFileEncryption(boolean enabled) {
		accountStatementService.updateFileEncryption(enabled);
	}

	public BankMessageRetrievalResult retrieveBankMessagesWithResult(BankAccess bankAccess, char[] pin) {
		return bankMessageService.retrieveBankMessagesWithResult(bankAccess, pin);
	}

	public List<BankMessage> getBankMessages(BankAccess bankAccess) {
		return bankMessageService.listBankMessages(bankAccess);
	}

	void saveHbciBookingsForAccount(BankAccount bankAccount, List<org.kapott.hbci.GV_Result.GVRKUms.UmsLine> buchungen) {
		accountTransactionService.saveHbciBookingsForAccount(bankAccount, buchungen);
	}

	public List<BankAccount> getAllAccounts() {
		return dbController.getAll(BankAccount.class);
	}

	public RebookingAssignmentSummary detectRebookings(LocalDate dateFrom, LocalDate dateTo, List<BankAccount> anchorAccounts) {
		return accountTransactionService.detectRebookings(dateFrom, dateTo, anchorAccounts);
	}

	public int saveDetectedRebookings(RebookingAssignmentSummary summary) {
		return accountTransactionService.persistDetectedRebookingLinks(summary);
	}

	public MissingRebookingCreationSummary detectMissingRebookings(LocalDate dateFrom, LocalDate dateTo, List<BankAccount> anchorAccounts) {
		return accountTransactionService.detectMissingRebookings(dateFrom, dateTo, anchorAccounts);
	}

	public int createMissingRebookings(MissingRebookingCreationSummary summary) {
		return accountTransactionService.createMissingRebookings(summary);
	}

	public int releaseRebookingLinks(List<Booking> bookings) {
		return accountTransactionService.releaseRebookingLinks(bookings);
	}
	
	
	public List<MoneyTransfer> retrieveOpenTransfers() {
		return EXECUTABLE_MONEY_TRANSFER_STATUSES.stream().flatMap(status -> dbController.getAllWithFilter(MoneyTransfer.class, status).stream()).toList();
	}
	
	public BankAccount getAccountForOpenMoneytransfers(int accountId) {
		return dbController.getByIdFull(BankAccount.class, accountId);
	}
	
	public boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {
		return moneyTransferExecutionService.executeTransfer(moneyTransfer, bankAccount, pin);
	}

	public boolean retrieveMoneyTransferInventory(BankAccount bankAccount, OrderType orderType, char[] pin) {
		return moneyTransferInventoryService.retrieveInventory(bankAccount, orderType, pin);
	}
	
	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf) {
		return saveMoneyTransferToDB(mtf, null);
	}

	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf, MoneyTransfer existingMoneyTransfer) {
		return dbController.executeInTransaction(() -> saveMoneyTransferToDBInTransaction(mtf, existingMoneyTransfer));
	}

	private MoneyTransfer saveMoneyTransferToDBInTransaction(MoneyTransferForm mtf, MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer != null && existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_DELETE_PENDING_EDIT"));
		}
		MoneyTransferForeign foreignTransfer = mtf.getForeignTransfer();
		Recipient recipient = dbController.resolveRecipient(new Recipient(mtf.getRecipientName(), mtf.getIban(), mtf.getBic(),
				foreignTransfer != null ? foreignTransfer.getRecipientAccountNumber() : null,
				foreignTransfer != null ? foreignTransfer.getRecipientBankCode() : mtf.getRecipientBlz(), mtf.getBank(), Source.MONEYTRANSFER));
		log.info("saveMoneyTransferToDB(): using Recipient with id: {}", recipient.getId());

		MoneyTransferStatus statusAfterSave = resolveMoneyTransferStatusAfterSave(existingMoneyTransfer);
		MoneyTransfer historyPredecessor = resolveHistoryPredecessor(existingMoneyTransfer);
		MoneyTransfer moneyTransfer = selectMoneyTransfer(historyPredecessor, existingMoneyTransfer);
		boolean newMoneyTransfer = moneyTransfer.getId() <= 0;
		moneyTransfer.setAccountId(mtf.getBankAccount().getId());
		moneyTransfer.setOrderType(mtf.getOrderType());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setRecipient(recipient);
		moneyTransfer.setPurpose(mtf.getPurpose());
		moneyTransfer.setAmount(mtf.getAmount());
		moneyTransfer.setCurrency(mtf.getCurrency());
		moneyTransfer.setForeignTransfer(foreignTransfer);
		moneyTransfer.setExecutionDate(mtf.getExecutionDate());
		moneyTransfer.setExecutionDay(mtf.getExecutionDay());
		moneyTransfer.setStandingorderMode(mtf.getStandingorderMode());
		moneyTransfer.setMoneytransferStatus(statusAfterSave);
		
		MoneyTransfer persistedMoneyTransfer = dbController.insertOrUpdate(moneyTransfer);
		log.info("{} money transfer id {}, type={}, accountId={}, status={}", newMoneyTransfer ? "Created" : "Updated",
				persistedMoneyTransfer != null ? persistedMoneyTransfer.getId() : moneyTransfer.getId(), moneyTransfer.getOrderType(),
				moneyTransfer.getAccountId(), moneyTransfer.getMoneytransferStatus());
		return persistedMoneyTransfer;
	}

	private MoneyTransfer selectMoneyTransfer(MoneyTransfer historyPredecessor, MoneyTransfer existingMoneyTransfer) {
		if (historyPredecessor != null) {
			return createSuccessor(historyPredecessor);
		}
		return existingMoneyTransfer != null && existingMoneyTransfer.getId() > 0 ? existingMoneyTransfer : new MoneyTransfer();
	}

	private MoneyTransfer resolveHistoryPredecessor(MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer == null || existingMoneyTransfer.getId() <= 0
				|| existingMoneyTransfer.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY
				|| !isBankOrderType(existingMoneyTransfer)) {
			return null;
		}
		MoneyTransfer predecessor = dbController.getAllByParent(MoneyTransfer.class, existingMoneyTransfer.getAccountId()).stream()
				.filter(transfer -> transfer.getId() == existingMoneyTransfer.getId()).findFirst().orElse(null);
		if (predecessor == null) {
			throw new GBankingException("Money transfer to be versioned no longer exists");
		}
		return predecessor;
	}

	private MoneyTransfer createSuccessor(MoneyTransfer predecessor) {
		MoneyTransfer successor = new MoneyTransfer();
		successor.setBankOrderId(predecessor.getBankOrderId());
		successor.setPurposeCode(predecessor.getPurposeCode());
		successor.setHistoryorderId(predecessor.getId());
		return successor;
	}

	private MoneyTransferStatus resolveMoneyTransferStatusAfterSave(MoneyTransfer existingMoneyTransfer) {
		if (existingMoneyTransfer == null || existingMoneyTransfer.getId() <= 0) {
			return MoneyTransferStatus.NEW;
		}
		if (isBankOrderType(existingMoneyTransfer) && (existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.INVENTORY
				|| existingMoneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED)) {
			return MoneyTransferStatus.CHANGED;
		}
		return existingMoneyTransfer.getMoneytransferStatus() != null ? existingMoneyTransfer.getMoneytransferStatus() : MoneyTransferStatus.NEW;
	}
	
	public void deleteMoneyTransferFromDB(MoneyTransfer moneytransfer) {
		if (isBankManagedOrder(moneytransfer)) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_BANK_ORDER_LOCAL_DELETE"));
		}
		log.info("Deleting money transfer id {}, type={}, status={}", moneytransfer != null ? moneytransfer.getId() : null,
				moneytransfer != null ? moneytransfer.getOrderType() : null, moneytransfer != null ? moneytransfer.getMoneytransferStatus() : null);
		dbController.delete(moneytransfer, null);
	}

	public MoneyTransfer requestBankOrderDeletion(MoneyTransfer moneyTransfer) {
		return dbController.executeInTransaction(() -> requestBankOrderDeletionInTransaction(moneyTransfer));
	}

	private MoneyTransfer requestBankOrderDeletionInTransaction(MoneyTransfer moneyTransfer) {
		MoneyTransfer persistedTransfer = findPersistedMoneyTransfer(moneyTransfer);
		if (persistedTransfer == null || !isBankManagedOrder(persistedTransfer)) {
			throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
		}
		if (persistedTransfer.getBankOrderId() == null || persistedTransfer.getBankOrderId().isBlank()) {
			throw new GBankingException(getText("ERROR_MONEYTRANSFER_BANK_ORDER_ID_REQUIRED"));
		}
		if (persistedTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			return persistedTransfer;
		}

		if (persistedTransfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED) {
			MoneyTransfer predecessor = findPersistedMoneyTransferById(persistedTransfer.getAccountId(), persistedTransfer.getHistoryorderId());
			if (predecessor == null || predecessor.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY) {
				throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
			}
			persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.SUPERSEDED);
			dbController.insertOrUpdate(persistedTransfer);
			persistedTransfer = predecessor;
		}

		if (persistedTransfer.getMoneytransferStatus() != MoneyTransferStatus.INVENTORY) {
			throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
		}
		persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.DELETE_PENDING);
		return dbController.insertOrUpdate(persistedTransfer);
	}

	public MoneyTransfer cancelBankOrderDeletion(MoneyTransfer moneyTransfer) {
		return dbController.executeInTransaction(() -> {
			MoneyTransfer persistedTransfer = findPersistedMoneyTransfer(moneyTransfer);
			if (persistedTransfer == null || persistedTransfer.getMoneytransferStatus() != MoneyTransferStatus.DELETE_PENDING) {
				throw new GBankingException(getText(ERROR_BANK_DELETE_NOT_AVAILABLE));
			}
			persistedTransfer.setMoneytransferStatus(MoneyTransferStatus.INVENTORY);
			return dbController.insertOrUpdate(persistedTransfer);
		});
	}

	public boolean isBankManagedOrder(MoneyTransfer moneyTransfer) {
		if (!isBankOrderType(moneyTransfer)) {
			return false;
		}
		MoneyTransferStatus status = moneyTransfer.getMoneytransferStatus();
		return status == MoneyTransferStatus.INVENTORY || status == MoneyTransferStatus.CHANGED
				|| status == MoneyTransferStatus.DELETE_PENDING;
	}

	private static boolean isBankOrderType(MoneyTransfer moneyTransfer) {
		return moneyTransfer != null && (moneyTransfer.getOrderType() == OrderType.STANDING_ORDER
				|| moneyTransfer.getOrderType() == OrderType.SCHEDULED_TRANSFER);
	}

	private MoneyTransfer findPersistedMoneyTransfer(MoneyTransfer moneyTransfer) {
		if (moneyTransfer == null || moneyTransfer.getId() <= 0) {
			return null;
		}
		return findPersistedMoneyTransferById(moneyTransfer.getAccountId(), moneyTransfer.getId());
	}

	private MoneyTransfer findPersistedMoneyTransferById(int accountId, Integer moneyTransferId) {
		if (moneyTransferId == null || moneyTransferId <= 0) {
			return null;
		}
		return dbController.getAllByParent(MoneyTransfer.class, accountId).stream()
				.filter(transfer -> transfer.getId() == moneyTransferId).findFirst().orElse(null);
	}
	
	public Recipient saveRecipientToDB(Recipient recipient) {
		Recipient resolvedRecipient = dbController.resolveRecipient(recipient);
		synchronizeRecipientDefault(recipient, resolvedRecipient);
		log.info("saveRecipientToDB(): using Recipient with id: {}", resolvedRecipient.getId());
		return resolvedRecipient;
	}

	public Recipient findDefaultRecipientForSameAccountIdentifier(Recipient recipient) {
		List<Recipient> matchingDefaults = getDefaultRecipientsForSameAccountIdentifier(recipient);
		return matchingDefaults.isEmpty() ? null : matchingDefaults.get(0);
	}

	private void synchronizeRecipientDefault(Recipient requestedRecipient, Recipient resolvedRecipient) {
		if (requestedRecipient == null || resolvedRecipient == null || resolvedRecipient.getId() <= 0) {
			return;
		}

		boolean defaultRequested = requestedRecipient.isDefault();
		if (defaultRequested) {
			clearOtherDefaultRecipients(resolvedRecipient);
		}
		if (resolvedRecipient.isDefault() != defaultRequested) {
			dbController.updateRecipientDefault(resolvedRecipient.getId(), defaultRequested);
			resolvedRecipient.setDefault(defaultRequested);
		}
	}

	private void clearOtherDefaultRecipients(Recipient recipient) {
		for (Recipient defaultRecipient : getDefaultRecipientsForSameAccountIdentifier(recipient)) {
			if (defaultRecipient.getId() != recipient.getId()) {
				dbController.updateRecipientDefault(defaultRecipient.getId(), false);
			}
		}
	}

	private List<Recipient> getDefaultRecipientsForSameAccountIdentifier(Recipient recipient) {
		RecipientAccountIdentifier identifier = getRecipientAccountIdentifier(recipient);
		if (identifier == null) {
			return List.of();
		}

		int recipientId = recipient.getId();
		return dbController.getAll(Recipient.class).stream()
				.filter(Recipient::isDefault)
				.filter(candidate -> candidate.getId() != recipientId)
				.filter(identifier::matches)
				.toList();
	}

	private RecipientAccountIdentifier getRecipientAccountIdentifier(Recipient recipient) {
		if (recipient == null) {
			return null;
		}

		String iban = normalizeIban(recipient.getIban());
		if (iban != null) {
			return new RecipientAccountIdentifier(iban, true);
		}

		String accountNumber = trimToNull(recipient.getAccountNumber());
		return accountNumber != null ? new RecipientAccountIdentifier(accountNumber, false) : null;
	}

	private static String normalizeIban(String value) {
		String normalizedValue = trimToNull(value);
		return normalizedValue != null ? normalizedValue.toUpperCase(Locale.ROOT) : null;
	}

	private record RecipientAccountIdentifier(String value, boolean iban) {

		private boolean matches(Recipient recipient) {
			if (iban) {
				return value.equals(normalizeIban(recipient.getIban()));
			}
			return value.equals(trimToNull(recipient.getAccountNumber()));
		}
	}
	
	public void deleteRecipientFromDB(Recipient recipient) {
		 dbController.delete(recipient, null);
	}
	
	public boolean isRecipientEditable(Recipient recipient) {
		return dbController.isRecipientEditable(recipient);
	}

	public boolean isRecipientDeletable(Recipient recipient) {
		return dbController.isRecipientDeletable(recipient);
	}
	
	public void saveCategoryToDB(Category category) {
		dbController.insertOrUpdate(category);
		
	}

	public CategoryDeleteImpact getCategoryDeleteImpact(Category category) {
		Set<Integer> categoryIds = collectCategoryTreeIds(category);
		if (categoryIds.isEmpty()) {
			return new CategoryDeleteImpact(0, 0);
		}

		int bookingCount = collectBookingIdsForCategories(categoryIds).size();
		int categoryRuleCount = Math.toIntExact(dbController.getAllFull(CategoryRule.class).stream()
				.filter(categoryRule -> categoryRule.getCategory() != null && categoryIds.contains(categoryRule.getCategory().getId()))
				.count());
		return new CategoryDeleteImpact(bookingCount, categoryRuleCount);
	}
	
	public boolean deleteCategoryFromDB(Category category) {
		Set<Integer> categoryIds = collectCategoryTreeIds(category);
		if (categoryIds.isEmpty()) {
			return false;
		}

		Set<Integer> bookingIds = collectBookingIdsForCategories(categoryIds);
		if (!bookingIds.isEmpty() && dbController.clearBookingCategories(bookingIds) < 0) {
			return false;
		}
		return dbController.delete(category, null);
	}
	
	public List<Booking> getBookingsForAccount(int accountId){
		return dbController.getAllByParentFull(Booking.class, accountId);
	}
	
	public List<Booking> getAllBookings(){
		return dbController.getAllFull(Booking.class);
	}

	public boolean deleteBooking(Booking booking) {
		return bookingSplitService.deleteBookingWithSplits(booking);
	}

	public int deleteBookingsInBlock(Booking referenceBooking, boolean deleteFromDate) {
		if (referenceBooking == null || referenceBooking.getAccountId() <= 0 || !isBlockDeleteSource(referenceBooking.getSource())) {
			return 0;
		}

		LocalDate referenceDate = getRelevantBookingDate(referenceBooking);
		if (referenceDate == null) {
			return 0;
		}

		List<Booking> bookingsToDelete = dbController.getAllByParentFull(Booking.class, referenceBooking.getAccountId()).stream()
				.filter(booking -> booking != null && isSameDeletionSourceFamily(referenceBooking.getSource(), booking.getSource()))
				.filter(booking -> {
					LocalDate bookingDate = getRelevantBookingDate(booking);
					if (bookingDate == null) {
						return false;
					}
					return deleteFromDate ? !bookingDate.isBefore(referenceDate) : !bookingDate.isAfter(referenceDate);
				})
				.toList();

		int deletedCount = 0;
		for (Booking booking : bookingsToDelete) {
			if (deleteBooking(booking)) {
				deletedCount++;
			}
		}
		log.info("Deleted {} bookings in block for account id {}, direction={}", deletedCount, referenceBooking.getAccountId(),
				deleteFromDate ? "from-date" : "until-date");
		return deletedCount;
	}
	
	
	public void applyCategoryRule(CategoryRule categoryRule) {
		applyCategoryRule(categoryRule, dbController.getAllFull(Booking.class), true);
	}

	public int assignCategoryToBookings(Category category, List<Booking> bookings) {
		if (category == null || category.getId() <= 0 || bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream()
				.filter(booking -> booking != null && booking.getId() > 0)
				.map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
		categoryBookingMap.put(category, bookingIds);
		if (!dbController.updateBookingsWithCategories(categoryBookingMap)) {
			return 0;
		}

		for (Booking booking : bookings) {
			if (booking != null && bookingIds.contains(booking.getId())) {
				booking.setCategory(category);
				booking.setCategoryId(category.getId());
				booking.setCategoryRuleId(null);
				booking.setCategoryRuleName(null);
			}
		}
		log.info("Assigned category '{}' to {} bookings.", category.getFullName(), bookingIds.size());
		return bookingIds.size();
	}

	public int clearCategoryFromBookings(List<Booking> bookings) {
		if (bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream()
				.filter(booking -> booking != null && booking.getId() > 0 && getBookingCategoryId(booking) > 0)
				.map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		int updatedBookingCount = dbController.clearBookingCategories(bookingIds);
		if (updatedBookingCount <= 0) {
			return 0;
		}

		for (Booking booking : bookings) {
			if (booking != null && bookingIds.contains(booking.getId())) {
				booking.setCategory(null);
				booking.setCategoryId(0);
				booking.setCategoryRuleId(null);
				booking.setCategoryRuleName(null);
			}
		}
		log.info("Removed category assignment from {} bookings.", updatedBookingCount);
		return updatedBookingCount;
	}

	public int applyCategoryRulesToBookings(List<Booking> bookings, boolean overwriteExistingCategories) {
		if (bookings == null || bookings.isEmpty()) {
			return 0;
		}

		Set<Integer> bookingIds = bookings.stream()
				.filter(booking -> booking != null && booking.getId() > 0)
				.map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
		if (bookingIds.isEmpty()) {
			return 0;
		}

		List<Booking> candidateBookings = dbController.getAllFull(Booking.class).stream()
				.filter(booking -> bookingIds.contains(booking.getId()))
				.toList();
		return applyCategoryRulesToCandidateBookings(candidateBookings, overwriteExistingCategories);
	}

	private int applyCategoryRulesToCandidateBookings(List<Booking> candidateBookings, boolean overwriteExistingCategories) {
		if (candidateBookings == null || candidateBookings.isEmpty()) {
			return 0;
		}

		Set<Integer> candidateAccountIds = candidateBookings.stream().map(Booking::getAccountId).collect(java.util.stream.Collectors.toSet());
		Set<Integer> updatedBookingIds = new HashSet<>();
		for (CategoryRule categoryRule : dbController.getAllFull(CategoryRule.class)) {
			if (appliesToAnyCheckedAccount(categoryRule, candidateAccountIds)) {
				updatedBookingIds.addAll(applyCategoryRule(categoryRule, candidateBookings, overwriteExistingCategories));
			}
		}
		log.info("Applied category rules to {} bookings. overwriteExistingCategories={}", updatedBookingIds.size(), overwriteExistingCategories);
		return updatedBookingIds.size();
	}

	private Set<Integer> applyCategoryRule(CategoryRule categoryRule, List<Booking> candidateBookings, boolean overwriteExistingCategories) {
		if (categoryRule == null || categoryRule.getCategory() == null || categoryRule.getCategory().getId() <= 0 || candidateBookings == null
				|| candidateBookings.isEmpty()) {
			return Set.of();
		}

		List<Predicate<Booking>> filters = buildCategoryRuleFilters(categoryRule);
		Predicate<Booking> matchesRule = combineCategoryRuleFilters(categoryRule, filters);

		Set<Integer> allowedAccountIds = getAllowedAccountIds(categoryRule);
		List<Booking> bookingListToCategorize = candidateBookings.stream()
				.filter(Objects::nonNull)
				.filter(booking -> allowedAccountIds.isEmpty() || allowedAccountIds.contains(booking.getAccountId()))
				.filter(matchesRule)
				.filter(booking -> shouldApplyCategory(booking, categoryRule.getCategory(), overwriteExistingCategories))
				.toList();

		if (bookingListToCategorize.isEmpty()) {
			log.debug("Category rule id {} matched no bookings to update.", categoryRule.getId());
			return Set.of();
		}

		Set<Integer> bookingIdSet = bookingListToCategorize.stream().map(Booking::getId).collect(java.util.stream.Collectors.toSet());
		if (!updateBookingsWithCategoryRule(categoryRule, bookingIdSet)) {
			return Set.of();
		}

		for (Booking booking : bookingListToCategorize) {
			booking.setCategory(categoryRule.getCategory());
			booking.setCategoryId(categoryRule.getCategory().getId());
			booking.setCategoryRuleId(categoryRule.getId() > 0 ? categoryRule.getId() : null);
			booking.setCategoryRuleName(categoryRule.getId() > 0 ? categoryRule.getName() : null);
		}
		log.info("Applied category rule id {} to {} bookings.", categoryRule.getId(), bookingListToCategorize.size());
		return bookingIdSet;
	}

	private boolean updateBookingsWithCategoryRule(CategoryRule categoryRule, Set<Integer> bookingIdSet) {
		if (categoryRule.getId() > 0) {
			return dbController.updateBookingsWithCategoryRule(categoryRule, bookingIdSet);
		}

		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
		categoryBookingMap.put(categoryRule.getCategory(), bookingIdSet);
		return dbController.updateBookingsWithCategories(categoryBookingMap);
	}

	private boolean shouldApplyCategory(Booking booking, Category category, boolean overwriteExistingCategories) {
		if (booking == null || category == null || category.getId() <= 0) {
			return false;
		}

		int existingCategoryId = getBookingCategoryId(booking);
		return existingCategoryId <= 0 || overwriteExistingCategories && existingCategoryId != category.getId();
	}

	private int getBookingCategoryId(Booking booking) {
		if (booking.getCategory() != null && booking.getCategory().getId() > 0) {
			return booking.getCategory().getId();
		}
		return booking.getCategoryId();
	}

	private Set<Integer> collectCategoryTreeIds(Category category) {
		if (category == null || category.getId() <= 0) {
			return Set.of();
		}

		Set<Integer> categoryIds = new HashSet<>();
		categoryIds.add(category.getId());
		List<Category> allCategories = dbController.getAll(Category.class);
		boolean foundChild;
		do {
			foundChild = false;
			for (Category candidate : allCategories) {
				if (candidate.getParentId() != null && categoryIds.contains(candidate.getParentId()) && categoryIds.add(candidate.getId())) {
					foundChild = true;
				}
			}
		} while (foundChild);
		return categoryIds;
	}

	private Set<Integer> collectBookingIdsForCategories(Set<Integer> categoryIds) {
		if (categoryIds == null || categoryIds.isEmpty()) {
			return Set.of();
		}
		return dbController.getAllFull(Booking.class).stream()
				.filter(booking -> categoryIds.contains(getBookingCategoryId(booking)))
				.map(Booking::getId)
				.collect(java.util.stream.Collectors.toSet());
	}

	private List<Predicate<Booking>> buildCategoryRuleFilters(CategoryRule categoryRule) {
		List<Predicate<Booking>> filters = new ArrayList<>();
		addAmountFilters(categoryRule, filters);
		addDateFilters(categoryRule, filters);
		addTextFilters(categoryRule, filters);
		return filters;
	}

	private void addAmountFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterAmountFrom() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountFrom()) >= 0);
		}
		if (categoryRule.getFilterAmountTo() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountTo()) <= 0);
		}
	}

	private void addDateFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterDateFrom() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isBefore(categoryRule.getFilterDateFrom()));
		}
		if (categoryRule.getFilterDateTo() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isAfter(categoryRule.getFilterDateTo()));
		}
	}

	private void addTextFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (categoryRule.getFilterPurpose() != null) {
			filters.add(booking -> matchesTextFilter(booking.getPurpose(), categoryRule.getFilterPurpose(), categoryRule.isFilterPurposeIsRegex()));
		}
		if (categoryRule.getFilterRecipientName() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getName() : null,
					categoryRule.getFilterRecipientName(), categoryRule.isFilterRecipientIsRegex()));
		}
		if (categoryRule.getFilterRecipientIban() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getIban() : null,
					categoryRule.getFilterRecipientIban(), false));
		}
		if (categoryRule.getFilterRecipientAccountNumber() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getAccountNumber() : null,
					categoryRule.getFilterRecipientAccountNumber(), false));
		}
	}

	private Predicate<Booking> combineCategoryRuleFilters(CategoryRule categoryRule, List<Predicate<Booking>> filters) {
		if (filters.isEmpty()) {
			return booking -> true;
		}

		if (categoryRule.getJoinType() == CategoryRule.JoinType.AND) {
			return booking -> filters.stream().allMatch(filter -> filter.test(booking));
		}

		return booking -> filters.stream().anyMatch(filter -> filter.test(booking));
	}

	private Set<Integer> getAllowedAccountIds(CategoryRule categoryRule) {
		if (categoryRule.getBankAccountList() == null || categoryRule.getBankAccountList().isEmpty()) {
			return Set.of();
		}

		Set<Integer> accountIds = new HashSet<>();
		for (BankAccount account : categoryRule.getBankAccountList()) {
			if (account != null && account.getId() > 0) {
				accountIds.add(account.getId());
			}
		}
		return accountIds;
	}

	private boolean matchesTextFilter(String value, String filter, boolean regex) {
		if (filter == null) {
			return true;
		}
		if (value == null) {
			return false;
		}
		if (regex) {
			try {
				return Pattern.compile(filter, Pattern.CASE_INSENSITIVE).matcher(value).find();
			} catch (PatternSyntaxException ex) {
				log.warn("Invalid regex for category rule filter: {}", filter, ex);
				return false;
			}
		}
		return value.toLowerCase().contains(filter.toLowerCase());
	}



	private boolean isBlockDeleteSource(Source source) {
		return isOnlineSource(source) || isImportSource(source);
	}

	private boolean isSameDeletionSourceFamily(Source referenceSource, Source checkedSource) {
		if (referenceSource == null || checkedSource == null) {
			return false;
		}
		return isOnlineSource(referenceSource) && isOnlineSource(checkedSource) || isImportSource(referenceSource) && isImportSource(checkedSource);
	}

	private boolean isOnlineSource(Source source) {
		return source == Source.ONLINE || source == Source.ONLINE_NEW || source == Source.ONLINE_PRENO || source == Source.ONLINE_PRENO_NEW;
	}

	private boolean isImportSource(Source source) {
		return source == Source.IMPORT || source == Source.IMPORT_NEW || source == Source.IMPORT_INITIAL || source == Source.IMPORT_INITIAL_NEW;
	}

	private LocalDate getRelevantBookingDate(Booking booking) {
		if (booking == null) {
			return null;
		}
		return booking.getDateBooking() != null ? booking.getDateBooking() : booking.getDateValue();
	}

	
	
	public Konto getSenderAccount(HBCIPassport passport, BankAccount bankAccount) throws GBankingException {
		
		for (Konto konto : passport.getAccounts()) {
			if (konto.iban.equalsIgnoreCase(bankAccount.getIban())
					|| konto.number.equalsIgnoreCase(bankAccount.getNumber())) {
				log.debug("Resolved HBCI sender account for account id {}", bankAccount.getId());
				return konto;
			}
		}
		log.warn("No HBCI sender account found for account id {}, IBAN: {} / Nr.: {}", bankAccount::getId,
				() -> SensitiveDataMasker.maskIban(bankAccount.getIban()), () -> SensitiveDataMasker.maskAccountNumber(bankAccount.getNumber()));
		throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND", SensitiveDataMasker.maskIban(bankAccount.getIban())));
	}

	public void postRetriveActions(List<BankAccount> accountsList) {
		for (BankAccount account : accountsList) {
			adjustRebookings(account);
		}
		applyCategoryRules(accountsList);
	}

	void adjustRebookings(BankAccount checkedAccount) {
		accountTransactionService.adjustRebookings(checkedAccount);
	}
	
	private void applyCategoryRules(List<BankAccount> checkedAccounts) {
		if (checkedAccounts == null || checkedAccounts.isEmpty()) {
			return;
		}

		Set<Integer> checkedAccountIds = checkedAccounts.stream().map(BankAccount::getId).collect(java.util.stream.Collectors.toSet());
		List<Booking> candidateBookings = dbController.getAllFull(Booking.class).stream()
				.filter(booking -> checkedAccountIds.contains(booking.getAccountId()))
				.toList();
		applyCategoryRulesToCandidateBookings(candidateBookings, true);
	}

	private boolean appliesToAnyCheckedAccount(CategoryRule categoryRule, Set<Integer> checkedAccountIds) {
		Set<Integer> ruleAccountIds = getAllowedAccountIds(categoryRule);
		return ruleAccountIds.isEmpty() || ruleAccountIds.stream().anyMatch(checkedAccountIds::contains);
	}

	public boolean supportsTransferOrderType(BankAccount bankAccount, OrderType orderType) {
		return bankingCapabilityService.supportsTransferOrderType(bankAccount, orderType);
	}

	public boolean supportsBankOrderOperation(BankAccount bankAccount, OrderType orderType, BankOrderOperation operation) {
		return bankingCapabilityService.supportsBankOrderOperation(bankAccount, orderType, operation);
	}

	public boolean supportsBankOrderManagement(BankAccount bankAccount, OrderType orderType) {
		return supportsTransferOrderType(bankAccount, orderType) || supportsOrderInventory(bankAccount, orderType)
				|| supportsBankOrderOperation(bankAccount, orderType, BankOrderOperation.EDIT)
				|| supportsBankOrderOperation(bankAccount, orderType, BankOrderOperation.DELETE);
	}

	public boolean supportsOrderInventory(BankAccount bankAccount, OrderType orderType) {
		return bankingCapabilityService.supportsOrderInventory(bankAccount, orderType);
	}

	public boolean supportsAccountTransactions(BankAccount bankAccount) {
		return bankingCapabilityService.supportsAccountTransactions(bankAccount);
	}

	public boolean supportsAccountStatements(BankAccount bankAccount) {
		return bankingCapabilityService.supportsAccountStatements(bankAccount);
	}

	public boolean supportsBankMessages(BankAccess bankAccess) {
		return bankingCapabilityService.supportsBankMessages(bankAccess);
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

	private void startInstituteImportWithProgress() {

		// Use primary stage (or any existing window)
		Stage dialogStage = new Stage();
		dialogStage.initModality(Modality.APPLICATION_MODAL);

		InstituteFileImportProgressBarPanel progressPanelDk = new InstituteFileImportProgressBarPanel(InstituteFileImportDk.class, dialogStage);
		Stage progressStageDk = progressPanelDk.createNewFileImportProgressBarWindow();
		progressStageDk.show();
		progressPanelDk.startTask(InstituteFileImportDk.DEFAULT_FILENAME, null, null);

		InstituteFileImportProgressBarPanel progressPanelDbb = new InstituteFileImportProgressBarPanel(InstituteFileImportDbb.class, dialogStage);
		Stage progressStageDbb = progressPanelDbb.createNewFileImportProgressBarWindow();
		progressStageDbb.show();
		progressPanelDbb.startTask(InstituteFileImportDbb.DEFAULT_FILENAME, null, null);

		InstituteFileImportProgressBarPanel progressPanelEpc = new InstituteFileImportProgressBarPanel(InstituteFileImportEpc.class, dialogStage);
		Stage progressStageEpc = progressPanelEpc.createNewFileImportProgressBarWindow();
		progressStageEpc.show();
		progressPanelEpc.startTask(InstituteFileImportEpc.DEFAULT_FILENAME, null, null);
	}

}
