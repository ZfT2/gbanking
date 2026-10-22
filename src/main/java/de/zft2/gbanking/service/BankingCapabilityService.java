package de.zft2.gbanking.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.zft2.gbanking.db.BankCapabilityCache;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Bpd;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.ParameterDataBankAccess;
import de.zft2.gbanking.db.dao.Upd;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.paypal.PaypalSupport;
import de.zft2.gbanking.service.moneytransfer.BankOrderOperation;

public class BankingCapabilityService extends AbstractDbService {

	private enum Capability {
		TRANSFER(Set.of("UEBSEPA", "HKCCS"), Set.of("UEBSEPAPAR")),
		REALTIME_TRANSFER(Set.of("INSTUEBSEPA", "HKIPZ"), Set.of("INSTUEBSEPAPAR")),
		URGENT_TRANSFER(Set.of("UEBEIL", "HKEIL"), Set.of("UEBEILPAR")),
		SCHEDULED_TRANSFER(Set.of("TERMUEBSEPA", "HKCSE"), Set.of("TERMUEBSEPAPAR")),
		STANDING_ORDER(Set.of("DAUERSEPANEW", "HKCDE"), Set.of("DAUERSEPANEWPAR")),
		SCHEDULED_TRANSFER_EDIT(Set.of("TERMUEBSEPAEDIT", "HKCSA"), Set.of("TERMUEBSEPAEDITPAR")),
		SCHEDULED_TRANSFER_DELETE(Set.of("TERMUEBSEPADEL", "HKCSL"), Set.of("TERMUEBSEPADELPAR")),
		STANDING_ORDER_EDIT(Set.of("DAUERSEPAEDIT", "HKCDN"), Set.of("DAUERSEPAEDITPAR")),
		STANDING_ORDER_DELETE(Set.of("DAUERSEPADEL", "HKCDL"), Set.of("DAUERSEPADELPAR")),
		FOREIGN_TRANSFER(Set.of("UEBFOREIGN", "HKAUB"), Set.of("UEBFOREIGNPAR", "AUB")),
		SCHEDULED_TRANSFER_INVENTORY(Set.of("TERMUEBSEPALIST", "TERMUEBLIST", "HKCSB"), Set.of("TERMUEBSEPALISTPAR", "TERMUEBLISTPAR")),
		STANDING_ORDER_INVENTORY(Set.of("DAUERSEPALIST", "HKCDB"), Set.of("DAUERSEPALISTPAR")),
		ACCOUNT_TRANSACTIONS(Set.of("KUMSALLCAMT", "KUMSZEITCAMT", "KUMSZEIT", "HKKAZ", "HKCAZ"), Set.of("KUMSZEITCAMTPAR", "KUMSZEITPAR")),
		ACCOUNT_STATEMENTS(Set.of("KONTOAUSZUG", "KONTOAUSZUGPDF", "HKEKA", "HKEKP"), Set.of("KONTOAUSZUGPAR", "KONTOAUSZUGPDFPAR")),
		STOCK_PORTFOLIO(Set.of("WPDEPOTLIST", "HKWPD"), Set.of("WPDEPOTLISTPAR")),
		ACCOUNT_BALANCE(Set.of("SALDOREQ", "HKSAL"), Set.of("SALDOPAR"));

		private final Set<String> codes;
		private final Set<String> bpdKeyTokens;

		Capability(Set<String> codes, Set<String> bpdKeyTokens) {
			this.codes = normalizeCodes(codes);
			this.bpdKeyTokens = normalizeCodes(bpdKeyTokens);
		}

		private static Set<String> normalizeCodes(Set<String> codes) {
			return codes.stream().map(code -> normalizeToken(code)).collect(Collectors.toUnmodifiableSet());
		}

		private Set<String> codes() {
			return codes;
		}

		private Set<String> bpdKeyTokens() {
			return bpdKeyTokens;
		}
	}

	public boolean supportsTransferOrderType(BankAccount bankAccount, OrderType orderType) {
		if (orderType == null) {
			return false;
		}
		return isAvailable(bankAccount, capabilityForTransfer(orderType));
	}

