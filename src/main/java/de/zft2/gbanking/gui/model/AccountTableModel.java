package de.zft2.gbanking.gui.model;

import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class AccountTableModel {

	private final ObservableList<BankAccount> accounts;

	public AccountTableModel(List<BankAccount> accountList) {
		this.accounts = FXCollections.observableArrayList(accountList);
	}

	public ObservableList<BankAccount> getAccounts() {
		return accounts;
	}

	public List<BankAccount> getCheckedAccounts() {

		return accounts.stream().filter(BankAccount::isSelected).toList();
	}

	public BankAccount getSelectedAccount(int rowIndex) {
		return accounts.get(rowIndex);
	}

	public int getSelectedAccountId(int rowIndex) {
		return accounts.get(rowIndex).getId();
	}

	public void addAccount(BankAccount account) {
		accounts.add(account);
	}

	public void removeAccount(BankAccount account) {
		accounts.remove(account);
	}

	public void clear() {
		accounts.clear();
	}
}
