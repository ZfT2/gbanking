package de.zft2.gbanking.db.dao;

import de.zft2.gbanking.db.dao.enu.AccountIdentifierType;

public record BankAccountIdentifier(int id, int accountId, AccountIdentifierType propertyType, String value) {
}
