package de.zft2.gbanking.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.zft2.core.dto.Account;
import de.zft2.core.dto.BookingDetails;
import de.zft2.core.dto.Counterpart;
import de.zft2.core.dto.Booking.Typ;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.BookingCreditCardDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.file.imp.ImportedAccountResolver;
import de.zft2.gbanking.file.imp.dto.ImportBankAccount;
import de.zft2.gbanking.file.imp.dto.ImportBooking;

public class ImportDaoMapper {

	private ImportDaoMapper() {
	}

	public static BankAccount maptoBankAccountDao(Account<?> importAccount, Source source) {

		BankAccount bankAccount = new BankAccount();

		bankAccount.setAccountType(getAccountType(importAccount));
		bankAccount.setBic(importAccount.getBic());
		bankAccount.setBlz(importAccount.getBlz());
		bankAccount.setBankName(importAccount.getBankName());
		bankAccount.setCountry(getCountry(importAccount));
		bankAccount.setCreditorid(null);
		bankAccount.setCurrency(getCurrency(importAccount));
		bankAccount.setCustomerid(null);
		bankAccount.setIban(importAccount.getIban());
		bankAccount.setLimit(null);
		bankAccount.setOwnerName(getOwnerName(importAccount));
		bankAccount.setOwnerName2(getOwnerName2(importAccount));
		bankAccount.setNumber(importAccount.getNumber());
		bankAccount.setSubnumber(getSubnumber(importAccount));
		bankAccount.setHbciAccountType(0);
		bankAccount.setSEPAAccount(false);
		bankAccount.setSource(source != null ? source : Source.IMPORT_INITIAL);
		bankAccount.setOfflineAccount(isOfflineAccount(importAccount));
		bankAccount.setAccountState(getAccountState(importAccount));
		bankAccount.setBalance(importAccount.getBalance());
		bankAccount.setAccountName(importAccount.getAccountName() != null ? importAccount.getAccountName() : bankAccount.getDefaultAccountName());
		bankAccount.setBankAccessId(null);

		if (importAccount instanceof ImportBankAccount) {
			bankAccount.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		}

		return bankAccount;
	}

	private static AccountType getAccountType(Account<?> importAccount) {
		if (importAccount instanceof ImportBankAccount account && account.getAccountType() != null) {
			return account.getAccountType();
		}
		if (importAccount instanceof de.zft2.fp3xmlextract.data.Fp3XmlBankAccount account) {
			String accountType = account.getType();
			if (accountType == null) {
				return AccountType.CURRENT_ACCOUNT;
			}
			return switch (accountType) {
			case "Sparbuch" -> AccountType.OVERNIGHT_MONEY;
			case "Festgeldkonto", "Bausparkonto" -> AccountType.FIXED_DEPOSIT;
			case "Darlehenskonto", "Kreditkartenkonto" -> AccountType.CREDIT_ACCOUNT;
			default -> AccountType.CURRENT_ACCOUNT;
			};
		}
		return AccountType.CURRENT_ACCOUNT;
	}

	private static String getCountry(Account<?> importAccount) {
		if (importAccount instanceof ImportBankAccount account && account.getCountry() != null) {
			return account.getCountry();
		}
		return "DE";
	}

	private static String getCurrency(Account<?> importAccount) {
		if (importAccount instanceof ImportBankAccount account && account.getCurrency() != null) {
			return account.getCurrency();
		}
		return "EUR";
	}

	private static String getOwnerName(Account<?> importAccount) {
		return importAccount instanceof ImportBankAccount account ? account.getOwnerName() : null;
	}

	private static String getOwnerName2(Account<?> importAccount) {
		return importAccount instanceof ImportBankAccount account ? account.getOwnerName2() : null;
	}

	private static String getSubnumber(Account<?> importAccount) {
		return importAccount instanceof ImportBankAccount account ? account.getSubnumber() : null;
	}

	private static boolean isOfflineAccount(Account<?> importAccount) {
		return !(importAccount instanceof ImportBankAccount account) || account.isOfflineAccount();
	}

	private static AccountState getAccountState(Account<?> importAccount) {
		if (importAccount instanceof ImportBankAccount account && account.getAccountState() != null) {
			return account.getAccountState();
		}
		return AccountState.ACTIVE;
	}

