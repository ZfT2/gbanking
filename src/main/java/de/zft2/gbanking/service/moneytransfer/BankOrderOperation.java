package de.zft2.gbanking.service.moneytransfer;

import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;

public enum BankOrderOperation {

	CREATE,
	EDIT,
	DELETE;

	public static BankOrderOperation forTransfer(MoneyTransfer moneyTransfer) {
		if (moneyTransfer != null && moneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.DELETE_PENDING) {
			return DELETE;
		}
		if (moneyTransfer != null && moneyTransfer.getMoneytransferStatus() == MoneyTransferStatus.CHANGED) {
			return EDIT;
		}
		return CREATE;
	}
}
