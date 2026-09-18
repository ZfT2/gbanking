package de.zft2.gbanking.service.moneytransfer;

import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;

record MoneyTransferStatusResolution(boolean requestSuccessful, MoneyTransferStatus resolvedStatus) {

	static final MoneyTransferStatusResolution NOT_RESOLVED = new MoneyTransferStatusResolution(false, null);

	boolean resolved() {
		return resolvedStatus == MoneyTransferStatus.SENT || resolvedStatus == MoneyTransferStatus.ERROR;
	}
}
