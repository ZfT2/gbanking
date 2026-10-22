package de.zft2.gbanking.rebooking;

import static de.zft2.gbanking.util.TextValues.hasText;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;

/**
 * Writes rebooking pairs to the database. A rebooking pair consists of an existing booking and a counter
 * booking with the negated amount on the counterpart account. Both bookings reference each other
 * afterwards, so that the movement is handled like a transfer between two accounts.
 */
public final class RebookingLinkWriter {

	private final DBController dbController;

	public RebookingLinkWriter(DBController dbController) {
		this.dbController = Objects.requireNonNull(dbController, "dbController");
	}

	/**
	 * Creates the counter booking of the given booking on the counterpart account and links both bookings
	 * with each other. The given booking is marked as rebooking.
	 *
	 * @param booking            the booking that shall be linked, it must be loaded from the database
	 * @param sourceAccount      the account the booking belongs to
	 * @param counterpartAccount the account that receives the counter booking
	 * @return the created counter booking or {@code null} if the booking cannot be linked
	 */
	public Booking linkWithCounterBooking(Booking booking, BankAccount sourceAccount, BankAccount counterpartAccount) {
		if (!canLinkWithCounterBooking(booking, sourceAccount, counterpartAccount)) {
			return null;
		}

		Booking counterBooking = createCounterBooking(booking, sourceAccount, counterpartAccount);
		Booking savedCounterBooking = dbController.insertOrUpdate(counterBooking);
		if (savedCounterBooking == null || savedCounterBooking.getId() <= 0) {
			return null;
		}

		booking.setBookingType(BookingType.rebookingFromAmount(booking.getAmount()));
		booking.setCrossAccountId(counterpartAccount.getId());
		booking.setCrossBookingId(savedCounterBooking.getId());
		dbController.insertOrUpdate(booking);
		return savedCounterBooking;
	}

	/**
	 * Checks whether a counter booking can be created for the given booking.
	 *
	 * @param booking            the booking that shall be linked
	 * @param sourceAccount      the account the booking belongs to
	 * @param counterpartAccount the account that would receive the counter booking
	 * @return {@code true} if the booking can be linked with a counter booking
	 */
	public static boolean canLinkWithCounterBooking(Booking booking, BankAccount sourceAccount,
			BankAccount counterpartAccount) {
		if (booking == null || sourceAccount == null || counterpartAccount == null || booking.getId() <= 0
				|| booking.getAmount() == null || sourceAccount.getId() <= 0 || counterpartAccount.getId() <= 0
				|| booking.getAccountId() != sourceAccount.getId()
				|| booking.getAccountId() == counterpartAccount.getId()) {
			return false;
		}
		if (booking.getParentBookingId() != null || isPrenotification(booking)
				|| booking.getForeignCurrencyDetails() != null) {
			return false;
		}
		Integer crossBookingId = booking.getCrossBookingId();
		if (crossBookingId != null && crossBookingId > 0) {
			return false;
		}
		Integer crossAccountId = booking.getCrossAccountId();
		return crossAccountId == null || crossAccountId <= 0
				|| crossAccountId == counterpartAccount.getId();
	}

	/**
	 * Creates the counter booking for the given booking. The counter booking is not stored yet.
	 *
	 * @param booking       the booking that receives the counter booking
	 * @param sourceAccount the account the booking belongs to
	 * @param targetAccount the account that receives the counter booking
	 * @return the counter booking with the negated amount
	 */
	public Booking createCounterBooking(Booking booking, BankAccount sourceAccount, BankAccount targetAccount) {
		BigDecimal counterAmount = booking.getAmount().negate();
		Booking counterBooking = new Booking();
		counterBooking.setAccountId(targetAccount.getId());
		counterBooking.setDateBooking(defaultDate(booking.getDateBooking(), booking.getDate(),
				LocalDate.now(ZoneId.systemDefault())));
		counterBooking.setDateValue(defaultDate(booking.getDateValue(), booking.getDate(),
				counterBooking.getDateBooking()));
		counterBooking.setDate(booking.getDate() != null ? booking.getDate() : counterBooking.getDateValue());
		counterBooking.setPurpose(booking.getPurpose());
		counterBooking.setAmount(counterAmount);
		counterBooking.setSource(Source.MANUELL_NEW);
		counterBooking.setBookingType(BookingType.rebookingFromAmount(counterAmount));
		counterBooking.setCrossAccountId(booking.getAccountId());
		counterBooking.setCrossBookingId(booking.getId());
		counterBooking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

		Recipient recipient = dbController.resolveRecipient(createRecipientForAccount(sourceAccount));
		if (recipient != null) {
			counterBooking.setRecipient(recipient);
			counterBooking.setRecipientId(recipient.getId());
		}
		return counterBooking;
	}

	private static Recipient createRecipientForAccount(BankAccount account) {
		Recipient recipient = new Recipient();
		recipient.setName(accountName(account));
		recipient.setIban(account.getIban());
		recipient.setBic(account.getBic());
		recipient.setAccountNumber(account.getNumber());
		recipient.setBlz(account.getBlz());
		recipient.setBank(account.getBankName());
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	private static String accountName(BankAccount account) {
		if (hasText(account.getNamePP())) {
			return account.getNamePP();
		}
		if (hasText(account.getAccountName())) {
			return account.getAccountName();
		}
		if (hasText(account.getIban())) {
			return account.getIban();
		}
		if (hasText(account.getNumber())) {
			return account.getNumber();
		}
		return "Konto " + account.getId();
	}

	private static LocalDate defaultDate(LocalDate value, LocalDate fallback, LocalDate defaultValue) {
		if (value != null) {
			return value;
		}
		return fallback != null ? fallback : defaultValue;
	}

	private static boolean isPrenotification(Booking booking) {
		return booking.getSource() != null && booking.getSource().isPrenotification();
	}
}
