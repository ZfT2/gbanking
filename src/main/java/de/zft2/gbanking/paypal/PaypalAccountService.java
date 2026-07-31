package de.zft2.gbanking.paypal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BankAccessType;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.TanProcedure;

public class PaypalAccountService implements BaseMessagesDb {

	private final PaypalSoapClient client;

	public PaypalAccountService() {
		this(new PaypalSoapClient());
	}

	PaypalAccountService(PaypalSoapClient client) {
		this.client = client;
	}

	public boolean initialize(BankAccess bankAccess) throws InterruptedException {
		List<PaypalBalance> balances = client.getBalances(bankAccess.getPaypalApiUsername(), bankAccess.getPin(),
				bankAccess.getPaypalApiSignature());
		if (balances.isEmpty()) {
			throw new PaypalApiException(getText("ERROR_PAYPAL_NO_BALANCES"), false);
		}

		BankAccess existingAccess = dbController.getBankAccessByBlzAndUserId(PaypalSupport.BANK_CODE, bankAccess.getUserId());
		Map<String, BankAccount> existingAccounts = getExistingAccounts(existingAccess);
		applyAccessData(bankAccess, existingAccess);
		bankAccess.setAccounts(balances.stream().map(balance -> mapAccount(bankAccess, balance, existingAccounts.get(balance.currency()))).toList());
		return true;
	}

	private Map<String, BankAccount> getExistingAccounts(BankAccess existingAccess) {
		if (existingAccess == null) {
			return Map.of();
		}
		Map<String, BankAccount> accountsByCurrency = new HashMap<>();
		for (BankAccount account : dbController.getAllByParent(BankAccount.class, existingAccess.getId())) {
			accountsByCurrency.put(account.getCurrency(), account);
		}
		return accountsByCurrency;
	}

	private void applyAccessData(BankAccess bankAccess, BankAccess existingAccess) {
		if (existingAccess != null) {
			bankAccess.setId(existingAccess.getId());
		}
		bankAccess.setAccessType(BankAccessType.PAYPAL);
		bankAccess.setBlz(PaypalSupport.BANK_CODE);
		bankAccess.setBankName(PaypalSupport.DISPLAY_NAME);
		bankAccess.setCountry("");
		bankAccess.setPort(443);
		bankAccess.setCustomerId(bankAccess.getUserId());
		bankAccess.setTanProcedure(TanProcedure.APP_TAN);
		bankAccess.setAllowedTwostepMechanisms(List.of());
		bankAccess.setBpdVersion("0");
		bankAccess.setUpdVersion("0");
		bankAccess.setFilterType(HbciEncodingFilterType.BASE64);
		bankAccess.setActive(true);
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
	}

	private BankAccount mapAccount(BankAccess bankAccess, PaypalBalance balance, BankAccount existingAccount) {
		BankAccount account = new BankAccount();
		if (existingAccount != null) {
			account.setId(existingAccount.getId());
			account.setCreatedAt(existingAccount.getCreatedAt());
		}
		account.setBankAccessId(bankAccess.getId() > 0 ? bankAccess.getId() : null);
		account.setAccountName(PaypalSupport.DISPLAY_NAME + " - " + balance.currency());
		account.setAccountType(AccountType.SPECIAL_ACCOUNT);
		account.setCurrency(balance.currency());
		account.setBankName(PaypalSupport.DISPLAY_NAME);
		account.setBlz(PaypalSupport.BANK_CODE);
		account.setCustomerid(bankAccess.getUserId());
		account.setSource(Source.ONLINE);
		account.setSEPAAccount(false);
		account.setOfflineAccount(false);
		account.setAccountState(AccountState.ACTIVE);
		account.setBalance(balance.amount());
		account.setAllowedBusinessCases(List.of());
		return account;
	}
}
