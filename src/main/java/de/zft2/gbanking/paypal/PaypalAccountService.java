package de.zft2.gbanking.paypal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.service.Service;

public class PaypalAccountService implements Service, BaseMessages {

	private final PaypalSoapClient client;
	private final DBController database;

	public PaypalAccountService() {
		this(new PaypalSoapClient(), DBController.getInstance("."));
	}

	PaypalAccountService(PaypalSoapClient client, DBController database) {
		this.client = client;
		this.database = database;
	}

	public boolean initialize(BankAccess bankAccess) throws InterruptedException {
		List<PaypalBalance> balances = client.getBalances(bankAccess.getPaypal().getApiUsername(), bankAccess.getPin(),
				bankAccess.getPaypal().getApiSignature());
		if (balances.isEmpty()) {
			throw new PaypalApiException(getText("ERROR_PAYPAL_NO_BALANCES"), false);
		}

		BankAccess existingAccess = database.getBankAccessByBlzAndUserId(PaypalSupport.BANK_CODE, bankAccess.getPaypal().getUserId());
		BankAccount existingAccount = findExistingAccount(bankAccess, existingAccess);
		applyAccessData(bankAccess, existingAccess);
		bankAccess.setAccounts(List.of(mapAccount(bankAccess, balances.get(0), existingAccount)));
		return true;
	}

	public List<BankAccount> getLinkableAccounts() {
		return database.getAll(BankAccount.class).stream()
				.filter(account -> account.getBankAccessId() == null || account.getBankAccessId() <= 0)
				.filter(account -> PaypalSupport.isPaypal(account))
				.toList();
	}

	public void linkAccount(BankAccess bankAccess, BankAccount existingAccount) {
		if (bankAccess == null || existingAccount == null || existingAccount.getId() <= 0
				|| !PaypalSupport.isPaypal(existingAccount)
				|| existingAccount.getBankAccessId() != null && existingAccount.getBankAccessId() > 0
				|| bankAccess.getAccounts() == null || bankAccess.getAccounts().isEmpty()) {
			return;
		}
		BankAccount retrievedAccount = bankAccess.getAccounts().get(0);
		PaypalBalance primaryBalance = new PaypalBalance(retrievedAccount.getCurrency(), retrievedAccount.getBalance());
		bankAccess.setAccounts(List.of(mapAccount(bankAccess, primaryBalance, existingAccount)));
	}

	private BankAccount findExistingAccount(BankAccess bankAccess, BankAccess existingAccess) {
		if (existingAccess != null) {
			return database.getAllByParent(BankAccount.class, existingAccess.getId()).stream().findFirst().orElse(null);
		}
		List<BankAccount> matchingAccounts = getLinkableAccounts().stream()
				.filter(account -> containsIgnoreCase(account.getAccountName(), bankAccess.getPaypal().getUserId()))
				.toList();
		return matchingAccounts.size() == 1 ? matchingAccounts.get(0) : null;
	}

	private void applyAccessData(BankAccess bankAccess, BankAccess existingAccess) {
		if (existingAccess != null) {
			bankAccess.setId(existingAccess.getId());
		}
		bankAccess.setAccessType(BankAccessType.PAYPAL);
		bankAccess.setBankName(PaypalSupport.DISPLAY_NAME);
		bankAccess.setActive(true);
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
	}

	private BankAccount mapAccount(BankAccess bankAccess, PaypalBalance balance, BankAccount existingAccount) {
		BankAccount account = existingAccount != null ? existingAccount : new BankAccount();
		account.setBankAccessId(bankAccess.getId() > 0 ? bankAccess.getId() : null);
		account.setAccountName(existingAccount != null ? existingAccount.getAccountName()
				: PaypalSupport.DISPLAY_NAME + " - " + bankAccess.getPaypal().getUserId());
		account.setAccountType(AccountType.SPECIAL_ACCOUNT);
		account.setCurrency(balance.currency());
		account.setBankName(PaypalSupport.DISPLAY_NAME);
		account.setBlz(PaypalSupport.BANK_CODE);
		account.setCustomerid(bankAccess.getPaypal().getUserId());
		account.setSource(Source.ONLINE);
		account.setSEPAAccount(false);
		account.setOfflineAccount(false);
		account.setAccountState(AccountState.ACTIVE);
		account.setBalance(balance.amount());
		account.setAllowedBusinessCases(List.of());
		return account;
	}

	private boolean containsIgnoreCase(String value, String searchValue) {
		return value != null && searchValue != null && !searchValue.isBlank()
				&& value.toLowerCase(Locale.ROOT).contains(searchValue.trim().toLowerCase(Locale.ROOT));
	}
}