	public boolean supportsBankOrderOperation(BankAccount bankAccount, OrderType orderType, BankOrderOperation operation) {
		if (orderType == null || operation == null) {
			return false;
		}
		if (operation == BankOrderOperation.CREATE) {
			return supportsTransferOrderType(bankAccount, orderType);
		}

		Capability capability = capabilityForExistingBankOrder(orderType, operation);
		return capability != null && isAvailable(bankAccount, capability);
	}

	public boolean supportsOrderInventory(BankAccount bankAccount, OrderType orderType) {
		if (orderType == OrderType.SCHEDULED_TRANSFER) {
			return isAvailable(bankAccount, Capability.SCHEDULED_TRANSFER_INVENTORY);
		}
		if (orderType == OrderType.STANDING_ORDER) {
			return isAvailable(bankAccount, Capability.STANDING_ORDER_INVENTORY);
		}
		return false;
	}

	public boolean supportsAccountTransactions(BankAccount bankAccount) {
		BankAccount resolvedAccount = resolveAccount(bankAccount);
		if (hasUsableBankAccess(resolvedAccount)) {
			BankAccess bankAccess = dbController.getBankAccessById(resolvedAccount.getBankAccessId());
			if (PaypalSupport.isPaypal(bankAccess) || bankAccess.getAccessType() == BankAccessType.ENABLEBANKING) {
				return bankAccess.isActive();
			}
		}
		return isAvailable(bankAccount, Capability.ACCOUNT_TRANSACTIONS) && isAvailable(bankAccount, Capability.ACCOUNT_BALANCE);
	}

	public boolean supportsBankOrderManagement(BankAccount bankAccount, OrderType orderType) {
		return supportsTransferOrderType(bankAccount, orderType) || supportsOrderInventory(bankAccount, orderType)
				|| supportsBankOrderOperation(bankAccount, orderType, BankOrderOperation.EDIT)
				|| supportsBankOrderOperation(bankAccount, orderType, BankOrderOperation.DELETE);
	}

	public boolean supportsAccountStatements(BankAccount bankAccount) {
		return isAvailable(bankAccount, Capability.ACCOUNT_STATEMENTS);
	}

	public boolean supportsStockPortfolio(BankAccount bankAccount) {
		return isAvailable(bankAccount, Capability.STOCK_PORTFOLIO);
	}

	public boolean supportsAccountUpdate(BankAccount bankAccount) {
		return bankAccount != null && (bankAccount.getAccountType() == AccountType.DEPOT
				? supportsStockPortfolio(bankAccount) : supportsAccountTransactions(bankAccount));
	}

	public boolean supportsBankMessages(BankAccess bankAccess) {
		BankAccess resolvedAccess = resolveBankAccess(bankAccess);
		return hasUsableBankAccess(resolvedAccess) && resolvedAccess.getAccessType() == BankAccessType.HBCI;
	}

	public boolean requiresInteractiveCredential(BankAccount bankAccount) {
		BankAccount resolvedAccount = resolveAccount(bankAccount);
		if (!hasUsableBankAccess(resolvedAccount)) {
			return true;
		}
		BankAccess bankAccess = dbController.getBankAccessById(resolvedAccount.getBankAccessId());
		return bankAccess == null || bankAccess.getAccessType() != BankAccessType.ENABLEBANKING;
	}

	private boolean isAvailable(BankAccount bankAccount, Capability capability) {
		return supportedCapabilities(bankAccount).contains(capability.name());
	}

	/**
	 * Returns the names of the banking capabilities that are supported for the given account.
	 * <p>
	 * The capabilities are determined once per account and cached afterwards, because they are
	 * evaluated for every single capability check of the user interface.
	 */
	private Set<String> supportedCapabilities(BankAccount bankAccount) {
		if (bankAccount == null) {
			return Set.of();
		}
		if (bankAccount.getId() <= 0) {
			return computeSupportedCapabilities(bankAccount, accountCodes(bankAccount, bankAccount.getAllowedBusinessCases()));
		}

		BankCapabilityCache.AccountData accountData = BankCapabilityCache.getAccountData(bankAccount.getId(),
				() -> loadAccountData(bankAccount.getId()));
		BankAccount account = accountData != null ? accountData.account() : null;
		if (account == null) {
			// The account has been removed in the meantime, fall back to the given instance.
			return computeSupportedCapabilities(bankAccount, accountCodes(bankAccount, bankAccount.getAllowedBusinessCases()));
		}
		return accountData.supportedCapabilities();
	}

