package de.zft2.gbanking.enablebanking;

import static de.zft2.gbanking.enablebanking.EnablebankingJson.firstText;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.upper;
import static de.zft2.gbanking.util.TextValues.equalsIgnoreCaseNonNull;
import static de.zft2.gbanking.util.TextValues.hasText;
import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccessEnablebanking;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Psd2ClientConfiguration;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Psd2ClientMode;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.paypal.PaypalSupport;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.bankaccess.BankAccessService;
import de.zft2.gbanking.util.IbanStructure;

public class EnablebankingSetupService extends AbstractDbService {

	private final EnablebankingAuthorizationService authorizationService;

	public EnablebankingSetupService() {
		this(new EnablebankingAuthorizationService());
	}

	EnablebankingSetupService(EnablebankingAuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
	}

	public Psd2ClientConfiguration getPersonalConfiguration() {
		return dbController.getAll(Psd2ClientConfiguration.class).stream()
				.filter(configuration -> configuration.getClientMode() == Psd2ClientMode.PERSONAL)
				.min(Comparator.comparingInt(Psd2ClientConfiguration::getId))
				.orElseGet(Psd2ClientConfiguration::new);
	}

	public List<EnablebankingAspsp> configure(String applicationId, String privateKeyPem, String callbackUrl) {
		Psd2ClientConfiguration configuration = getPersonalConfiguration();
		configuration.setApplicationId(trimToNull(applicationId));
		configuration.setCallbackUrl(trimToNull(callbackUrl));
		if (privateKeyPem != null && !privateKeyPem.isBlank()) {
			configuration.setPrivateKeyPkcs8(EnablebankingPrivateKeyReader.readPkcs8(privateKeyPem));
		}
		validateConfiguration(configuration);
		new EnablebankingCallbackMaterialService().ensureMaterial(configuration);
		EnablebankingApiClient client = new EnablebankingApiClient(configuration);
		client.validateApplication();
		dbController.insertOrUpdate(configuration);
		return client.getAspsps().stream()
				.sorted(Comparator.comparing(EnablebankingAspsp::country).thenComparing(EnablebankingAspsp::name,
						String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public BankAccess authorize(Psd2ClientConfiguration configuration, EnablebankingAspsp aspsp,
			String psuType, EnablebankingAuthMethod authMethod) {
		EnablebankingSession session = authorizationService.authorize(configuration, aspsp, psuType,
				authMethod != null ? authMethod.name() : null);
		if (!session.isAuthorized()) {
			throw new EnablebankingException("Die Enablebanking-Sitzung wurde nicht autorisiert.");
		}
		dbController.insertOrUpdate(configuration);
		BankAccess bankAccess = new BankAccess();
		bankAccess.setAccessType(BankAccessType.ENABLEBANKING);
		bankAccess.setBankName(aspsp.name());
		bankAccess.setActive(true);
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		BankAccessEnablebanking accessData = new BankAccessEnablebanking();
		accessData.setPsd2ClientConfigurationId(configuration.getId());
		accessData.setAspspName(aspsp.name());
		accessData.setAspspCountry(aspsp.country());
		accessData.setPsuType(psuType);
		accessData.setAuthMethod(authMethod != null ? authMethod.name() : null);
		accessData.setSessionId(session.sessionId());
		accessData.setValidUntil(session.validUntil());
		bankAccess.setEnablebanking(accessData);
		bankAccess.setAccounts(mapSelectableAccounts(aspsp, session.accounts()));
		return bankAccess;
	}

	public boolean save(BankAccess bankAccess, List<BankAccount> selectedAccounts) {
		if (bankAccess == null || selectedAccounts == null || selectedAccounts.isEmpty()) {
			return false;
		}
		bankAccess.setAccounts(selectedAccounts);
		dbController.executeInTransaction(() -> {
			boolean success = ServiceRegistry.getService(BankAccessService.class).saveBankAccessAccountsToDB(bankAccess);
			if (!success) {
				throw new GBankingException("Enablebanking access and accounts were not saved");
			}
		});
		EnablebankingAccountTransactionService retrievalService = ServiceRegistry.getService(EnablebankingAccountTransactionService.class);
		selectedAccounts.forEach(account -> retrievalService.retrieve(account));
		return true;
	}

	public void cancelAuthorization(BankAccess bankAccess) {
		if (bankAccess == null || bankAccess.getEnablebanking() == null) {
			return;
		}
		Psd2ClientConfiguration configuration = dbController.getById(Psd2ClientConfiguration.class,
				bankAccess.getEnablebanking().getPsd2ClientConfigurationId());
		if (configuration != null) {
			new EnablebankingApiClient(configuration).deleteSession(bankAccess.getEnablebanking().getSessionId());
		}
	}

	/**
	 * Applies an account type corrected by the user. The automatically derived
	 * account name is updated as well, because it contains the account type. Custom
	 * account names are left untouched.
	 *
	 * @param account     the account to correct
	 * @param accountType the account type selected by the user
	 */
	public static void applyAccountType(BankAccount account, AccountType accountType) {
		if (account == null || accountType == null || accountType == account.getAccountType()) {
			return;
		}
		AccountType previousType = account.getAccountType();
		account.setAccountType(accountType);
		if (hasDerivedAccountName(account, previousType)) {
			account.setAccountName(createAccountName(accountType, account.getNumber(), account.getCurrency()));
		}
	}

	private List<BankAccount> mapSelectableAccounts(EnablebankingAspsp aspsp,
			List<EnablebankingRemoteAccount> remoteAccounts) {
		List<BankAccount> existingAccounts = dbController.getAll(BankAccount.class);
		List<BankAccess> existingAccesses = dbController.getAll(BankAccess.class);
		boolean directPaypalConfigured = existingAccesses.stream()
				.anyMatch(access -> access.isActive() && PaypalSupport.isPaypal(access));
		List<BankAccount> selectable = new ArrayList<>();
		for (EnablebankingRemoteAccount remoteAccount : remoteAccounts) {
			boolean incompleteAccount = !hasText(remoteAccount.identificationHash()) || !hasText(remoteAccount.currency());
			boolean directPaypalHasPriority = directPaypalConfigured && containsPaypal(aspsp.name());
			if (incompleteAccount || directPaypalHasPriority) {
				continue;
			}
			List<BankAccount> matches = existingAccounts.stream()
					.filter(account -> sameAccount(account, remoteAccount))
					.toList();
			if (matches.stream().noneMatch(account -> hasPriorityAccess(account, existingAccesses))) {
				BankAccount reusableAccount = matches.stream()
						.filter(account -> account.getBankAccessId() == null || account.getBankAccessId() <= 0)
						.findFirst().orElse(null);
				selectable.add(mapAccount(aspsp, remoteAccount, reusableAccount));
			}
		}
		return selectable;
	}

	private boolean hasPriorityAccess(BankAccount account, List<BankAccess> existingAccesses) {
		if (account.getBankAccessId() == null || account.getBankAccessId() <= 0) {
			return false;
		}
		return existingAccesses.stream()
				.filter(access -> access.getId() == account.getBankAccessId() && access.isActive())
				.anyMatch(access -> access.getAccessType() == BankAccessType.HBCI
						|| access.getAccessType() == BankAccessType.PAYPAL
						|| access.getAccessType() == BankAccessType.ENABLEBANKING);
	}

	private boolean sameAccount(BankAccount existing, EnablebankingRemoteAccount remote) {
		boolean sameProviderId = hasText(remote.identificationHash())
				&& remote.identificationHash().equals(existing.getProviderAccountId());
		boolean sameIban = hasText(remote.iban()) && equalsIgnoreCaseNonNull(remote.iban(), existing.getIban());
		return sameProviderId || sameIban;
	}

	private BankAccount mapAccount(EnablebankingAspsp aspsp, EnablebankingRemoteAccount remote,
			BankAccount existing) {
		return mapAccount(aspsp.name(), aspsp.country(), remote, existing);
	}

	static BankAccount mapAccount(String bankName, String country, EnablebankingRemoteAccount remote,
			BankAccount existing) {
		return mapAccount(bankName, country, remote, existing, false);
	}

	/**
	 * Maps the remote account details onto an already persisted account. The account type of the
	 * persisted account is kept, because Enablebanking often delivers a wrong or unspecific account
	 * type that the user has corrected in the setup dialog.
	 */
	static BankAccount mapAccountDetails(String bankName, String country, EnablebankingRemoteAccount remote,
			BankAccount existing) {
		return mapAccount(bankName, country, remote, existing, true);
	}

	private static BankAccount mapAccount(String bankName, String country, EnablebankingRemoteAccount remote,
			BankAccount existing, boolean keepAccountType) {
		BankAccount account = existing != null ? existing : new BankAccount();
		AccountType accountType = resolveAccountType(remote.cashAccountType(), account.getAccountType(),
				keepAccountType);
		// Some banks deliver the IBAN in the account number field, so it has to be moved to the IBAN field.
		String remoteIban = IbanStructure.normalizeIban(remote.number());
		String iban = remoteIban != null ? remoteIban : firstText(remote.iban(), account.getIban());
		String existingNumber = accountNumberWithoutIban(account.getNumber());
		String number = remoteIban != null
				? firstText(existingNumber, IbanStructure.extractAccountNumber(iban))
				: firstText(remote.number(), existingNumber, IbanStructure.extractAccountNumber(iban));
		String ibanBlz = InstituteLookupCache.extractGermanBlzFromIban(iban);
		boolean germanAccount = ibanBlz != null || "DE".equalsIgnoreCase(firstText(country, account.getCountry()));
		String blz = firstText(germanAccount ? InstituteLookupCache.normalizeBlzCandidate(remote.blz()) : null,
				account.getBlz(), ibanBlz);
		String bic = hasText(remote.bic()) ? remote.bic()
				: firstText(account.getBic(), InstituteLookupCache.findBicForBlz(blz).orElse(null));
		account.setProviderAccountId(firstText(remote.identificationHash(), account.getProviderAccountId()));
		account.setAccountType(accountType);
		account.setCurrency(firstText(remote.currency(), account.getCurrency()));
		if (existing == null || !hasText(existing.getAccountName())
				|| existing.getAccountName().equals(remote.ownerName())) {
			account.setAccountName(createAccountName(accountType, number, account.getCurrency()));
		}
		account.setIban(iban);
		account.setBic(bic);
		account.setBlz(blz);
		account.setNumber(number);
		account.setBankName(firstText(bankName, account.getBankName()));
		account.setCountry(firstText(country, account.getCountry()));
		account.setOwnerName(firstText(remote.ownerName(), account.getOwnerName()));
		account.setSEPAAccount(hasText(iban));
		account.setOfflineAccount(false);
		account.setAccountState(AccountState.ACTIVE);
		account.setSource(Source.ONLINE);
		account.setAllowedBusinessCases(List.of());
		account.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return account;
	}

	/**
	 * Determines the account type of an account. The type delivered by Enablebanking is used, unless the
	 * already persisted type has to be kept because the user corrected it.
	 */
	private static AccountType resolveAccountType(String cashAccountType, AccountType existingType,
			boolean keepExistingType) {
		AccountType mappedType = mapAccountType(cashAccountType);
		if (existingType == null) {
			return mappedType;
		}
		if (keepExistingType) {
			return existingType;
		}
		return mappedType != AccountType.UNKNOWN_ACCOUNT ? mappedType : existingType;
	}

	/**
	 * Returns the account number of an existing account, dropping an IBAN that was stored in the
	 * account number field by an earlier release.
	 */
	private static String accountNumberWithoutIban(String number) {
		return IbanStructure.normalizeIban(number) != null ? null : number;
	}

	private static String createAccountName(AccountType accountType, String number, String currency) {
		List<String> components = new ArrayList<>(4);
		if (accountType != AccountType.UNKNOWN_ACCOUNT) {
			components.add(accountType.toString());
		}
		if (hasText(number)) {
			components.add(number);
		}
		if (hasText(currency)) {
			components.add(currency);
		}
		components.add("(PSD2)");
		return String.join(" - ", components);
	}

	/**
	 * Checks whether the account name was derived from the account type during the setup, so that it
	 * can be rebuilt after the account type was corrected.
	 */
	private static boolean hasDerivedAccountName(BankAccount account, AccountType previousType) {
		String accountName = account.getAccountName();
		if (!hasText(accountName)) {
			return false;
		}
		String expectedName = createAccountName(previousType, account.getNumber(), account.getCurrency());
		return accountName.equals(expectedName);
	}

	private static AccountType mapAccountType(String cashAccountType) {
		return switch (upper(cashAccountType)) {
		case "CACC" -> AccountType.CURRENT_ACCOUNT;
		case "SVGS" -> AccountType.SAVINGS_ACCOUNT;
		case "CARD" -> AccountType.CREDIT_CARD;
		case "LOAN" -> AccountType.CREDIT_ACCOUNT;
		case "CASH" -> AccountType.CASH_ACCOUNT;
		default -> AccountType.UNKNOWN_ACCOUNT;
		};
	}

	private void validateConfiguration(Psd2ClientConfiguration configuration) {
		if (!hasText(configuration.getApplicationId())) {
			throw new EnablebankingException("Die Enablebanking Application-ID fehlt.");
		}
		if (configuration.getPrivateKeyPkcs8() == null) {
			throw new EnablebankingException("Bitte die PEM-Datei mit dem privaten Enablebanking-Schlüssel auswählen.");
		}
		if (!hasText(configuration.getCallbackUrl())) {
			throw new EnablebankingException("Die Enablebanking Callback-URL fehlt.");
		}
	}

	private boolean containsPaypal(String value) {
		return value != null && value.toLowerCase(Locale.ROOT).contains("paypal");
	}

}
