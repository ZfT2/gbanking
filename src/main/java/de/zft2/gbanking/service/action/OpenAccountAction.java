package de.zft2.gbanking.service.action;

import de.zft2.gbanking.db.dao.BankAccount;

public record OpenAccountAction<T>(BankAccount account, T details) implements OpenBankingAction {
}
