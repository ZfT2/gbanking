package de.gbanking;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import de.gbanking.db.StatementsConfig;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.CategoryRule;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.Setting;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.exception.GBankingException;
import de.gbanking.gui.dto.MoneyTransferForm;
import de.gbanking.gui.progress.InstituteFileImportProgressBarPanel;
import de.gbanking.hbci.GBankingHBCICallback;
import de.gbanking.logging.GBankingLoggingHandler;
import de.gbanking.mapper.HbciMapper;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GBankingBean extends BaseBean implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6144828924996356319L;

	private static Logger log = LogManager.getLogger(GBankingBean.class);

	private static final HBCIVersion VERSION = HBCIVersion.HBCI_300;
	
	private static GBankingLoggingHandler logHandler = GBankingLoggingHandler.getInstance();
	private final MoneyTransferExecutionService moneyTransferExecutionService = new MoneyTransferExecutionService(this);
	private final AccountTransactionService accountTransactionService = new AccountTransactionService(this, logHandler);
	
	public boolean addNewBankAccess(BankAccess bankAccess) {

		log.info("addNewBankAccess called.");

		GBankingHBCICallback hbciCallback = new GBankingHBCICallback(bankAccess);
		hbciCallback.startStatusDialog();
		HBCIPassport passport = initBankConnection(bankAccess, hbciCallback);

		List<BankAccount> bankAccountList = new ArrayList<>();

		try (HBCIHandler handle = createHBCIHandler(VERSION.getId(), passport)) {

			logHandler.logRetrivedBankAccessInfo(passport, false);

			Konto[] konten = passport.getAccounts();
			if (konten == null || konten.length == 0) {
				log.error("Keine Konten ermittelbar");
			} else {
				log.info("Anzahl Konten: {}", konten.length);
			}
			
			for (Konto konto : konten) {

				logHandler.logRetrievedAccountInfo(konto);

				log.info("Konto: GVs:");
				List<?> allowedGVs = konto.allowedGVs;
				for (Object gv : allowedGVs) {
					log.info("GV: {}", gv);
				}
				bankAccountList.add(HbciMapper.mapKontoToBankAccount(passport.getInstName(), konto));
			}
			
			bankAccess.setBlz(passport.getBLZ());

			bankAccess.setUpd(passport.getUPD());
			bankAccess.setBpd(passport.getBPD());
			bankAccess.setAccounts(bankAccountList);
			
			BankAccess bankAccessDb = dbController.getBankAccessByBlz(bankAccess.getBlz());
			if (bankAccessDb != null)
				bankAccess.setId(bankAccessDb.getId());
			
			HBCIExecStatus status = handle.execute();

			if (!status.isOK()) {
				log.log(Level.ERROR, () -> messages.getFormattedMessage("ERROR_HBCI_STATE", status.getErrorString()));
				hbciCallback.handleFailure(status.getErrorString());
			}
			
			return status.isOK();
			
		} catch (Exception ex) {
			hbciCallback.handleException(ex);
			ex.printStackTrace();
			throw new GBankingException(getText("EXCEPTION_ADD_BANKACCESS"), ex);
		} finally {
			clearSecret(bankAccess != null ? bankAccess.getPin() : null);
			hbciCallback.finishStatusDialog();
			passport.close();
		}
		
	}

	public boolean deleteBankAccessFromDB(BankAccess bankAccess) {
		boolean result = true;
		
		for (BankAccount account : bankAccess.getAccounts()) {
			account.setSource(Source.MANUELL);
		}
		
		result &= dbController.executeSimpleUpdate(bankAccess.getAccounts(), StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, null) >= 0;
		result &= dbController.delete(bankAccess, StatementsConfig.StatementType.DELETE_BANKACCESS_BY_BLZ);
		
		return result;
	}
	
	public boolean saveBankAccessAccountsToDB(BankAccess bankAccess) {

		boolean result = true;

		for (BankAccount bankAccount : bankAccess.getAccounts()) {
			bankAccount.setBankAccessId(bankAccess.getId());
			bankAccount.setOfflineAccount(false);
			bankAccount.setAccountState(AccountState.ACTIVE);
			result &= dbController.insertOrUpdate(bankAccount) != null;
			result &= dbController.insertBusinessCases(bankAccount);
		}

		return result;
	}
	
	public boolean retrieveAccountTransactions(BankAccount bankAccount, char[] pin) {
		return accountTransactionService.retrieveAccountTransactions(bankAccount, pin);
	}

	void saveHbciBookingsForAccount(BankAccount bankAccount, List<org.kapott.hbci.GV_Result.GVRKUms.UmsLine> buchungen) {
		accountTransactionService.saveHbciBookingsForAccount(bankAccount, buchungen);
	}
	
	
	public List<MoneyTransfer> retrieveOpenTransfers() {
		List<MoneyTransfer> openMoneytransferList = dbController.getAllWithFilter(MoneyTransfer.class, MoneyTransferStatus.NEW);
		return openMoneytransferList;
	}
	
	public BankAccount getAccountForOpenMoneytransfers(int accountId) {
		return dbController.getByIdFull(BankAccount.class, accountId);
	}
	
	public boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {
		return moneyTransferExecutionService.executeTransfer(moneyTransfer, bankAccount, pin);
	}
	
	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf) {
		Recipient recipient = dbController.find(Recipient.class, (new Recipient(mtf.getRecipientName(), mtf.getIban())));
		if (recipient == null) {
			recipient = new Recipient(mtf.getRecipientName(), mtf.getIban(), mtf.getBic(), null, null, mtf.getBank(), Source.MONEYTRANSFER);
			recipient = dbController.insertOrUpdate(recipient);
			log.info("created new Recipient with id: {}", recipient.getId());
		}

		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(mtf.getBankAccount().getId());
		moneyTransfer.setOrderType(mtf.getOrderType());
		moneyTransfer.setRecipientId(recipient.getId());
		moneyTransfer.setPurpose(mtf.getPurpose());
		moneyTransfer.setAmount(mtf.getAmount());
		moneyTransfer.setExecutionDate(mtf.getExecutionDate());
		moneyTransfer.setExecutionDay(mtf.getExecutionDay());
		moneyTransfer.setStandingorderMode(mtf.getStandingorderMode());
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.NEW);
		
		return dbController.insertOrUpdate(moneyTransfer);
	}
	
	public void deleteMoneyTransferFromDB(MoneyTransfer moneytransfer) {
		 dbController.delete(moneytransfer, null);
	}
	
	public Recipient saveRecipientToDB(Recipient recipient) {
		
		Recipient recipientDB = dbController.find(Recipient.class, recipient);
		if (recipientDB != null) {
			if (recipient.getNote() != null && !recipient.getNote().equals(recipientDB.getNote())) {
				recipientDB.setNote(recipient.getNote());
				recipient = dbController.insertOrUpdate(recipientDB);
				log.info("updated note for Recipient with id: {}", recipient.getId());			
			}
		} else {
			recipient = dbController.insertOrUpdate(recipient);
			log.info("created new Recipient with id: {}", recipient.getId());			
		}
		
		return recipient;
	}
	
	public void deleteRecipientFromDB(Recipient recipient) {
		 dbController.delete(recipient, null);
	}
	
	public boolean isRecipientEditable(Recipient recipient) {
		return dbController.getSingleResultField(recipient, StatementsConfig.StatementType.SELECT_SPECIFIC_EDITABLE, Boolean.class);
	}
	
	public void saveCategoryToDB(Category category) {
		dbController.insertOrUpdate(category);
		
	}
	
	public void deleteCategoryFromDB(Category category) {
		 dbController.delete(category, null);
	}
	
	public List<Booking> getBookingsForAccount(int accountId){
		return dbController.getAllByParent(Booking.class, accountId);
	}
	
	public List<Booking> getAllBookings(){
		return dbController.getAllFull(Booking.class);
	}
	
	
	public void applyCategoryRule(CategoryRule categoryRule ) {
		if (categoryRule == null || categoryRule.getCategory() == null) {
			return;
		}

		List<Predicate<Booking>> filters = buildCategoryRuleFilters(categoryRule);
		Predicate<Booking> matchesRule = combineCategoryRuleFilters(categoryRule, filters);

		Set<Integer> allowedAccountIds = getAllowedAccountIds(categoryRule);
		List<Booking> bookingListToCategorize = dbController.getAllFull(Booking.class).stream()
				.filter(booking -> allowedAccountIds.isEmpty() || allowedAccountIds.contains(booking.getAccountId()))
				.filter(matchesRule)
				.toList();

		if (bookingListToCategorize.isEmpty()) {
			return;
		}

		Set<Integer> bookingIdSet = bookingListToCategorize.stream().map(Booking::getId).collect(java.util.stream.Collectors.toSet());
		Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
		categoryBookingMap.put(categoryRule.getCategory(), bookingIdSet);

		dbController.updateBookingsWithCategories(categoryBookingMap);
	}

	private List<Predicate<Booking>> buildCategoryRuleFilters(CategoryRule categoryRule) {
		List<Predicate<Booking>> filters = new ArrayList<>();

		if (categoryRule.getFilterAmountFrom() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountFrom()) >= 0);
		}
		if (categoryRule.getFilterAmountTo() != null) {
			filters.add(booking -> booking.getAmount() != null && booking.getAmount().compareTo(categoryRule.getFilterAmountTo()) <= 0);
		}
		if (categoryRule.getFilterDateFrom() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isBefore(categoryRule.getFilterDateFrom()));
		}
		if (categoryRule.getFilterDateTo() != null) {
			filters.add(booking -> booking.getDateBooking() != null && !booking.getDateBooking().isAfter(categoryRule.getFilterDateTo()));
		}
		if (categoryRule.getFilterPurpose() != null) {
			filters.add(booking -> matchesTextFilter(booking.getPurpose(), categoryRule.getFilterPurpose(), categoryRule.isFilterPurposeIsRegex()));
		}
		if (categoryRule.getFilterRecipient() != null) {
			filters.add(booking -> matchesTextFilter(booking.getRecipient() != null ? booking.getRecipient().getName() : null,
					categoryRule.getFilterRecipient(), categoryRule.isFilterRecipientIsRegex()));
		}

		return filters;
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

	BankAccess initBankAccess(BankAccount bankAccount, char[] pin) {
		BankAccess bankAccess;
		if (bankAccount.getBankAccessId() <= 0) {
			log.warn("No BankAccess configured for Account {}, IBAN: {} / Nr.: {}", bankAccount.getAccountName(),
					bankAccount.getIban(), bankAccount.getNumber());
			return null;
		} else {
			bankAccess = dbController.getBankAccessById(bankAccount.getBankAccessId());
			bankAccess.setPin(pin);
		}
		return bankAccess;
	}
	
	HBCIPassport initBankConnection(BankAccess bankAccess) {
		return initBankConnection(bankAccess, new GBankingHBCICallback(bankAccess));
	}

	HBCIPassport initBankConnection(BankAccess bankAccess, GBankingHBCICallback hbciCallback) {
		Properties props = new Properties();
		HBCIUtils.init(props, hbciCallback);

		HBCIUtils.setParam("client.passport.PinTan.init", "1");

		Setting settingProductKey = dbController.getAll(Setting.class).stream().filter(setting -> "productKey".equals(setting.getAttribute())).findAny()
				.orElse(null);
		if (settingProductKey != null && settingProductKey.getValue() != null)
			HBCIUtils.setParam("client.product.name", settingProductKey.getValue());
		else
			log.warn("Product-Key noch found!");

		HBCIPassport passport = AbstractHBCIPassport.getInstance("PinTanDB", bankAccess.getBlz());
		passport.setCountry("DE");

		BankInfo info = HBCIUtils.getBankInfo(bankAccess.getBlz());
		if (info == null || info.getPinTanAddress() == null || info.getPinTanAddress().isBlank()) {
			throw new GBankingException("No FinTS address available for bank code: " + bankAccess.getBlz());
		}
		passport.setHost(info.getPinTanAddress());
		passport.setPort(443);

		/* Art der Nachrichten-Codierung. Bei Chipkarte/Schluesseldatei wird "None" verwendet. Bei PIN/TAN kommt "Base64" zum Einsatz. */
		passport.setFilterType(HbciEncodingFilterType.BASE64.toString());
		return passport;
	}
	
	Konto getSenderAccount(HBCIPassport passport, BankAccount bankAccount) throws GBankingException {
		
		for (Konto konto : passport.getAccounts()) {
			if (konto.iban.equalsIgnoreCase(bankAccount.getIban())
					|| konto.number.equalsIgnoreCase(bankAccount.getNumber())) {
				return konto;
			}
		}
		throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND", bankAccount.getIban()));
	}

	public void postRetriveActions(List<BankAccount> accountsList) {
		for (BankAccount account : accountsList) {
			adjustRebookings(account);
		}
		applyCategoryRules(accountsList);
	}

	void adjustRebookings(BankAccount checkedAccount) {
		for (Booking booking : checkedAccount.getBookings()) {
			if (booking.getSource() == Source.ONLINE_NEW) {
				Booking crossBooking = dbController.findCrossBooking(booking);
				if (crossBooking != null) {
					crossBooking.setBookingType(booking.getAmount().compareTo(BigDecimal.ZERO) < 0 ? BookingType.REBOOKING_IN : BookingType.REBOOKING_OUT);
					crossBooking.setCrossAccountId(booking.getAccountId());
					dbController.insertOrUpdate(crossBooking);
					booking.setBookingType(booking.getAmount().compareTo(BigDecimal.ZERO) < 0 ? BookingType.REBOOKING_OUT : BookingType.REBOOKING_IN);
					booking.setCrossAccountId(crossBooking.getAccountId());
					dbController.insertOrUpdate(booking);
				}
			}
		}
	}
	
	private void applyCategoryRules(List<BankAccount> checkedAccounts) {
		if (checkedAccounts == null || checkedAccounts.isEmpty()) {
			return;
		}

		Set<Integer> checkedAccountIds = checkedAccounts.stream().map(BankAccount::getId).collect(java.util.stream.Collectors.toSet());
		for (CategoryRule categoryRule : dbController.getAll(CategoryRule.class)) {
			if (appliesToAnyCheckedAccount(categoryRule, checkedAccountIds)) {
				applyCategoryRule(categoryRule);
			}
		}
	}

	private boolean appliesToAnyCheckedAccount(CategoryRule categoryRule, Set<Integer> checkedAccountIds) {
		Set<Integer> ruleAccountIds = getAllowedAccountIds(categoryRule);
		return ruleAccountIds.isEmpty() || ruleAccountIds.stream().anyMatch(checkedAccountIds::contains);
	}
	
	HBCIHandler createHBCIHandler(String versionId, HBCIPassport passport) {
	    return new HBCIHandler(versionId, passport);
	}

	boolean supportsTransferOrderType(BankAccount bankAccount, OrderType orderType) {
		return moneyTransferExecutionService.supportsTransferOrderType(bankAccount, orderType);
	}

	private void clearSecret(char[] secret) {
		if (secret != null) {
			Arrays.fill(secret, '\0');
		}
	}

	@SuppressWarnings("unchecked")
	<T extends HBCIJobResult> HBCIJob<T> newHbciJob(HBCIHandler handle, String jobDescription) {
		return handle.newJob(jobDescription);
	}

	static HBCIVersion getVersion() {
		return VERSION;
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

	private void startInstituteImportWithProgress() throws Exception {

		// Use primary stage (or any existing window)
		Stage dialogStage = new Stage();
		dialogStage.initModality(Modality.APPLICATION_MODAL);

		InstituteFileImportProgressBarPanel progressPanel = new InstituteFileImportProgressBarPanel(dialogStage);

		Stage progressStage = progressPanel.createNewFileImportProgressBarWindow();

		progressStage.show();

		progressPanel.startTask("fints_institute NEU mit BIC Master.csv", null, null);
	}

}
