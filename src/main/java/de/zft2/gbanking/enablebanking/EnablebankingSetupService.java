package de.zft2.gbanking.enablebanking;

import static de.zft2.gbanking.enablebanking.EnablebankingJson.firstText;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.hasText;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.trimToNull;
import static de.zft2.gbanking.enablebanking.EnablebankingJson.upper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
		return dbController.executeInTransaction(() -> {
			boolean success = ServiceRegistry.getService(BankAccessService.class).saveBankAccessAccountsToDB(bankAccess);
			if (!success) {
				throw new GBankingException("Enablebanking access and accounts were not saved");
			}
			return true;
		});
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
			if (matches.stream().anyMatch(account -> hasPriorityAccess(account, existingAccesses))) {
				continue;
			}
			BankAccount reusableAccount = matches.stream()
					.filter(account -> account.getBankAccessId() == null || account.getBankAccessId() <= 0)
					.findFirst().orElse(null);
			selectable.add(mapAccount(aspsp, remoteAccount, reusableAccount));
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
		boolean sameIban = hasText(remote.iban()) && equalsIgnoreCase(remote.iban(), existing.getIban());
		return sameProviderId || sameIban;
	}

	private BankAccount mapAccount(EnablebankingAspsp aspsp, EnablebankingRemoteAccount remote,
			BankAccount existing) {
		BankAccount account = existing != null ? existing : new BankAccount();
		account.setProviderAccountId(remote.identificationHash());
		account.setAccountName(existing != null && hasText(existing.getAccountName()) ? existing.getAccountName()
				: firstText(remote.name(), remote.details(), remote.product(), aspsp.name() + " - " + firstText(remote.iban(), remote.number())));
		account.setAccountType(mapAccountType(remote.cashAccountType()));
		account.setCurrency(remote.currency());
		account.setIban(remote.iban());
		account.setBic(remote.bic());
		account.setNumber(remote.number());
		account.setBankName(aspsp.name());
		account.setCountry(aspsp.country());
		account.setOwnerName(remote.ownerName());
		account.setSEPAAccount(hasText(remote.iban()));
		account.setOfflineAccount(false);
		account.setAccountState(AccountState.ACTIVE);
		account.setSource(Source.ONLINE);
		account.setAllowedBusinessCases(List.of());
		account.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return account;
	}

	private AccountType mapAccountType(String cashAccountType) {
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

	private boolean equalsIgnoreCase(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

}
