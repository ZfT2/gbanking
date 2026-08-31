package de.zft2.gbanking.service.bankaccess;

import static de.zft2.gbanking.util.TextValues.firstNonBlank;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.StatementsConfig;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessFints;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Setting;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.hbci.GBankingHBCICallback;
import de.zft2.gbanking.hbci.GBankingKUmsAllCamt;
import de.zft2.gbanking.hbci.HbciProperties;
import de.zft2.gbanking.hbci.InstantPaymentStatusSyntaxExtension;
import de.zft2.gbanking.logging.GBankingLoggingHandler;
import de.zft2.gbanking.logging.LoggingSettings;
import de.zft2.gbanking.logging.SensitiveDataMasker;
import de.zft2.gbanking.mapper.HbciMapper;
import de.zft2.gbanking.paypal.PaypalAccountService;
import de.zft2.gbanking.paypal.PaypalSupport;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.HbciSessionRunner;
import de.zft2.gbanking.service.ServiceRegistry;

public class BankAccessService extends AbstractDbService {

	private static final String CAMT_TRANSACTION_JOB = "KUmsAllCamt";
	private static Logger log = LogManager.getLogger(BankAccessService.class);

	private static GBankingLoggingHandler logHandler = GBankingLoggingHandler.getInstance();
	private final PaypalAccountService paypalAccountService = ServiceRegistry.getService(PaypalAccountService.class);

	public HBCIHandler createHBCIHandler(String versionId, HBCIPassport passport) {
		HBCIHandler handler = new HBCIHandler(versionId, passport);
		InstantPaymentStatusSyntaxExtension.apply(handler);
		return handler;
	}

	@SuppressWarnings("unchecked")
	public <T extends HBCIJobResult> HBCIJob<T> newHbciJob(HBCIHandler handle, String jobDescription) {
		if (CAMT_TRANSACTION_JOB.equals(jobDescription)) {
			return (HBCIJob<T>) new GBankingKUmsAllCamt(handle);
		}
		return handle.newJob(jobDescription);
	}

	@SuppressWarnings("unchecked")
	public <T extends HBCIJobResult> HBCIJob<T> newLowlevelHbciJob(HBCIHandler handle, String jobDescription) {
		return handle.newLowlevelJob(jobDescription);
	}

	HBCIPassport initBankConnection(BankAccess bankAccess) {
		return initBankConnection(bankAccess, new GBankingHBCICallback(bankAccess));
	}

	public HBCIPassport initBankConnection(BankAccess bankAccess, GBankingHBCICallback hbciCallback) {
		LoggingSettings.applyHbciLogLevel();
		Properties props = HbciProperties.createBaseProperties();
		Setting settingProductKey = dbController.getAll(Setting.class).stream().filter(setting -> "productKey".equals(setting.getAttribute())).findAny()
				.orElse(null);
		if (settingProductKey != null && settingProductKey.getValue() != null) {
			props.setProperty(HbciProperties.PRODUCT_KEY_PARAM, settingProductKey.getValue());
		} else {
			log.warn("Product key not found.");
		}
		HBCIUtils.init(props, hbciCallback);

		HBCIPassport passport = AbstractHBCIPassport.getInstance("PinTanDB", bankAccess.getFints().getBlz());
		log.debug("Created HBCI passport for bank code {}", () -> SensitiveDataMasker.maskIdentifier(bankAccess.getFints().getBlz()));
		passport.setCountry("DE");

		BankInfo info = HBCIUtils.getBankInfo(bankAccess.getFints().getBlz());
		if (info == null || info.getPinTanAddress() == null || info.getPinTanAddress().isBlank()) {
			throw new GBankingException("No FinTS address available for bank code: " + bankAccess.getFints().getBlz());
		}
		passport.setHost(info.getPinTanAddress());
		passport.setPort(443);

		/*
		 * Art der Nachrichten-Codierung. Bei Chipkarte/Schluesseldatei wird "None"
		 * verwendet. Bei PIN/TAN kommt "Base64" zum Einsatz.
		 */
		passport.setFilterType(HbciEncodingFilterType.BASE64.toString());
		log.info("Initialized HBCI bank connection for bank code {}", () -> SensitiveDataMasker.maskIdentifier(bankAccess.getFints().getBlz()));
		return passport;
	}

