package de.gbanking;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
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
import de.gbanking.db.dao.enu.SqlFilter;
import de.gbanking.exception.GBankingException;
import de.gbanking.fileimport.institute.InstituteFileImportBean;
import de.gbanking.gui.dto.MoneyTransferForm;
import de.gbanking.hbci.GBankingHBCICallback;
import de.gbanking.mapper.HbciMapper;

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

		HBCIPassport passport = initBankConnection(bankAccess);

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
			bankAccess.setUpd(passport.getBPD());
			bankAccess.setAccounts(bankAccountList);
			
			BankAccess bankAccessDb = dbController.getBankAccessByBlz(bankAccess.getBlz());
			if (bankAccessDb != null)
				bankAccess.setId(bankAccessDb.getId());
			
			HBCIExecStatus status = handle.execute();
			
			//delete PIN
			 Arrays.fill(bankAccess.getPin(),'0');

			if (!status.isOK())
				log.log(Level.ERROR, () -> messages.getFormattedMessage("ERROR_HBCI_STATE", status.getErrorString()));
			
			return status.isOK();
			
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new GBankingException(getText("EXCEPTION_ADD_BANKACCESS"), ex);
		} finally {
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
		
		Calendar lastBookingDate = getAccountLastBookingDate(bankAccount);
		
		HBCIPassport passport = initBankConnection(bankAccess);
		
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
						umsatzJob = createAndAddHbciJob(handle, "KUmsAllCamt", Map.of("my", konto, "startdate", lastBookingDate.getTime()));
					} else {
						umsatzJob = createAndAddHbciJob(handle, "KUmsAllCamt", Map.of("my", konto));
					}

					HBCIExecStatus status = handle.execute();
					result = status.isOK();
					
					if (!result)
						log.error("HBCI Error, Status: {}", status);

					readSaldo(saldoJob);

					List<UmsLine> buchungen = readUms(umsatzJob);
					
					saveHbciBookingsForAccount(bankAccount, buchungen);

					break;
				}
			}

		} catch (Exception e) {
			log.error("Error in handling HBCI calls: ", e);
			result = false;
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
	
	
//	HBCIJob<?> createAndAddHbciJob(HBCIHandler handle, String jobDescription, Map<String, Object> params) {
//		HBCIJob<?> job = handle.newJob(jobDescription);
//		for (Entry<String, Object> param : params.entrySet()) {
//			switch (param.getValue()) {
//				case String s -> job.setParam(param.getKey(), s);
//				case Date d -> job.setParam(param.getKey(), d);
//				case Integer i -> job.setParam(param.getKey(), i);
//				case Konto k -> job.setParam(param.getKey(), k);
//				default -> log.error("Unknown HBCI Job Param Type: {}", param.getValue().getClass());
//			}
//
//			job.addToQueue();
//		}
//		return job;
//	}

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
		return dbController.getById(BankAccount.class, accountId);
	}
	
	public boolean executeTransfer(MoneyTransfer moneyTransfer, BankAccount bankAccount, char[] pin) {

		boolean result = false;

		BankAccess bankAccess = initBankAccess(bankAccount, pin);
		HBCIPassport passport = initBankConnection(bankAccess);
		
		try (HBCIHandler handle = createHBCIHandler(VERSION.getId(), passport)) {

			Konto hbciSenderAccount = getSenderAccount(passport, bankAccount); /* passport.getAccounts()[0]; */
			Konto hbciRecipientAccount = new Konto();
			hbciRecipientAccount.iban = moneyTransfer.getRecipient().getIban();
			hbciRecipientAccount.bic = moneyTransfer.getRecipient().getBic();
			hbciRecipientAccount.name = moneyTransfer.getRecipient().getName();

			HBCIJob<?> job = handle.newJob("SEPAU");

			job.setParam("src", hbciSenderAccount);
			job.setParam("dst", hbciRecipientAccount);
			job.setParam("btg.value", moneyTransfer.getAmount().toPlainString());
			job.setParam("btg.curr", "EUR");
			job.setParam("usage", moneyTransfer.getPurpose());

			job.addToQueue();
			HBCIExecStatus status = handle.execute();

			result = status.isOK();
			if (!result) {
				log.error("HBCI Error, Status: {}", status);
				moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.ERROR);
			} else {
				moneyTransfer.setExecutionDate(Calendar.getInstance());
				moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);		
			}
			dbController.insertOrUpdate(moneyTransfer);

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new GBankingException(getText("EXCEPTION_MONEYTRANSFER_SENDING_ACCOUNT_NOT_FOUND"), ex);
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

		MoneyTransfer moneyTransfer = new MoneyTransfer(mtf.getBankAccount().getId(), OrderType.TRANSFER, recipient.getId(), mtf.getPurpose(), mtf.getAmount(),
				Calendar.getInstance(), MoneyTransferStatus.NEW);
		
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
		
		List<BankAccount> accountListForRule = categoryRule.getBankAccountList();
		
		List<CategoryParam> paramList = new ArrayList<>();
		