	private Set<String> computeSupportedCapabilities(BankAccount account, Set<String> accountCodes) {
		if (!hasUsableBankAccess(account)) {
			return Set.of();
		}

		Set<String> supported = new HashSet<>();
		for (Capability capability : Capability.values()) {
			if (bankSupports(account.getBankAccessId(), capability) && matches(accountCodes, capability)) {
				supported.add(capability.name());
			}
		}
		return Set.copyOf(supported);
	}

	private Set<String> accountCodes(BankAccount account, List<BusinessCase> allowedBusinessCases) {
		Set<String> codes = new HashSet<>(businessCaseCodes(allowedBusinessCases));
		codes.addAll(collectUpdCodes(account));
		return codes;
	}

	private BankCapabilityCache.AccountData loadAccountData(int accountId) {
		// The bookings of the account are not required here, therefore only the account itself
		// and its business cases are loaded.
		BankAccount account = dbController.getById(BankAccount.class, accountId);
		if (account == null) {
			return new BankCapabilityCache.AccountData(null, Set.of());
		}
		List<BusinessCase> businessCases = dbController.getAllByParent(BusinessCase.class, accountId);
		Set<String> codes = accountCodes(account, businessCases);
		return new BankCapabilityCache.AccountData(account, computeSupportedCapabilities(account, codes));
	}

	/**
	 * Returns the account with the current data of the database.
	 */
	private BankAccount resolveAccount(BankAccount bankAccount) {
		if (bankAccount != null && bankAccount.getId() > 0) {
			BankCapabilityCache.AccountData accountData = BankCapabilityCache.getAccountData(bankAccount.getId(),
					() -> loadAccountData(bankAccount.getId()));
			BankAccount account = accountData != null ? accountData.account() : null;
			if (account != null) {
				return account;
			}
		}
		return bankAccount;
	}

	private BankAccess resolveBankAccess(BankAccess bankAccess) {
		if (bankAccess != null && bankAccess.getId() > 0) {
			BankAccess accessFromDb = dbController.getById(BankAccess.class, bankAccess.getId());
			if (accessFromDb != null) {
				return accessFromDb;
			}
		}
		return bankAccess;
	}

	private boolean hasUsableBankAccess(BankAccount bankAccount) {
		return bankAccount != null
				&& bankAccount.getBankAccessId() != null
				&& bankAccount.getBankAccessId() > 0
				&& !bankAccount.isOfflineAccount()
				&& (bankAccount.getAccountState() == null || bankAccount.getAccountState() == AccountState.ACTIVE);
	}

	private boolean hasUsableBankAccess(BankAccess bankAccess) {
		return bankAccess != null && bankAccess.getId() > 0 && bankAccess.isActive();
	}

	private Set<String> businessCaseCodes(List<BusinessCase> allowedBusinessCases) {
		if (allowedBusinessCases == null || allowedBusinessCases.isEmpty()) {
			return Set.of();
		}

		return allowedBusinessCases.stream()
				.map(BusinessCase::getCaseValue)
				.flatMap(BankingCapabilityService::normalizedCaseStream)
				.collect(Collectors.toSet());
	}

	private Set<String> collectUpdCodes(BankAccount bankAccount) {
		List<Upd> updEntries = updEntries(bankAccount.getBankAccessId());
		if (updEntries.isEmpty()) {
			return Set.of();
		}
		Set<String> matchingUpdGroups = findMatchingUpdGroups(updEntries, bankAccount);
		Predicate<Upd> accountScope = matchingUpdGroups.isEmpty() ? upd -> true : upd -> matchingUpdGroups.contains(getUpdGroup(upd.getPdKey()));

		return updEntries.stream()
				.filter(accountScope)
				.filter(this::isAllowedBusinessCaseParameter)
				.map(ParameterDataBankAccess::getPdValue)
				.flatMap(BankingCapabilityService::normalizedCaseStream)
				.collect(Collectors.toSet());
	}

