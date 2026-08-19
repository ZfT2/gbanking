package de.zft2.gbanking.service;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;

public abstract class AbstractDbService implements Service, BaseMessages {

	protected final DBController dbController = DBController.getInstance(".");

	protected final String getNullableBankAccountId(BankAccount bankAccount) {
		return bankAccount != null ? String.valueOf(bankAccount.getId()) : "(Account is null)";
	}
}
