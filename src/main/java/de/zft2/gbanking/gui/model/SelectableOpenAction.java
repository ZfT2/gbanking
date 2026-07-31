package de.zft2.gbanking.gui.model;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.service.action.OpenBankingAction;

public class SelectableOpenAction<T extends OpenBankingAction> {

	private final T value;
	private boolean selected;

	public SelectableOpenAction(T value) {
		this.value = value;
	}

	public BankAccount getAccount() {
		return value.account();
	}

	public T getValue() {
		return value;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}
}