	private Set<String> findMatchingUpdGroups(List<Upd> updEntries, BankAccount bankAccount) {
		Set<String> updGroups = updEntries.stream()
				.map(entry -> getUpdGroup(entry.getPdKey()))
				.filter(group -> !group.isBlank())
				.collect(Collectors.toSet());
		if (updGroups.isEmpty()) {
			return Set.of();
		}

		int bestScore = updGroups.stream()
				.mapToInt(group -> scoreUpdGroup(updEntries, group, bankAccount))
				.max()
				.orElse(0);
		if (bestScore <= 0) {
			return Set.of();
		}

		return updGroups.stream()
				.filter(group -> scoreUpdGroup(updEntries, group, bankAccount) == bestScore)
				.collect(Collectors.toSet());
	}

	private int scoreUpdGroup(List<Upd> updEntries, String updGroup, BankAccount bankAccount) {
		int score = 0;

		String accountNumber = normalizeToken(bankAccount.getNumber());
		String updNumber = getUpdGroupValue(updEntries, updGroup, "KTVNUMBER", "NUMBER");
		String accountBlz = normalizeToken(bankAccount.getBlz());
		String updBlz = getUpdGroupValue(updEntries, updGroup, "KTVKIKBLZ", "BLZ");
		if (valuesMatch(accountNumber, updNumber) && blzMatches(accountBlz, updBlz)) {
			score += 100;
		}

		String accountSubnumber = normalizeToken(bankAccount.getSubnumber());
		String updSubnumber = getUpdGroupValue(updEntries, updGroup, "KTVSUBNUMBER", "SUBNUMBER");
		if (valuesMatch(accountSubnumber, updSubnumber)) {
			score += 20;
		}

		String accountIban = normalizeToken(bankAccount.getIban());
		String updIban = getUpdGroupValue(updEntries, updGroup, "IBAN", "KTVIBAN");
		if (valuesMatch(accountIban, updIban)) {
			score += 80;
		}

		if (bankAccount.getHbciAccountType() > 0) {
			String updAccountType = getUpdGroupValue(updEntries, updGroup, "ACCTYPE");
			if (Integer.toString(bankAccount.getHbciAccountType()).equals(updAccountType)) {
				score += 40;
			}
		}

		String updKonto = getUpdGroupValue(updEntries, updGroup, "KONTO");
		if (matchesAccountTypeText(bankAccount, updKonto)) {
			score += 10;
		}

		return score;
	}

	private boolean valuesMatch(String left, String right) {
		return !left.isBlank() && !right.isBlank() && left.equals(right);
	}

	private boolean blzMatches(String accountBlz, String updBlz) {
		return accountBlz.isBlank() || updBlz.isBlank() || accountBlz.equals(updBlz);
	}

	private boolean matchesAccountTypeText(BankAccount bankAccount, String updKonto) {
		if (updKonto.isBlank()) {
			return false;
		}

		String accountName = normalizeToken(bankAccount.getAccountName());
		if (!accountName.isBlank() && accountName.contains(updKonto)) {
			return true;
		}

		String accountType = bankAccount.getAccountType() != null ? normalizeToken(bankAccount.getAccountType().getGermanName()) : "";
		if (accountType.isBlank()) {
			return false;
		}
		return accountType.equals(updKonto)
				|| accountType.contains(updKonto)
				|| updKonto.contains(accountType)
				|| ("GIROKONTO".equals(accountType) && "KONTOKORRENT".equals(updKonto));
	}

	private String getUpdGroupValue(List<Upd> updEntries, String updGroup, String... normalizedFieldNames) {
		Set<String> fieldNames = Arrays.stream(normalizedFieldNames).collect(Collectors.toSet());
		return updEntries.stream()
				.filter(entry -> updGroup.equals(getUpdGroup(entry.getPdKey())))
				.filter(entry -> fieldNames.contains(getUpdFieldName(entry.getPdKey())))
				.map(ParameterDataBankAccess::getPdValue)
				.map(BankingCapabilityService::normalizeToken)
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse("");
	}

	private String getUpdGroup(String pdKey) {
		if (pdKey == null) {
			return "";
		}
		int dotIndex = pdKey.indexOf('.');
		if (dotIndex <= 0) {
			return "";
		}
		String group = pdKey.substring(0, dotIndex);
		return group.equals("KInfo") || group.startsWith("KInfo_") ? group : "";
	}

