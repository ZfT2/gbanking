package de.zft2.gbanking.service.action;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;

public record OpenTransferAction(BankAccount account, MoneyTransfer moneyTransfer) implements OpenBankingAction {
}