	public BankAccess initBankAccess(BankAccount bankAccount, char[] pin) {
		if (!hasConfiguredBankAccess(bankAccount)) {
			return null;
		}

		BankAccess bankAccess = dbController.getBankAccessById(bankAccount.getBankAccessId());
		if (bankAccess == null) {
			log.warn("Configured BankAccess {} not found for account id {}, IBAN: {} / Nr.: {}", bankAccount::getBankAccessId,
					bankAccount::getId, () -> SensitiveDataMasker.maskIban(bankAccount.getIban()),
					() -> SensitiveDataMasker.maskAccountNumber(bankAccount.getNumber()));
			return null;
		}

		bankAccess.setPin(pin);
		return bankAccess;
	}

	public boolean hasConfiguredBankAccess(BankAccount bankAccount) {
		if (bankAccount == null) {
			log.warn("No BankAccount provided for BankAccess check");
			return false;
		}

		if (bankAccount.getBankAccessId() != null && bankAccount.getBankAccessId() > 0) {
			return true;
		}

		log.warn("No BankAccess configured for account id {}, IBAN: {} / Nr.: {}", bankAccount::getId,
				() -> SensitiveDataMasker.maskIban(bankAccount.getIban()),
				() -> SensitiveDataMasker.maskAccountNumber(bankAccount.getNumber()));
		return false;
	}

	public boolean addNewBankAccess(BankAccess bankAccess) {
		if (PaypalSupport.isPaypal(bankAccess)) {
			try {
				return paypalAccountService.initialize(bankAccess);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new GBankingException(getText("EXCEPTION_ADD_BANKACCESS"), exception);
			} finally {
				HbciSessionRunner.clearSecret(bankAccess != null ? bankAccess.getPin() : null);
			}
		}

		log.info("Starting bank access setup for bank code {}",
				bankAccess != null ? SensitiveDataMasker.maskIdentifier(bankAccess.getFints().getBlz()) : null);

		try {
			return new HbciSessionRunner().run(bankAccess, bankAccess != null ? bankAccess.getPin() : null,
					session -> addNewBankAccess(bankAccess, session));
		} catch (InterruptedException ex) {
			log.error("Bank access setup failed for bank code {}",
					bankAccess != null ? SensitiveDataMasker.maskIdentifier(bankAccess.getFints().getBlz()) : null, ex);
			Thread.currentThread().interrupt();
			throw new GBankingException(getText("EXCEPTION_ADD_BANKACCESS"), ex);
		}
	}

	private boolean addNewBankAccess(BankAccess bankAccess, HbciSessionRunner.HbciSession session) {
		HBCIPassport passport = session.passport();
		logHandler.logRetrivedBankAccessInfo(passport, false);
		List<BankAccount> bankAccountList = mapPassportAccounts(passport);
		applyPassportData(bankAccess, passport, bankAccountList);

		BankAccess bankAccessDb = dbController.getBankAccessByBlz(bankAccess.getFints().getBlz());
		if (bankAccessDb != null) {
			bankAccess.setId(bankAccessDb.getId());
		}

		HBCIExecStatus status = session.handler().execute();

		if (!status.isOK()) {
			log.log(Level.ERROR, () -> getText("ERROR_HBCI_STATE", status.getErrorString()));
			session.callback().handleFailure(status.getErrorString());
		}

		boolean success = status.isOK();
		if (success) {
			syncBankAccessIdByBlz(bankAccess);
		}
		log.info("Finished bank access setup for bank code {}, accounts={}, success={}",
				() -> SensitiveDataMasker.maskIdentifier(bankAccess.getFints().getBlz()), bankAccountList::size, () -> success);
		return success;
	}