	public static Collection<Booking> maptoBookingDaoList(String accountName, Collection<de.zft2.fp3xmlextract.data.Fp3XmlBooking> bookingXMLList,
			Map<String, Integer> accountIdMapByAccountName, Map<String, Integer> crossAccountIdMapByIdentifier, Source source) {

		List<Booking> bookingDaoList = new ArrayList<>();

		for (de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking : bookingXMLList) {

			int accountId = ImportedAccountResolver.resolveAccountId(accountName, xmlBooking, accountIdMapByAccountName);
			Integer crossAccountId = ImportedAccountResolver.resolveCrossAccountId(xmlBooking, accountId, accountIdMapByAccountName,
					crossAccountIdMapByIdentifier);
			Booking booking = maptoBookingDao(xmlBooking, accountId, crossAccountId, source);

			bookingDaoList.add(booking);
		}

		return bookingDaoList;
	}

	public static Booking maptoBookingDao(de.zft2.core.dto.Booking importBooking, int accountId, Integer crossAccountId, Source source) {

		Booking booking = new Booking();
		Source bookingSource = source != null ? source : Source.IMPORT;

		booking.setAccountId(accountId);
		booking.setSource(bookingSource);
		booking.setBookingType(getBookingType(importBooking));
		booking.setAmount(importBooking.getAmount() != null ? importBooking.getAmount().setScale(2, RoundingMode.HALF_UP) : null);
		booking.setDateBooking(getDateBooking(importBooking));
		booking.setDateValue(getDateValue(importBooking));
		booking.setPurpose(importBooking.getPurpose());
		booking.setCrossAccountId(crossAccountId);

		mapAdditionalFields(importBooking, booking);
		mapRecipient(importBooking, booking);
		mapCategory(importBooking, booking);

		return booking;
	}

	private static BookingType getBookingType(de.zft2.core.dto.Booking importBooking) {
		Typ typ = importBooking.getTyp();
		if (typ != null) {
			return Booking.toBookingType(typ);
		}
		if (importBooking instanceof ImportBooking booking) {
			if (booking.getBookingType() != null) {
				return booking.getBookingType();
			}
			return getBookingTypeByAmount(booking.getAmount(), true);
		}
		if (importBooking instanceof Booking booking && booking.getBookingType() != null) {
			return booking.getBookingType();
		}
		return getBookingTypeByAmount(importBooking.getAmount(), false);
	}

	private static BookingType getBookingTypeByAmount(BigDecimal amount, boolean includeZeroAsDeposit) {
		if (amount == null) {
			return BookingType.DEPOSIT;
		}
		boolean isDeposit = includeZeroAsDeposit ? amount.compareTo(BigDecimal.ZERO) >= 0 : amount.compareTo(BigDecimal.ZERO) > 0;
		return isDeposit ? BookingType.DEPOSIT : BookingType.REMOVAL;
	}

	private static LocalDate getDateBooking(de.zft2.core.dto.Booking importBooking) {
		if (importBooking instanceof BookingDetails bookingDetails && bookingDetails.getDateBooking() != null) {
			return bookingDetails.getDateBooking();
		}
		return importBooking.getDate();
	}

	private static LocalDate getDateValue(de.zft2.core.dto.Booking importBooking) {
		return importBooking instanceof BookingDetails bookingDetails ? bookingDetails.getDateValue() : null;
	}

	private static void mapAdditionalFields(de.zft2.core.dto.Booking importBooking, Booking booking) {
		BookingSepaDetails sepaDetails = mapSepaDetails(importBooking);

		if (importBooking instanceof ImportBooking importBookingDto) {
			sepaDetails.setType(importBookingDto.getSepaType());
			mapAdditionalFields(importBookingDto, booking);
		} else if (importBooking instanceof de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking) {
			sepaDetails.setType(SepaType.forSepaTyp(xmlBooking.getSepaTyp()));
		}

		booking.setSepaDetails(sepaDetails);
	}

