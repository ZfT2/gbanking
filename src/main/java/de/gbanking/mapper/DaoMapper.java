package de.gbanking.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.fp32xmlextract.data.Booking.SepaTyp;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.util.TypeConverter;

public class DaoMapper {

	private DaoMapper() {
	}

	public static BankAccount maptoBankAccountDao(de.fp32xmlextract.data.BankAccount bankAccountXML) {

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

		bankAccount.setAccountName(
				bankAccountXML.getBezeichnung() != null ? bankAccountXML.getBezeichnung() : bankAccount.getDefaultAccountName());

		bankAccount.setBankAccessId(null);

		return bankAccount;

	}

	public static Collection<Booking> maptoBookingDaoList(String accountName, Collection<de.fp32xmlextract.data.Booking> bookingXMLList,
			Map<String, Integer> accountIdMapByAccountName, Map<String, Integer> crossAccountIdMapByIdentifier, Source source) {

		List<Booking> bookingDaoList = new ArrayList<>();

		for (de.fp32xmlextract.data.Booking xmlBooking : bookingXMLList) {

			Booking booking = maptoBookingDao(accountName, xmlBooking, accountIdMapByAccountName, crossAccountIdMapByIdentifier, source);

			bookingDaoList.add(booking);
		}

		return bookingDaoList;
	}
	
	public static Booking maptoBookingDao(String accountName, de.fp32xmlextract.data.Booking xmlBooking,
			Map<String, Integer> accountIdMapByAccountName, Map<String, Integer> crossAccountIdMapByIdentifier, Source source) {

			Booking booking = new Booking();

			if (xmlBooking.getAccountNamePP() == null) {
				xmlBooking.setAccountNamePP(accountName);
			}
			booking.setAccountId(accountIdMapByAccountName.get(xmlBooking.getAccountNamePP()));
			booking.setSource(source);
			booking.setBookingType(xmlBooking.getTyp() != null ? BookingType.forString(xmlBooking.getTyp().name()) : null);
			booking.setAmount(xmlBooking.getAmount());
			//booking.setDate(TypeConverter.toCalendarFromDateStr(xmlBooking.getDate()));
			booking.setDateBooking(TypeConverter.toCalendarFromDateStrShort(xmlBooking.getDateBooking()));
			booking.setDateValue(TypeConverter.toCalendarFromDateStrShort(xmlBooking.getDateValue()));
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
				Integer crossAccountId = crossAccountIdMapByIdentifier.get(crossIban.replaceFirst("^0+(?!$)", ""));
				if (crossAccountId == null && crossIban.length() >= 15) {
					crossAccountId = crossAccountIdMapByIdentifier.get(crossIban.substring(12).replaceFirst("^0+(?!$)", ""));
				}
				booking.setCrossAccountId(crossAccountId);
			} else {
				booking.setCrossAccountId(null);
			}

//			setCrossBooking(booking, crossBookingMap.get(xmlBooking));
			
			mapRecipient(xmlBooking, booking, source);

			mapCategory(xmlBooking, booking);
			
			return booking;
		}

		public static void setCrossBooking(Booking bookingDao, Integer crossBookingId) {
			if (crossBookingId != null) {
				bookingDao.setCrossBookingId(crossBookingId);
				bookingDao.setBookingType(bookingDao.getAmount().compareTo(BigDecimal.ZERO) < 0 ? BookingType.REBOOKING_OUT : BookingType.REBOOKING_IN);
			}
		}

		private static void mapRecipient(de.fp32xmlextract.data.Booking xmlBooking, Booking booking, Source source) {
		if (xmlBooking.getCrossReceiverName() != null || xmlBooking.getCrossAccountIBAN() != null
				|| xmlBooking.getCrossAccountNumber() != null) {
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

	private static void mapCategory(de.fp32xmlextract.data.Booking xmlBooking, Booking booking) {
		if (xmlBooking.getCategory() != null) {
			booking.setCategory(new Category(xmlBooking.getCategory()));
		}
	}
}