//		for (BankAccount bankAccount : accountListForRule) {
			
//			StringBuilder sb = new StringBuilder("UPDATE BOOKING set category_id = ? WHERE");
			StringBuilder sb = new StringBuilder("SELECT id FROM BOOKING b, RECIPIENT r WHERE b.recipient_id = r.id ");
			
				addFilterParam("amount", ">=", categoryRule.getFilterAmountFrom(), paramList);
				addFilterParam("amount", "<=", categoryRule.getFilterAmountTo(), paramList);

				addFilterParam("dateBooking", ">=", categoryRule.getFilterDateFrom(), paramList);
				addFilterParam("dateBooking", "<=", categoryRule.getFilterDateTo(), paramList);

				addFilterParam("purpose", "LIKE", categoryRule.getFilterPurpose(), paramList);
				addFilterParam("name", "LIKE", categoryRule.getFilterRecipient(), paramList);

				addFilterParam("account_id", "IN", categoryRule.getBankAccountList().stream().map(account -> String.valueOf(account.getId())).collect(Collectors.joining(", ")), paramList);
				
				for (CategoryParam param : paramList) {
					sb.append("AND ").append(param.getParamField()).append(" ").append(param.getOperator()).append(" ").append(param.getValue());
				}

				SqlFilter sqlFilter = SqlFilter.SPECIFIC_QUERY;
				sqlFilter.setSql(sb.toString());
				List<Booking> bookingListToCategorize = dbController.getAllWithFilter(Booking.class, sqlFilter);
				//dbController.gets

				Set<Integer> bookingIdSet = bookingListToCategorize.stream().map(Booking::getId).collect(Collectors.toSet());
				Map<Category, Set<Integer>> categoryBookingMap = new HashMap<>();
				categoryBookingMap.put(categoryRule.getCategory(), bookingIdSet);

				dbController.updateBookingsWithCategories(categoryBookingMap);

//			for (Booking booking : bankAccount.getBookings()) {
//				categoryRule.getFilterAmountFrom();
//				categoryRule.getFilterAmountTo();
//			}
//		}
	}
	
	private void addFilterParam(String field, String operator, Object value, List<CategoryParam> paramList) {
		if (value != null)
			paramList.add(new CategoryParam(field, operator, value));
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
		
		
		Properties props = new Properties();
		HBCIUtils.init(props, /* new HBCICallbackSwing() */ new GBankingHBCICallback(bankAccess));

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

	
	/** shortcut methods **/
	
	private Calendar getAccountLastBookingDate(BankAccount bankAccount){
		return dbController.getSingleResultField(bankAccount, StatementsConfig.StatementType.SELECT_ACCOUNT_LAST_BOOKING_DATE, Calendar.class);
	}

	public void setup() {
		InstituteFileImportBean instituteFileImport = new InstituteFileImportBean();
		try {
			instituteFileImport.runImport();
		} catch (IOException e) {
			log.error("Error importing bank institute list: ", e);
		}
		
	}

}