	public boolean refreshBankAccessParameterData(BankAccess bankAccess, char[] pin) {
		if (bankAccess == null || bankAccess.getId() <= 0) {
			HbciSessionRunner.clearSecret(pin);
			return false;
		}

		BankAccess refreshAccess = dbController.getBankAccessById(bankAccess.getId());
		if (refreshAccess == null) {
			HbciSessionRunner.clearSecret(pin);
			return false;
		}

		refreshAccess.setPin(pin);
		log.info("Starting BPD/UPD refresh for bank access id {}, bank code {}", refreshAccess::getId,
				() -> SensitiveDataMasker.maskIdentifier(refreshAccess.getFints().getBlz()));

		try {
			return new HbciSessionRunner().run(refreshAccess, pin, session -> refreshBankAccessParameterData(bankAccess, refreshAccess, session));
		} catch (InterruptedException ex) {
			log.error("BPD/UPD refresh failed for bank access id {}", bankAccess.getId(), ex);
			Thread.currentThread().interrupt();
			throw new GBankingException(getText("ERROR_BANK_ACCESS_REFRESH"), ex);
		}
	}

	private boolean refreshBankAccessParameterData(BankAccess targetBankAccess, BankAccess refreshAccess, HbciSessionRunner.HbciSession session) {
		HBCIDialogStatus status = session.handler().refreshXPD(HBCIHandler.REFRESH_BPD + HBCIHandler.REFRESH_UPD);
		if (status != null && !status.isOK()) {
			log.error("BPD/UPD refresh dialog failed: {}", status);
			session.callback().handleFailure(status.getErrorString());
			return false;
		}
		session.passport().saveChanges();

		List<BankAccount> bankAccountList = mapPassportAccounts(session.passport());
		applyPassportData(refreshAccess, session.passport(), bankAccountList);
		syncBankAccessIdByBlz(refreshAccess);

		BankAccess savedBankAccess = dbController.insertOrUpdate(refreshAccess);
		if (savedBankAccess != null) {
			refreshAccess.setId(savedBankAccess.getId());
		}
		dbController.insertOrUpdatePD(refreshAccess);
		boolean accountsSaved = saveBankAccessAccountsToDB(refreshAccess);
		copyBankAccessData(refreshAccess, targetBankAccess);

		log.info("Finished BPD/UPD refresh for bank access id {}, accounts={}, success={}", refreshAccess.getId(), bankAccountList.size(), accountsSaved);
		return accountsSaved;
	}

	private List<BankAccount> mapPassportAccounts(HBCIPassport passport) {
		List<BankAccount> bankAccountList = new ArrayList<>();
		Konto[] konten = passport.getAccounts();
		if (konten == null || konten.length == 0) {
			log.error("Keine Konten ermittelbar");
			return bankAccountList;
		}

		log.info("Retrieved {} accounts for bank access.", konten.length);
		for (Konto konto : konten) {
			logHandler.logRetrievedAccountInfo(konto);
			if (konto.allowedGVs == null) {
				konto.allowedGVs = List.of();
			}

			log.debug("Allowed HBCI business cases for retrieved account:");
			for (Object gv : konto.allowedGVs) {
				log.debug("GV: {}", gv);
			}
			bankAccountList.add(HbciMapper.mapKontoToBankAccount(passport.getInstName(), konto));
		}
		return bankAccountList;
	}

	private void applyPassportData(BankAccess bankAccess, HBCIPassport passport, List<BankAccount> bankAccountList) {
		BankAccessFints fints = bankAccess.getFints();
		fints.setCountry(firstNonBlank(passport.getCountry(), fints.getCountry()));
		fints.setBlz(firstNonBlank(passport.getBLZ(), fints.getBlz()));
		bankAccess.setBankName(firstNonBlank(passport.getInstName(), bankAccess.getBankName()));
		fints.setHbciURL(firstNonBlank(passport.getHost(), fints.getHbciURL()));
		if (passport.getPort() != null) {
			fints.setPort(passport.getPort());
		}
		fints.setUserId(firstNonBlank(passport.getUserId(), fints.getUserId()));
		fints.setCustomerId(firstNonBlank(passport.getCustomerId(), firstNonBlank(fints.getCustomerId(), fints.getUserId())));
		fints.setHbciVersion(firstNonBlank(passport.getHBCIVersion(), fints.getHbciVersion()));
		fints.setBpdVersion(firstNonBlank(passport.getBPDVersion(), fints.getBpdVersion()));
		fints.setUpdVersion(firstNonBlank(passport.getUPDVersion(), fints.getUpdVersion()));
		HbciEncodingFilterType filterType = HbciEncodingFilterType.forString(passport.getFilterType());
		if (filterType != null) {
			fints.setFilterType(filterType);
		}
		fints.setBpd(passport.getBPD());
		fints.setUpd(passport.getUPD());
		bankAccess.setAccounts(bankAccountList);
		bankAccess.setActive(true);
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
	}

