package de.zft2.gbanking.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.zft2.gbanking.cache.InstituteLookupCache;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.TypeConverter;

public class HbciMapper {

	private static final Logger log = LogManager.getLogger(HbciMapper.class);

	private HbciMapper() {
	}

	public static BankAccount mapKontoToBankAccount(String bankName, Konto konto) {

		BankAccount bankAccount = new BankAccount();

		bankAccount.setBankName(bankName);

		bankAccount.setAccountType(getAccountType(konto));
		bankAccount.setAccountName(konto.type + " - " + konto.number);
		bankAccount.setBic(konto.bic);
		bankAccount.setBlz(konto.blz);
		bankAccount.setCountry(konto.country);
		bankAccount.setCreditorid(konto.creditorid);
		bankAccount.setBaseCurrency(BookingCurrencyMapper.baseCurrency(konto.curr));
		bankAccount.setCustomerid(konto.customerid);
		bankAccount.setIban(konto.iban);
		bankAccount.setLimit(konto.limit != null ? konto.limit.toString() : null);
		bankAccount.setOwnerName(konto.name);
		bankAccount.setOwnerName2(konto.name2);
		bankAccount.setNumber(konto.number);
		bankAccount.setSubnumber(konto.subnumber);
		bankAccount.setHbciAccountType(parseHbciAccountType(konto.acctype));
		bankAccount.setSEPAAccount(konto.isSEPAAccount());
		bankAccount.setSource(Source.ONLINE);

		List<BusinessCase> businessCaseList = new ArrayList<>();
		for (Object gv : konto.allowedGVs) {
			BusinessCase businessCase = new BusinessCase();
			businessCase.setCaseValue(gv.toString());
			businessCaseList.add(businessCase);
		}
		bankAccount.setAllowedBusinessCases(businessCaseList);
		
		bankAccount.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

		return bankAccount;
	}

	private static int parseHbciAccountType(String accountType) {
		if (accountType == null || accountType.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(accountType.trim());
		} catch (NumberFormatException e) {
			log.warn("Could not parse HBCI account type '{}'. Falling back to 0.", accountType);
			return 0;
		}
	}
	
	public static Booking mapUmsLineToBooking(int accountId, UmsLine umsLine) {
		return mapUmsLineToBooking(accountId, umsLine, Currency.EUR, Source.ONLINE_NEW);
	}

	public static Booking mapUmsLineToBooking(int accountId, UmsLine umsLine, Source source) {
		return mapUmsLineToBooking(accountId, umsLine, Currency.EUR, source);
	}

	public static Booking mapUmsLineToBooking(int accountId, UmsLine umsLine, Currency baseCurrency, Source source) {

		Booking booking = new Booking();
		
		booking.setAccountId(accountId);
		booking.setDateBooking(TypeConverter.toLocalDateFromDate(umsLine.bdate));
		booking.setDateValue(TypeConverter.toLocalDateFromDate(umsLine.valuta));
		StringBuilder sb = new StringBuilder();
		if (umsLine.usage != null && !umsLine.usage.isEmpty()) {
			for (String purposeline : umsLine.usage) {
				sb.append(purposeline);
				sb.append("\n");
			}
			booking.setPurpose(sb.toString());
		} else {
			booking.setPurpose(umsLine.text);
		}
		BigDecimal amount = toBigDecimal(umsLine.value);
		BookingCurrencyMapper.mapAmounts(booking, amount, currency(umsLine.value), baseCurrency,
				toBigDecimal(umsLine.orig_value), currency(umsLine.orig_value), null);
		booking.setFee(BookingCurrencyMapper.createFee(toBigDecimal(umsLine.charge_value),
				currency(umsLine.charge_value), baseCurrency));
		booking.setBookingType(resolveBookingType(booking));
		booking.setSource(source);

		booking.setSepaDetails(mapSepaDetails(umsLine));
		booking.setAdditionalDetails(mapAdditionalFields(umsLine));
		
		booking.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

		return booking;
	}

