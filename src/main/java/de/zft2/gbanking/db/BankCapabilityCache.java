package de.zft2.gbanking.db;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Upd;

/**
 * Cache for the database data that decides which banking operations a bank access supports.
 * <p>
 * The data changes only when a bank access is synchronized or an account is updated. Therefore the
 * cached entries are invalidated explicitly on those write operations and when the database
 * connection is closed.
 */
public final class BankCapabilityCache {

	/**
	 * The bank parameter data and the user parameter data of a single bank access.
	 * <p>
	 * The bank parameter data is stored in its normalized form, because it is evaluated for every
	 * single capability check.
	 */
	public record BankAccessData(List<Upd> updEntries, Set<String> bpdValueCodes, List<String> bpdKeyNames) {
	}

	/**
	 * A bank account together with the names of the banking capabilities that are supported for it.
	 */
	public record AccountData(BankAccount account, Set<String> supportedCapabilities) {
	}

	private static final Map<Integer, BankAccessData> BANK_ACCESS_DATA = new ConcurrentHashMap<>();
	private static final Map<Integer, AccountData> ACCOUNT_DATA = new ConcurrentHashMap<>();

	private BankCapabilityCache() {
	}

	/**
	 * Returns the parameter data of the given bank access and loads it on the first access.
	 */
	public static BankAccessData getBankAccessData(int bankAccessId, Supplier<BankAccessData> loader) {
		BankAccessData cached = BANK_ACCESS_DATA.get(bankAccessId);
		if (cached != null) {
			return cached;
		}
		BankAccessData loaded = loader.get();
		if (loaded == null) {
			return null;
		}
		BankAccessData existing = BANK_ACCESS_DATA.putIfAbsent(bankAccessId, loaded);
		return existing != null ? existing : loaded;
	}

	/**
	 * Returns the account data of the given account and loads it on the first access.
	 */
	public static AccountData getAccountData(int accountId, Supplier<AccountData> loader) {
		AccountData cached = ACCOUNT_DATA.get(accountId);
		if (cached != null) {
			return cached;
		}
		AccountData loaded = loader.get();
		if (loaded == null) {
			return null;
		}
		AccountData existing = ACCOUNT_DATA.putIfAbsent(accountId, loaded);
		return existing != null ? existing : loaded;
	}

	/**
	 * Removes the cached parameter data of the given bank access and of all its accounts.
	 */
	public static void invalidateBankAccess(int bankAccessId) {
		BANK_ACCESS_DATA.remove(bankAccessId);
		ACCOUNT_DATA.entrySet().removeIf(entry -> belongsToBankAccess(entry.getValue(), bankAccessId));
	}

	/**
	 * Removes the cached data of the given account.
	 */
	public static void invalidateAccount(int accountId) {
		ACCOUNT_DATA.remove(accountId);
	}

	/**
	 * Removes all cached data, for example when the database connection is closed.
	 */
	public static void clear() {
		BANK_ACCESS_DATA.clear();
		ACCOUNT_DATA.clear();
	}

	private static boolean belongsToBankAccess(AccountData accountData, int bankAccessId) {
		BankAccount account = accountData.account();
		return account != null && account.getBankAccessId() == bankAccessId;
	}
}