	private static BookingSepaDetails mapSepaDetails(de.zft2.core.dto.Booking importBooking) {
		BookingSepaDetails sepaDetails = new BookingSepaDetails();
		if (importBooking instanceof BookingDetails bookingDetails) {
			sepaDetails.setCustomerRef(bookingDetails.getSepaCustomerRef());
			sepaDetails.setCreditorId(bookingDetails.getSepaCreditorId());
			sepaDetails.setEndToEnd(bookingDetails.getSepaEndToEnd());
			sepaDetails.setMandate(bookingDetails.getSepaMandate());
			sepaDetails.setPersonId(bookingDetails.getSepaPersonId());
			sepaDetails.setPurpose(bookingDetails.getSepaPurpose());
		}
		return sepaDetails;
	}

	private static void mapAdditionalFields(ImportBooking importBooking, Booking booking) {
		booking.setCurrency(importBooking.getCurrency());
		booking.setBalance(importBooking.getBalance());
		booking.setAdditionalDetails(mapAdditionalDetails(importBooking));
		booking.setCreditCardDetails(mapCreditCardDetails(importBooking));
		booking.setUpdatedAt(importBooking.getUpdatedAt() != null ? importBooking.getUpdatedAt() : LocalDate.now(ZoneId.systemDefault()));
	}

	private static BookingAdditionalDetails mapAdditionalDetails(ImportBooking importBooking) {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setBankSaldo(importBooking.getAddBankSaldo());
		details.setInstref(importBooking.getAddInstref());
		details.setGvcode(importBooking.getAddGvcode());
		details.setText(importBooking.getAddText());
		details.setPrimanota(importBooking.getAddPrimanota());
		details.setKey(importBooking.getAddKey());
		details.setStorno(importBooking.getAddIsStorno());
		details.setOrigValue(importBooking.getAddOrigValue());
		details.setChargeValue(importBooking.getAddChargeValue());
		details.setRawData(importBooking.getAddRawData());
		details.setSepa(importBooking.getAddIsSepa());
		details.setCamt(importBooking.getAddIsCamt());
		return details;
	}

	private static BookingCreditCardDetails mapCreditCardDetails(ImportBooking importBooking) {
		BookingCreditCardDetails details = new BookingCreditCardDetails();
		details.setTransactionDate(importBooking.getCreditcardTransactionDate());
		details.setType(importBooking.getCreditcardType());
		details.setCurrencyAmount(importBooking.getCreditcardCurrencyAmount());
		details.setCurrencyRate(importBooking.getCreditcardCurrencyRate());
		details.setCurrency(importBooking.getCreditcardCurrency());
		details.setMerchantArea(importBooking.getCreditcardMerchantArea());
		details.setMerchantCategory(importBooking.getCreditcardMerchantCategory());
		return details;
	}

	public static void setCrossBooking(Booking bookingDao, Integer crossBookingId) {
		if (crossBookingId != null) {
			bookingDao.setCrossBookingId(crossBookingId);
			if (bookingDao.getBookingType() != BookingType.CANCEL) {
				bookingDao.setBookingType(bookingDao.getAmount().compareTo(BigDecimal.ZERO) < 0 ? BookingType.REBOOKING_OUT : BookingType.REBOOKING_IN);
			}
		}
	}

	private static void mapRecipient(de.zft2.core.dto.Booking importBooking, Booking booking) {
		Counterpart counterpart = importBooking.getCounterpart();
		if (Counterpart.hasData(counterpart)) {
			booking.setCounterpart(counterpart);
		}
		if (importBooking instanceof ImportBooking bookingDto && bookingDto.getRecipientId() != null) {
			booking.setRecipientId(bookingDto.getRecipientId());
		}
	}

	private static void mapCategory(de.zft2.core.dto.Booking importBooking, Booking booking) {
		if (importBooking instanceof ImportBooking bookingDto) {
			mapCategory(bookingDto.getCategory(), booking);
		} else if (importBooking instanceof de.zft2.fp3xmlextract.data.Fp3XmlBooking xmlBooking) {
			mapCategory(xmlBooking.getCategory(), booking);
		}
	}

	private static void mapCategory(String category, Booking booking) {
		if (category != null) {
			booking.setCategory(new Category(category));
		}
	}
}