	private static BookingSepaDetails mapSepaDetails(UmsLine umsLine) {
		BookingSepaDetails details = new BookingSepaDetails();
		details.setCustomerRef(umsLine.customerref);
		details.setCreditorId(umsLine.other != null ? umsLine.other.creditorid : null);
		details.setEndToEnd(umsLine.endToEndId);
		details.setMandate(umsLine.mandateId);
		details.setPurpose(umsLine.purposecode);
		return details;
	}

	private static BookingAdditionalDetails mapAdditionalFields(UmsLine umsLine) {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref(umsLine.instref);
		details.setGvcode(umsLine.gvcode);
		details.setText(umsLine.text);
		details.setPrimanota(umsLine.primanota);
		details.setKey(umsLine.addkey);
		details.setStorno(umsLine.isStorno);
		details.setRawData(umsLine.additional);
		details.setSepa(umsLine.isSepa);
		details.setCamt(umsLine.isCamt);
		details.setBankSaldo(umsLine.saldo != null ? toBigDecimal(umsLine.saldo.value) : null);
		return details;
	}

	private static BigDecimal toBigDecimal(Value value) {
		return value != null ? value.getBigDecimalValue() : null;
	}

	private static String currency(Value value) {
		return value != null ? value.getCurr() : null;
	}

	private static BookingType resolveBookingType(Booking booking) {
		if (booking.getAmount() == null) {
			return null;
		}
		return booking.getAmount().signum() < 0 ? BookingType.REMOVAL : BookingType.DEPOSIT;
	}
	
	public static Recipient mapUmsLineKontoToRecipient(Konto other) {

		if (other == null || (other.name == null && other.iban == null && other.number == null))
			return null;
		
		Recipient recipient = new Recipient();
		log.debug("Konto other: %15s %24s %8s %12s %24s %n", other.name, other.iban, other.bic, other.number, other.blz);
		
		recipient.setName(other.name);
		recipient.setIban(other.iban);
		recipient.setBic(other.bic);
		recipient.setAccountNumber(other.number);
		recipient.setBlz(other.blz);
		String bankName = InstituteLookupCache.findBankNameForBankData(other.bic, other.blz).orElse(null);
		if (bankName != null) {
			recipient.setBank(bankName);
		}
		recipient.setSource(Source.ONLINE);
		recipient.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));

		return recipient;
	}

	/**
	 * Guesses the AccountType from a hbci4java Konto-object.
	 * 
	 * @param konto The hbci4java returned Konto-object
	 * @return AccountType enum
	 */
	public static AccountType getAccountType(Konto konto) {
		if (konto == null) {
			return AccountType.UNKNOWN_ACCOUNT;
		}

		// Step 1: prio acctype-Field
		String typeCandidate = (konto.acctype != null) ? konto.acctype.trim() : "";

		// Step 2: Fallback on raw type-field, if acctype is empty
		if (typeCandidate.isEmpty() && konto.type != null) {
			typeCandidate = konto.type.trim();
		}

		// Step 3: Normalize (Codes resolution & handle free texts)
		switch (typeCandidate.toLowerCase()) {
		// Resolve numeric DK-Codes
		case "0001", "girokonto", "online-konto", "kontokorrent":
			return AccountType.CURRENT_ACCOUNT;

		case "0002", "sparkonto", "sparbuch":
			return AccountType.SAVINGS_ACCOUNT;

		case "0003", "0004", "festgeld":
			return AccountType.FIXED_DEPOSIT;

		case "termgeld", "tagesgeld":
			return AccountType.OVERNIGHT_MONEY;

		case "0005", "depot", "wertpapierdepot":
			return AccountType.DEPOT;

		case "0006", "darlehen", "kredit", "darlehenskonto":
			return AccountType.CREDIT_ACCOUNT;

		case "0007", "kreditkarte", "credit card":
			return AccountType.CREDIT_CARD;

		case "0008", "bauspar", "bausparkonto":
			return AccountType.SAVEINGS_HOME;

		default:
			// Fallback for bank-specific own names (e.g. "Top-Giro", "KlassikKonto")
			if (typeCandidate.toLowerCase().contains("giro")) {
				return AccountType.CURRENT_ACCOUNT;
			} else if (typeCandidate.toLowerCase().contains("spar")) {
				return AccountType.SAVINGS_ACCOUNT;
			}
			return AccountType.UNKNOWN_ACCOUNT;
		}
	}
}
