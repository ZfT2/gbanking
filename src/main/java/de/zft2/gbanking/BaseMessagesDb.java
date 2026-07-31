package de.zft2.gbanking;

import org.kapott.hbci.manager.HBCIVersion;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;

public interface BaseMessagesDb extends BaseMessages {

	public static DBController dbController = DBController.getInstance(".");

	static final HBCIVersion HBCI_VERSION = HBCIVersion.HBCI_300;

	default DBController getDBController() {
		return dbController;
	}

	static HBCIVersion getVersion() {
		return HBCI_VERSION;
	}

	default String getNullableBankAccountId(BankAccount bankAccount) {
		return (bankAccount != null ? String.valueOf(bankAccount.getId()) : "(Account is null)");
	}

}
