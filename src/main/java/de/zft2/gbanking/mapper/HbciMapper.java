package de.zft2.gbanking.mapper;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.util.TypeConverter;

public class HbciMapper {

	private static final Logger log = LogManager.getLogger(HbciMapper.class);

	private HbciMapper() {
	}

	public static BankAccount mapKontoToBankAccount(String bankName, Konto konto) {

		BankAccount bankAccount = new BankAccount();

		bankAccount.setBankName(bankName);
		switch (konto.type) {
		case "Termineinlage":
			bankAccount.setAccountType(AccountType.OVERNIGHT_MONEY);
			break;
		default:
			bankAccount.setAccountType(AccountType.CURRENT_ACCOUNT);
		}
		//bankAccount.setAccountType(AccountType.forString(konto.type));
		bankAccount.setAccountName(konto.type + " - " + konto.number);
		bankAccount.setBic(konto.bic);
		bankAccount.setBlz(konto.blz);
		bankAccount.setCountry(konto.country);
		bankAccount.setCreditorid(konto.creditorid);
		bankAccount.setCurrency(konto.curr);
		bankAccount.setCustomerid(konto.customerid);
		bankAccount.setIban(konto.iban);
		bankAccount.setLimit(konto.limit != null ? konto.limit.toString() : null);
		bankAccount.setOwnerName(konto.name);
		bankAccount.setOwnerName2(konto.name2);
		bankAccount.setNumber(konto.number);
		bankAccount.setSubnumber(konto.subnumber);
		bankAccount.setHbciAccountType(Integer.parseInt(konto.acctype));
		bankAccount.setSEPAAccount(konto.isSEPAAccount());
		bankAccount.setSource(Source.ONLINE);

		List<BusinessCase> businessCaseList = new ArrayList<BusinessCase>();
		for (Object gv : konto.allowedGVs) {
			BusinessCase businessCase = new BusinessCase();
			businessCase.setCaseValue(gv.toString());
			businessCaseList.add(businessCase);
		}
		bankAccount.setAllowedBusinessCases(businessCaseList);
		
		bankAccount.setUpdatedAt(LocalDate.now());
		
		//bankAccount.setAccountName(bankAccount.getDefaultAccountName());

		return bankAccount;
	}
	
	public static Booking mapUmsLineToBooking(int accountId, UmsLine umsLine) {

		Booking booking = new Booking();
		
		booking.setAccountId(accountId);
		booking.setDateBooking(TypeConverter.toLocalDateFromDate(umsLine.bdate));
		booking.setDateValue(TypeConverter.toLocalDateFromDate(umsLine.valuta));
		StringBuilder sb = new StringBuilder();
		for( String purposeline : umsLine.usage){
			sb.append(purposeline + "\n");
		}
		booking.setPurpose(sb.toString()); 
		booking.setAmount(umsLine.value.getBigDecimalValue());
		booking.setSource(Source.ONLINE_NEW);

		booking.setSepaCustomerRef(umsLine.customerref);
		booking.setSepaCreditorId(umsLine.other != null ? umsLine.other.creditorid : null);
		booking.setSepaEndToEnd(umsLine.endToEndId);
		booking.setSepaMandate(umsLine.mandateId);
		//		sepaPersonIdText;
		booking.setSepaPurpose(umsLine.purposecode);
		booking.setSepaTyp(null);
		
		booking.setUpdatedAt(LocalDate.now());

		return booking;
	}
	
	public static Recipient mapUmsLineKontoToRecipient(Konto other) {

		if (other == null || (other.iban == null && other.number == null))
			return null;
		
		Recipient recipient = new Recipient();
		log.debug("Konto other: %15s %24s %8s %12s %24s %n", other.name, other.iban, other.bic, other.number, other.blz);
		
		recipient.setName(other.name);
		recipient.setIban(other.iban);
		recipient.setBic(other.bic);
		recipient.setAccountNumber(other.number);
		recipient.setBlz(other.blz);
		recipient.setSource(Source.ONLINE);
		recipient.setUpdatedAt(LocalDate.now());

		return recipient;
	}

}
