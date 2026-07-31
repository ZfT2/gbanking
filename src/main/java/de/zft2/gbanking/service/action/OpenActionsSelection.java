package de.zft2.gbanking.service.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.dao.BankAccount;

public record OpenActionsSelection(List<BankAccount> accountUpdates, List<OpenTransferAction> transfers,
		List<BankAccount> scheduledTransferInventories, List<BankAccount> standingOrderInventories,
		List<BankAccount> accountStatements, List<BankAccount> accountStatementReceipts) {

	public OpenActionsSelection {
		accountUpdates = immutableList(accountUpdates);
		transfers = immutableList(transfers);
		scheduledTransferInventories = immutableList(scheduledTransferInventories);
		standingOrderInventories = immutableList(standingOrderInventories);
		accountStatements = immutableList(accountStatements);
		accountStatementReceipts = immutableList(accountStatementReceipts);
	}

	public List<BankAccount> accountUpdates() {
		return new ArrayList<>(accountUpdates);
	}

	public List<OpenTransferAction> transfers() {
		return new ArrayList<>(transfers);
	}

	public List<BankAccount> scheduledTransferInventories() {
		return new ArrayList<>(scheduledTransferInventories);
	}

	public List<BankAccount> standingOrderInventories() {
		return new ArrayList<>(standingOrderInventories);
	}

	public List<BankAccount> accountStatements() {
		return new ArrayList<>(accountStatements);
	}

	public List<BankAccount> accountStatementReceipts() {
		return new ArrayList<>(accountStatementReceipts);
	}

	public boolean isEmpty() {
		return accountUpdates.isEmpty()
				&& transfers.isEmpty()
				&& scheduledTransferInventories.isEmpty()
				&& standingOrderInventories.isEmpty()
				&& accountStatements.isEmpty()
				&& accountStatementReceipts.isEmpty();
	}

	public List<BankAccount> accountsRequiringAuthentication() {
		Map<Integer, BankAccount> accountsById = new LinkedHashMap<>();
		addAccounts(accountsById, accountUpdates);
		for (OpenTransferAction transfer : transfers) {
			addAccount(accountsById, transfer.account());
		}
		addAccounts(accountsById, scheduledTransferInventories);
		addAccounts(accountsById, standingOrderInventories);
		addAccounts(accountsById, accountStatements);
		addAccounts(accountsById, accountStatementReceipts);
		return List.copyOf(accountsById.values());
	}

	private static void addAccounts(Map<Integer, BankAccount> accountsById, List<BankAccount> accounts) {
		for (BankAccount account : accounts) {
			addAccount(accountsById, account);
		}
	}

	private static void addAccount(Map<Integer, BankAccount> accountsById, BankAccount account) {
		if (account != null) {
			accountsById.putIfAbsent(account.getId(), account);
		}
	}

	private static <T> List<T> immutableList(List<T> values) {
		return values != null ? List.copyOf(values) : List.of();
	}
}
