package de.zft2.gbanking.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.util.TypeConverter;

public class DaoMapper {

	private DaoMapper() {
	}

	public static BankAccount maptoBankAccountDao(de.zft2.fp3xmlextract.data.BankAccount bankAccountXML) {

		BankAccount bankAccount = new BankAccount();

		AccountType accountType = AccountType.CURRENT_ACCOUNT;

		switch (bankAccountXML.getType()) {
		case "Sparbuch":
			accountType = AccountType.OVERNIGHT_MONEY;
			break;
		case "Festgeldkonto", "Bausparkonto":
			accountType = AccountType.FIXED_DEPOSIT;
			break;
		case "Darlehenskonto", "Kreditkartenkonto":
			accountType = AccountType.CREDIT_ACCOUNT;
			break;
		default:
			break;
		}

		bankAccount.setAccountType(accountType);
		bankAccount.setBic(bankAccountXML.getBic());
		bankAccount.setBlz(bankAccountXML.getBlz());
		bankAccount.setBankName(bankAccountXML.getBankName());
		bankAccount.setCountry("DE");
		bankAccount.setCreditorid(null);
		bankAccount.setCurrency("EUR");
		bankAccount.setCustomerid(null);
		bankAccount.setIban(bankAccountXML.getIban());
		bankAccount.setLimit(null);
		bankAccount.setOwnerName(null);
		bankAccount.setOwnerName2(null);
		bankAccount.setNumber(bankAccountXML.getNumber());
		bankAccount.setSubnumber(null);
		bankAccount.setHbciAccountType(0);
		bankAccount.setSEPAAccount(false);
		bankAccount.setSource(Source.IMPORT_INITIAL);
		bankAccount.setOfflineAccount(true);
		bankAccount.setAccountState(AccountState.ACTIVE);

		bankAccount.setBalance(bankAccountXML.getBalance());

		bankAccount.setAccountName(bankAccountXML.getBezeichnung() != null ? bankAccountXML.getBezeichnung() : bankAccount.getDefaultAccountName());

		bankAccount.setBankAccessId(null);

		return bankAccount;

	}

	public static Collection<Booking> maptoBookingDaoList(String accountName, Collection<de.zft2.fp3xmlextract.data.Booking> bookingXMLList,
			Map<String, Integer> accountIdMapByAccountName, Map<String, Integer> crossAccountIdMapByIdentifier, Source source) {

		List<Booking> bookingDaoList = new ArrayList<>();

		for (de.zft2.fp3xmlextract.data.Booking xmlBooking : bookingXMLList) {

			Booking booking = maptoBookingDao(accountName, xmlBooking, accountIdMapByAccountName, crossAccountIdMapByIdentifier, source);

			bookingDaoList.add(booking);
		}

		return bookingDaoList;
	}

	public static Booking maptoBookingDao(String accountName, de.zft2.fp3xmlextract.data.Booking xmlBooking, Map<String, Integer> accountIdMapByAccountName,
			Map<String, Integer> crossAccountIdMapByIdentifier, Source source) {

		Booking booking = new Booking();

		if (xmlBooking.getAccountNamePP() == null || xmlBooking.getAccountNamePP().isBlank()) {
			xmlBooking.setAccountNamePP(accountName);
		}
		Integer accountId = accountIdMapByAccountName.get(xmlBooking.getAccountNamePP());
		if (accountId == null) {
			throw new GBankingException("No account found for imported booking accountNamePP: " + xmlBooking.getAccountNamePP());
		}
		booking.setAccountId(accountId);
		booking.setSource(source);
		booking.setBookingType(getBookingType(xmlBooking));
		booking.setAmount(xmlBooking.getAmount() != null ? xmlBooking.getAmount().setScale(2, RoundingMode.HALF_UP) : null);
		booking.setDateBooking(TypeConverter.toLocalDateFromDateStrFlexible(xmlBooking.getDateBooking()));
		booking.setDateValue(TypeConverter.toLocalDateFromDateStrFlexible(xmlBooking.getDateValue()));
		booking.setPurpose(xmlBooking.getPurpose());

		booking.setSepaCustomerRef(xmlBooking.getSepaCustomerRef());
		booking.setSepaCreditorId(xmlBooking.getSepaCreditorId());
		booking.setSepaEndToEnd(xmlBooking.getSepaEndToEnd());
		booking.setSepaMandate(xmlBooking.getSepaMandate());
		booking.setSepaPersonId(xmlBooking.getSepaPersonId());
		booking.setSepaPurpose(xmlBooking.getSepaPurpose());
		booking.setSepaTyp(xmlBooking.getSepaTyp());

		if (xmlBooking.getCrossAccountNamePP() != null) {
			booking.setCrossAccountId(accountIdMapByAccountName.get(xmlBooking.getCrossAccountNamePP()));
		} else if (xmlBooking.getCrossAccountIBAN() != null) {
			String crossIban = xmlBooking.getCrossAccountIBAN();
			Integer crossAccountId = null;
			if (crossAccountIdMapByIdentifier != null) {
				crossAccountId = crossAccountIdMapByIdentifier.get(crossIban.replaceFirst("^0+(?!$)", ""));
			}
			if (crossAccountId == null && crossAccountIdMapByIdentifier != null && crossIban.length() >= 15) {
				crossAccountId = crossAccountIdMapByIdentifier.get(crossIban.substring(12).replaceFirst("^0+(?!$)", ""));
			}
			booking.setCrossAccountId(crossAccountId);
		} else {
			booking.setCrossAccountId(null);
		}

		mapRecipient(xmlBooking, booking, source);

		mapCategory(xmlBooking, booking);

		return booking;
	}

	private static BookingType getBookingType(de.zft2.fp3xmlextract.data.Booking xmlBooking) {
		if (xmlBooking.getTyp() != null) {
			return BookingType.forString(xmlBooking.getTyp().name());
		} else {
			return xmlBooking.getAmount().compareTo(BigDecimal.ZERO) > 0 ? BookingType.DEPOSIT : BookingType.REMOVAL;
		}
	}

	public static void setCrossBooking(Booking bookingDao, Integer crossBookingId) {
		if (crossBookingId != null) {
			bookingDao.setCrossBookingId(crossBookingId);
			bookingDao.setBookingType(bookingDao.getAmount().compareTo(BigDecimal.ZERO) < 0 ? BookingType.REBOOKING_OUT : BookingType.REBOOKING_IN);
		}
	}

	private static void mapRecipient(de.zft2.fp3xmlextract.data.Booking xmlBooking, Booking booking, Source source) {
		if (xmlBooking.getCrossReceiverName() != null || xmlBooking.getCrossAccountIBAN() != null || xmlBooking.getCrossAccountNumber() != null) {
			Recipient recipient = new Recipient();
			recipient.setName(xmlBooking.getCrossReceiverName());
			recipient.setIban(xmlBooking.getCrossAccountIBAN());
			recipient.setBic(xmlBooking.getCrossAccountBIC());
			recipient.setBank(xmlBooking.getCrossBankName());
			recipient.setAccountNumber(xmlBooking.getCrossAccountNumber());
			recipient.setBlz(xmlBooking.getCrossBlz());
			recipient.setSource(source);
			booking.setRecipient(recipient);
		}
	}

	private static void mapCategory(de.zft2.fp3xmlextract.data.Booking xmlBooking, Booking booking) {
		if (xmlBooking.getCategory() != null) {
			booking.setCategory(new Category(xmlBooking.getCategory()));
		}
	}
}