	private void syncBankAccessIdByBlz(BankAccess bankAccess) {
		if (bankAccess == null || bankAccess.getId() > 0 || bankAccess.getFints().getBlz() == null) {
			return;
		}
		BankAccess bankAccessDb = dbController.getBankAccessByBlz(bankAccess.getFints().getBlz());
		if (bankAccessDb != null) {
			bankAccess.setId(bankAccessDb.getId());
		}
	}

	private void copyBankAccessData(BankAccess source, BankAccess target) {
		if (source == target || source == null || target == null) {
			return;
		}
		target.setId(source.getId());
		target.setAccessType(source.getAccessType());
		target.setBankName(source.getBankName());
		target.setActive(source.isActive());
		target.setFints(source.getFints());
		target.setPaypal(source.getPaypal());
		target.setEnablebanking(source.getEnablebanking());
		target.setAccounts(source.getAccounts());
		target.setUpdatedAt(source.getUpdatedAt());
	}

	public boolean deleteBankAccessFromDB(BankAccess bankAccess) {
		boolean result = true;

		if (bankAccess == null) {
			log.warn("Abort deleteBankAccessFromDB execution. bankAccess is null!");
			return false;
		}

		log.info("Deleting bank access id {} and converting linked accounts to manual accounts.", bankAccess.getId());

		for (BankAccount account : getAccounts(bankAccess)) {
			account.setSource(Source.MANUELL);
		}

		List<BankAccount> accounts = getAccounts(bankAccess);
		if (!accounts.isEmpty()) {
			result &= dbController.executeSimpleUpdate(accounts, StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, BankAccount.class) >= 0;
		}
		result &= dbController.delete(bankAccess, StatementsConfig.StatementType.DELETE_BANKACCESS_BY_ID);
		log.info("Deleted bank access id {}, success={}", bankAccess.getId(), result);

		return result;
	}

	public boolean saveBankAccessAccountsToDB(BankAccess bankAccess) {

		boolean result = true;
		if (bankAccess.getId() <= 0) {
			BankAccess savedBankAccess = dbController.insertOrUpdate(bankAccess);
			if (savedBankAccess == null || savedBankAccess.getId() <= 0) {
				return false;
			}
			bankAccess.setId(savedBankAccess.getId());
		}
		log.info("Saving {} accounts for bank access id {}.", bankAccess.getAccounts() != null ? bankAccess.getAccounts().size() : 0, bankAccess.getId());

		for (BankAccount bankAccount : bankAccess.getAccounts()) {
			bankAccount.setBankAccessId(bankAccess.getId());
			bankAccount.setOfflineAccount(false);
			bankAccount.setAccountState(AccountState.ACTIVE);
			result &= dbController.insertOrUpdate(bankAccount) != null;
			result &= dbController.insertBusinessCases(bankAccount);
		}

		log.info("Saved accounts for bank access id {}, success={}", bankAccess.getId(), result);
		return result;
	}

	public List<BankAccount> getLinkablePaypalAccounts() {
		return paypalAccountService.getLinkableAccounts();
	}

	public void linkPaypalAccount(BankAccess bankAccess, BankAccount bankAccount) {
		paypalAccountService.linkAccount(bankAccess, bankAccount);
	}

	private List<BankAccount> getAccounts(BankAccess bankAccess) {
		return bankAccess.getAccounts() != null ? bankAccess.getAccounts() : List.of();
	}

}
