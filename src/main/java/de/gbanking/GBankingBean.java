package de.gbanking;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.gbanking.db.StatementsConfig;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.BusinessCase;
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

		boolean result = false;

		BankAccess bankAccess = initBankAccess(bankAccount, pin);
		
		LocalDate lastBookingDate = getAccountLastBookingDate(bankAccount);
		
		GBankingHBCICallback hbciCallback = new GBankingHBCICallback(bankAccess);
		hbciCallback.startStatusDialog();
		HBCIPassport passport = initBankConnection(bankAccess, hbciCallback);
		
		refreshBankAccount(bankAccount);
		updatePreviousNewBookings(bankAccount);
		
		try (HBCIHandler handle = new HBCIHandler(VERSION.getId(), passport)){

			logHandler.logRetrivedBankAccessInfo(passport, false);

			Konto[] konten = getHbciAccountsFromPassport(passport);

			for (Konto konto : konten) {

				if (hbciKontosMatches(bankAccount, konto)) {
					logHandler.logRetrievedAccountInfo(konto);

					HBCIJob<?> saldoJob = createAndAddHbciJob(handle, "SaldoReq",  Map.of("my", konto));

					HBCIJob<?> umsatzJob = null;
					if (lastBookingDate != null) {
						umsatzJob = createAndAddHbciJob(handle, "KUmsAllCamt", Map.of("my", konto, "startdate", (java.util.Date.from(lastBookingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))));
					} else {
						umsatzJob = createAndAddHbciJob(handle, "KUmsAllCamt", Map.of("my", konto));
					}

					HBCIExecStatus status = handle.execute();
					result = status.isOK();
					
					if (!result) {
						log.error("HBCI Error, Status: {}", status);
						hbciCallback.handleFailure(status.getErrorString());
					}

					readSaldo(saldoJob);

					List<UmsLine> buchungen = readUms(umsatzJob);
					
					saveHbciBookingsForAccount(bankAccount, buchungen);

					break;
				}
			}

		} catch (Exception e) {
			hbciCallback.handleException(e);
			log.error("Error in handling HBCI calls: ", e);
			result = false;
		} finally {
			clearSecret(pin);
			hbciCallback.finishStatusDialog();
			passport.close();
		}

		return result;
	}

	void saveHbciBookingsForAccount(BankAccount bankAccount, List<UmsLine> buchungen) {
		List<Booking> newBookingsList = new ArrayList<>();
		Map<Recipient, Set<Integer>> recipientBookingMap = new HashMap<>();

		for (UmsLine buchung : buchungen) {

			/* logHandler.logRetrivedBookingInfo(buchung); */

			Booking newBooking = HbciMapper.mapUmsLineToBooking(bankAccount.getId(), buchung);
			
			/* log.info("Konto other: {}", buchung.other.toString()); */
			
			Recipient recipient = HbciMapper.mapUmsLineKontoToRecipient(buchung.other);

			if (recipient != null) {
				Recipient recipientDb = dbController.find(Recipient.class, recipient);
				if (recipientDb == null) {
					recipientDb = dbController.insertOrUpdate(recipient);
				}
				newBooking.setRecipient(recipientDb);
			}

			newBookingsList.add(newBooking);
		}

		dbController.insertAccountBookings(newBookingsList);
		
		for (Booking booking : newBookingsList) {
			Recipient recipient = booking.getRecipient();
			if (recipient != null) {
				Set<Integer> existingBookingIds = recipientBookingMap.get(recipient);
				if (existingBookingIds == null) {
					recipientBookingMap.put(recipient, new HashSet<>(Arrays.asList(booking.getId())));
				} else {
					existingBookingIds.add(booking.getId());
					recipientBookingMap.put(recipient, existingBookingIds);
				}
			}
		}
		
		dbController.updateBookingsWithRecipients(recipientBookingMap);
		
	}

	private void updatePreviousNewBookings(BankAccount bankAccount) {
		
		for (Booking booking : bankAccount.getBookings()) {
			booking.setSource(booking.getSource().getCorresponding());
		}
		
		dbController.executeSimpleUpdate(Arrays.asList(bankAccount), StatementsConfig.StatementType.UPDATE_BOOKING_SOURCE, Booking.class);
	}

	private void readSaldo(HBCIJob<?> saldoJob) {
		GVRSaldoReq saldoResult = (GVRSaldoReq) saldoJob.getJobResult();
		if (!saldoResult.isOK())
			log.error("Error in retrieving Saldo: {}", saldoResult);

		Value s = saldoResult.getEntries()[0].ready.value;
		log.info("Saldo: {}", s);
	}

	private List<UmsLine> readUms(HBCIJob<?> umsatzJob) {
		GVRKUms umsResult = (GVRKUms) umsatzJob.getJobResult();

		if (!umsResult.isOK())
			log.error("Error in retrieving Umsatz: {}", umsResult);

		return umsResult.getFlatData();
	}

	private boolean hbciKontosMatches(BankAccount bankAccount, Konto konto) {
		return bankAccount.getIban() != null && bankAccount.getIban().equalsIgnoreCase(konto.iban)
				|| bankAccount.getNumber() != null && bankAccount.getNumber().equalsIgnoreCase(konto.number);
	}

	HBCIJob<?> createAndAddHbciJob(HBCIHandler handle, String jobDescription, Map<String, Object> params) {
		HBCIJob<?> job = handle.newJob(jobDescription);

		for (Entry<String, Object> param : params.entrySet()) {
			Object value = param.getValue();

			if (value instanceof String s) {
				job.setParam(param.getKey(), s);
			} else if (value instanceof Date d) {
				job.setParam(param.getKey(), d);
			} else if (value instanceof Integer i) {
				job.setParam(param.getKey(), i);
			} else if (value instanceof Konto k) {
				job.setParam(param.getKey(), k);
			} else {
				if (value == null)
					log.log(Level.ERROR, () -> getText("HBCI_PARAM_NULL", param.getKey()));
				else {
					log.log(Level.ERROR, () -> getText("HBCI_PARAM_UNKNOWN_TYPE", param.getKey(), value.getClass().getName()));
				}
			}
		}
		job.addToQueue();

		return job;
	}

	private Konto[] getHbciAccountsFromPassport(HBCIPassport passport) {
		Konto[] konten = passport.getAccounts();
		if (konten == null || konten.length == 0) {
			log.error("No Accounts were found on bank site");
		} else {
			log.info("Number of accounts found: {}", konten.length);
		}
		return konten;
	}
	
	
	public List<MoneyTransfer> retrieveOpenTransfers() {
		
		List<MoneyTransfer> opnenMoneytransferList = dbController.getAllWithFilter(MoneyTransfer.class, MoneyTransferStatus.NEW);
		
//		for (MoneyTransfer moneytransfer : opnenMoneytransferList) {
//			BankAccount bankAccount = dbController.getById(BankAccount.class, moneytransfer.getAccountId(), false);
//			result = executeTransfer(moneytransfer, bankAccount);
//		}
		
		return opnenMoneytransferList;
	}
	
	public BankAccount getAccountForOpenMoneytransfers(int accountId) {
		return dbController.getByIdFull(BankAccount.class, accountId);
	}
	
	public boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {

		boolean result = false;
		BankAccount transferAccount = resolveBankAccountForTransfer(bankAccount);

		if (transferAccount == null || !supportsTransferOrderType(transferAccount, moneyTransfer.getOrderType())) {
			log.warn("Transfer order type {} is not supported for account {}", moneyTransfer.getOrderType(),
					transferAccount != null ? transferAccount.getAccountName() : null);
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			dbController.insertOrUpdate(moneyTransfer);
			return false;
		}

		BankAccess bankAccess = initBankAccess(transferAccount, pin);
		if (bankAccess == null) {
			moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			dbController.insertOrUpdate(moneyTransfer);
			return false;
		}
		GBankingHBCICallback hbciCallback = new GBankingHBCICallback(bankAccess);
		hbciCallback.startStatusDialog();
		HBCIPassport passport = initBankConnection(bankAccess, hbciCallback);
		
		try (HBCIHandler handle = createHBCIHandler(VERSION.getId(), passport)) {

			Konto hbciSenderAccount = getSenderAccount(passport, transferAccount); /* passport.getAccounts()[0]; */
			Konto hbciRecipientAccount = createRecipientAccount(moneyTransfer);
			HBCIJob<?> job = createTransferJob(handle, moneyTransfer, hbciSenderAccount, hbciRecipientAccount);

			job.addToQueue();
			HBCIExecStatus status = handle.execute();
			HBCIJobResult jobResult = job.getJobResult();

			result = status.isOK() && (jobResult == null || jobResult.isOK());
			if (!result) {
				log.error("HBCI Error, Status: {}", status);
				if (jobResult != null && !jobResult.isOK()) {
					hbciCallback.handleFailure(jobResult.getJobStatus().toString());
				}
				hbciCallback.handleFailure(status.getErrorString());
				moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			} else {
				if (moneyTransfer.getOrderType() == OrderType.TRANSFER || moneyTransfer.getOrderType() == OrderType.REALTIME_TRANSFER) {
					moneyTransfer.setExecutionDate(LocalDate.now());
				}
				moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);		
			}
			dbController.insertOrUpdate(moneyTransfer);

		} catch (Exception ex) {
			hbciCallback.handleException(ex);
			ex.printStackTrace();
			throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND"), ex);
		} finally {
			clearSecret(pin);
			hbciCallback.finishStatusDialog();
			passport.close();
		}
		return result;
	}
	
	public MoneyTransfer saveMoneyTransferToDB(MoneyTransferForm mtf) {
		
		/*
		 * Recipient recipientForm = mt.getRecipient();
		 * 
		 * Recipient recipientDb = dbController.find(Recipient.class, (new
		 * Recipient(recipientForm.getName(), recipientForm.getIban()))); if
		 * (recipientDb == null) { recipientDb = new Recipient(recipientForm.getName(),
		 * recipientForm.getIban(), recipientForm.getBic(), null, null,
		 * recipientForm.getBank(), Source.MONEYTRANSFER); recipientDb =
		 * dbController.insertOrUpdate(recipientDb);
		 * log.info("created new Recipient with id: {}", recipientDb.getId()); }
		 * 
		 */

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
		HBCIUtils.init(props, /* new HBCICallbackSwing() */ hbciCallback);

		HBCIUtils.setParam("client.passport.PinTan.init", "1");

		Setting settingProductKey = dbController.getAll(Setting.class).stream().filter(setting -> "productKey".equals(setting.getAttribute())).findAny()
				.orElse(null);
		if (settingProductKey != null && settingProductKey.getValue() != null)
			HBCIUtils.setParam("client.product.name", settingProductKey.getValue());
		else
			log.warn("Product-Key noch found!");

		HBCIPassport passport = AbstractHBCIPassport.getInstance("PinTanDB", bankAccess.getBlz()); //getInstance("PinTanDB");
		passport.setCountry("DE");

		BankInfo info = HBCIUtils.getBankInfo(bankAccess.getBlz());
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
			setCategories(account);
		}
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
	

	private void setCategories(BankAccount checkedAccount) {
		// TODO Auto-generated method stub
		
	}
	
	void refreshBankAccount(BankAccount bankAccount) {
		List<Booking> bookingList = dbController.getAllByParent(Booking.class, bankAccount.getId());
		bankAccount.setBookings(bookingList);
	}
	
	HBCIHandler createHBCIHandler(String versionId, HBCIPassport passport) {
	    return new HBCIHandler(versionId, passport);
	}

	private HBCIJob<?> createTransferJob(HBCIHandler handle, MoneyTransfer moneyTransfer, Konto senderAccount, Konto recipientAccount) {
		HBCIJob<?> job = handle.newJob(resolveJobName(moneyTransfer.getOrderType()));
		job.setParam("src", senderAccount);
		job.setParam("dst", recipientAccount);
		job.setParam("btg.value", moneyTransfer.getAmount().toPlainString());
		job.setParam("btg.curr", "EUR");
		job.setParam("usage", moneyTransfer.getPurpose());

		switch (moneyTransfer.getOrderType()) {
		case SCHEDULED_TRANSFER -> job.setParam("date", toUtilDate(moneyTransfer.getExecutionDate()));
		case STANDING_ORDER -> applyStandingOrderParams(job, moneyTransfer);
		case TRANSFER, REALTIME_TRANSFER -> {
			// no-op
		}
		default -> throw new GBankingException("Unsupported transfer order type: " + moneyTransfer.getOrderType());
		}

		return job;
	}

	private void applyStandingOrderParams(HBCIJob<?> job, MoneyTransfer moneyTransfer) {
		if (moneyTransfer.getExecutionDate() == null || moneyTransfer.getExecutionDay() == null || moneyTransfer.getStandingorderMode() == null) {
			throw new GBankingException(getText("ALERT_MONEYTRANSFER_REQUIRED_FIELD_MISSING"));
		}

		job.setParam("firstdate", toUtilDate(moneyTransfer.getExecutionDate()));
		job.setParam("timeunit", "M");
		job.setParam("turnus", determineStandingOrderTurnus(moneyTransfer.getStandingorderMode()));
		job.setParam("execday", formatStandingOrderExecutionDay(moneyTransfer.getExecutionDay()));
	}

	private String resolveJobName(OrderType orderType) {
		return switch (orderType) {
		case TRANSFER -> "UebSEPA";
		case REALTIME_TRANSFER -> "InstUebSEPA";
		case SCHEDULED_TRANSFER -> "TermUebSEPA";
		case STANDING_ORDER -> "DauerSEPANew";
		};
	}

	private Konto createRecipientAccount(MoneyTransfer moneyTransfer) {
		Konto hbciRecipientAccount = new Konto();
		hbciRecipientAccount.iban = moneyTransfer.getRecipient().getIban();
		hbciRecipientAccount.bic = moneyTransfer.getRecipient().getBic();
		hbciRecipientAccount.name = moneyTransfer.getRecipient().getName();
		return hbciRecipientAccount;
	}

	private Date toUtilDate(LocalDate date) {
		return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private String determineStandingOrderTurnus(de.gbanking.db.dao.enu.StandingorderMode standingorderMode) {
		return switch (standingorderMode) {
		case MONTHLY -> "1";
		case BIMONTHLY -> "2";
		case QUARTERLY -> "3";
		case SEMI_ANNUALLY -> "6";
		case ANNUALLY -> "12";
		};
	}

	private String formatStandingOrderExecutionDay(Integer executionDay) {
		if (executionDay == null) {
			return null;
		}
		if (executionDay >= 31) {
			return "31";
		}
		return String.format("%02d", executionDay);
	}

	BankAccount resolveBankAccountForTransfer(BankAccount bankAccount) {
		if (bankAccount == null) {
			return null;
		}

		if (bankAccount.getId() > 0) {
			BankAccount accountFromDb = dbController.getByIdFull(BankAccount.class, bankAccount.getId());
			if (accountFromDb != null) {
				return accountFromDb;
			}
		}

		return bankAccount;
	}

	boolean supportsTransferOrderType(BankAccount bankAccount, OrderType orderType) {
		if (bankAccount == null || orderType == null) {
			return false;
		}

		List<BusinessCase> allowedBusinessCases = bankAccount.getAllowedBusinessCases();
		if (allowedBusinessCases == null || allowedBusinessCases.isEmpty()) {
			return true;
		}

		Set<String> supportedCases = allowedBusinessCases.stream()
				.map(BusinessCase::getCaseValue)
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.map(String::toUpperCase)
				.collect(java.util.stream.Collectors.toSet());

		return getRequiredBusinessCases(orderType).stream().anyMatch(supportedCases::contains);
	}

	private void clearSecret(char[] secret) {
		if (secret != null) {
			Arrays.fill(secret, '\0');
		}
	}

	Set<String> getRequiredBusinessCases(OrderType orderType) {
		return switch (orderType) {
		case TRANSFER -> Set.of("UEBSEPA", "HKCCS");
		case REALTIME_TRANSFER -> Set.of("INSTUEBSEPA", "HKIPZ");
		case SCHEDULED_TRANSFER -> Set.of("TERMUEBSEPA", "HKCSE");
		case STANDING_ORDER -> Set.of("DAUERSEPANEW", "HKDSE");
		};
	}

	
	/** shortcut methods **/
	
	private LocalDate getAccountLastBookingDate(BankAccount bankAccount){
		return dbController.getSingleResultField(bankAccount, StatementsConfig.StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, LocalDate.class);
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
