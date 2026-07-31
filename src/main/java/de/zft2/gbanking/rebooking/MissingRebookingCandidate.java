package de.zft2.gbanking.rebooking;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;

public record MissingRebookingCandidate(Booking sourceBooking, BankAccount sourceAccount, BankAccount targetAccount) {
}