	private String getUpdFieldName(String pdKey) {
		String group = getUpdGroup(pdKey);
		if (group.isBlank()) {
			return "";
		}
		return normalizeToken(pdKey.substring(group.length() + 1));
	}

	private boolean isAllowedBusinessCaseParameter(ParameterDataBankAccess parameterData) {
		String normalizedKey = normalizeToken(parameterData.getPdKey());
		return normalizedKey.contains("ALLOWEDGV") && normalizedKey.endsWith("CODE");
	}

	private boolean bankSupports(Integer bankAccessId, Capability capability) {
		BankCapabilityCache.BankAccessData bankAccessData = bankAccessData(bankAccessId);
		if (bankAccessData == null) {
			return false;
		}

		if (bankAccessData.bpdValueCodes().stream().anyMatch(capability.codes()::contains)) {
			return true;
		}

		for (String bpdKeyName : bankAccessData.bpdKeyNames()) {
			if (capability.bpdKeyTokens().stream().anyMatch(bpdKeyName::contains)) {
				return true;
			}
		}

		return false;
	}

	private BankCapabilityCache.BankAccessData bankAccessData(Integer bankAccessId) {
		if (bankAccessId == null || bankAccessId <= 0) {
			return null;
		}
		return BankCapabilityCache.getBankAccessData(bankAccessId, () -> loadBankAccessData(bankAccessId));
	}

	private List<Upd> updEntries(Integer bankAccessId) {
		BankCapabilityCache.BankAccessData bankAccessData = bankAccessData(bankAccessId);
		return bankAccessData != null ? bankAccessData.updEntries() : List.of();
	}

	private BankCapabilityCache.BankAccessData loadBankAccessData(int bankAccessId) {
		List<Bpd> bpdEntries = dbController.getAllByParent(Bpd.class, bankAccessId);
		List<Upd> updEntries = dbController.getAllByParent(Upd.class, bankAccessId);
		Set<String> bpdValueCodes = new HashSet<>();
		List<String> bpdKeyNames = new ArrayList<>(bpdEntries.size());
		for (Bpd bpd : bpdEntries) {
			bpdValueCodes.addAll(normalizedCaseStream(bpd.getPdValue()).collect(Collectors.toSet()));
			bpdKeyNames.add(normalizeToken(bpd.getPdKey()));
		}
		return new BankCapabilityCache.BankAccessData(updEntries, Set.copyOf(bpdValueCodes), List.copyOf(bpdKeyNames));
	}

	private boolean matches(Set<String> values, Capability capability) {
		return values.stream().anyMatch(capability.codes::contains);
	}

	private static Capability capabilityForTransfer(OrderType orderType) {
		return switch (orderType) {
		case TRANSFER -> Capability.TRANSFER;
		case REALTIME_TRANSFER -> Capability.REALTIME_TRANSFER;
		case URGENT_TRANSFER -> Capability.URGENT_TRANSFER;
		case SCHEDULED_TRANSFER -> Capability.SCHEDULED_TRANSFER;
		case STANDING_ORDER -> Capability.STANDING_ORDER;
		case FOREIGN_TRANSFER -> Capability.FOREIGN_TRANSFER;
		};
	}

	private static Capability capabilityForExistingBankOrder(OrderType orderType, BankOrderOperation operation) {
		if (orderType == OrderType.SCHEDULED_TRANSFER) {
			return switch (operation) {
			case EDIT -> Capability.SCHEDULED_TRANSFER_EDIT;
			case DELETE -> Capability.SCHEDULED_TRANSFER_DELETE;
			case CREATE -> null;
			};
		}
		if (orderType == OrderType.STANDING_ORDER) {
			return switch (operation) {
			case EDIT -> Capability.STANDING_ORDER_EDIT;
			case DELETE -> Capability.STANDING_ORDER_DELETE;
			case CREATE -> null;
			};
		}
		return null;
	}

	private static Stream<String> normalizedCaseStream(String value) {
		if (value == null || value.isBlank()) {
			return Stream.empty();
		}
		return Arrays.stream(value.split("[;,\\s]+")).map(BankingCapabilityService::normalizeToken).filter(token -> !token.isBlank());
	}

	private static String normalizeToken(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}
}
