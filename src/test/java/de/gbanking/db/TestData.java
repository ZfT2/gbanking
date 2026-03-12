package de.gbanking.db;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Category;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.db.dao.enu.TanProcedure;

public class TestData {
	
	public static BankAccess createSampleBankAccess(String blz) {
		BankAccess ba = new BankAccess();
		ba.setBankName("TestBank-" + blz);
		ba.setCountry("DE");
		ba.setBlz(blz);
		ba.setHbciURL("https://hbci.test/" + blz);
		ba.setPort(3000);
		ba.setUserId("user-" + blz);
		ba.setCustomerId("cust-" + blz);
		ba.setSysId("sys-" + blz);
		ba.setTanProcedure(TanProcedure.APP_SECUREGO_PLUS); // beliebiger enum
		ba.setAllowedTwostepMechanisms(Arrays.asList("SMS", "APP"));
		ba.setHbciVersion("300");
		ba.setBpdVersion("1");
		ba.setUpdVersion("1");
		ba.setFilterType(HbciEncodingFilterType.NONE);
		ba.setActive(true);
		Calendar cal = Calendar.getInstance();
		ba.setUpdatedAt(cal);
		return ba;
	}

	public static BankAccount createSampleAccount(Integer bankAccessId) {
		BankAccount acc = new BankAccount();
		acc.setBankAccessId(bankAccessId);
		acc.setAccountName("Giro-" + UUID.randomUUID().toString().substring(0, 6));
		acc.setCurrency("EUR");
		acc.setAccountType(AccountType.CURRENT_ACCOUNT);
		acc.setSource(Source.ONLINE); // from Dao base class
		acc.setIban("DE" + (100000000 + new Random().nextInt(900000000)));
		acc.setBic("TESTBIC");
		acc.setNumber(String.valueOf(10000 + new Random().nextInt(90000)));
		acc.setSubnumber("00");
		acc.setBankName("TestBank");
		acc.setBlz("00000000");
		acc.setHbciAccountType(0);
		acc.setLimit("1000");
		acc.setCustomerid("CUST-1");
		acc.setOwnerName("Max Mustermann");
		acc.setOwnerName2("Erika Mustermann");
		acc.setCountry("DE");
		acc.setCreditorid("CR-1");
		acc.setSEPAAccount(true);
		acc.setAccountState(AccountState.ACTIVE);
		acc.setUpdatedAt(Calendar.getInstance());
		return acc;
	}
	
	public static BankAccount createAccountWithParams(Integer bankAccessId, String accountName, Source source, AccountType accountType, AccountState accountState, String owner, String iban) {
		BankAccount acc = new BankAccount();
		acc.setBankAccessId(bankAccessId);
		acc.setAccountName(accountName);
		acc.setCurrency("EUR");
		acc.setAccountType(accountType);
		acc.setSource(source);
		acc.setIban(iban);
		acc.setOwnerName(owner);
		acc.setCountry("DE");
		acc.setSEPAAccount(true);
		acc.setAccountState(accountState);
		acc.setUpdatedAt(Calendar.getInstance());
		return acc;
	}
	
	public static Booking createSampleBooking(Integer bankAccountId) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccountId);
		booking.setDateBooking(Calendar.getInstance());
		booking.setDateValue(Calendar.getInstance());
		booking.setPurpose("Miete");
		booking.setAmount(new BigDecimal("1200.00"));
		booking.setCurrency("EUR");
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.ONLINE);
		return booking;
	}
	
	public static Booking createSampleBookingWithRecipient(Integer bankAccountId, Integer recipientId) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccountId);
		booking.setDateBooking(Calendar.getInstance());
		booking.setDateValue(Calendar.getInstance());
		booking.setPurpose("Bareinzahlung");
		booking.setAmount(new BigDecimal("50.00"));
		booking.setCurrency("EUR");
		booking.setBookingType(BookingType.DEPOSIT);
		booking.setSource(Source.ONLINE);
		booking.setRecipientId(recipientId);
		return booking;
	}
	
	public static Booking createSampleBooking2(Integer bankAccountId) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccountId);
		booking.setDateBooking(Calendar.getInstance());
		booking.setDateValue(Calendar.getInstance());
		booking.setPurpose("Kreditrate");
		booking.setAmount(new BigDecimal("400.00"));
		booking.setCurrency("EUR");
		booking.setBookingType(BookingType.REMOVAL);
		booking.setSource(Source.ONLINE);
		return booking;
	}
	
	public static Booking createBookingWithParams(Integer bankAccountId, Integer recipientId, String purpose, double amount, BookingType bookingType, Source source) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccountId);
		booking.setRecipientId(recipientId);
		booking.setDateBooking(Calendar.getInstance());
		booking.setDateValue(Calendar.getInstance());
		booking.setPurpose(purpose);
		booking.setAmount(new BigDecimal(amount));
		booking.setCurrency("EUR");
		booking.setBookingType(bookingType);
		booking.setSource(Source.ONLINE);
		return booking;
	}
	
	public static Category createSampleCategory(String categoryName) {
		Category cg = new Category(categoryName);
		Calendar cal = Calendar.getInstance();
		cg.setUpdatedAt(cal);
		return cg;
	}
	
	public static Properties buildBPD() {
		Properties bpd = new Properties();
		bpd.setProperty("Params_31.Template2DPar.ParTemplate2D.dummy", "0;1;110000");
		bpd.setProperty("Params_52.Template2Par.SegHead.code", "HIBMLS");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_3", "15000");
		bpd.setProperty("Params_50.Template2DPar.SegHead.code", "HIIPSS");
		bpd.setProperty("Params_62.VoPCheckPar1.ParVoPCheck.segcode", "HKCCS");
		bpd.setProperty("Params_2.KUmsZeitPar4.ParKUmsZeit.canmaxentries", "J");
		bpd.setProperty("Params_59.Template2DPar.maxnum", "1");
		bpd.setProperty("Params_70.PinTanPar2.ParPinTan.PinTanGV_46.needtan", "J");
		bpd.setProperty("Params_72.Template2DPar.SegHead.ref", "4");
		bpd.setProperty("Params_67.Template2DPar.SegHead.version", "1");
		bpd.setProperty("Params_65.Template2DPar.ParTemplate2D.dummy", "0");
		bpd.setProperty("Params_39.Template2Par.SegHead.version", "2");
		bpd.setProperty("Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_3.needtanmedia", "0");
		bpd.setProperty("Params_27.TermSammelUebSEPAPar1.ParTermSammelUebSEPA.maxnum", "999");
		bpd.setProperty("Params_3.KUmsZeitPar5.SegHead.version", "5");
		bpd.setProperty("Params_70.PinTanPar2.ParPinTan.PinTanGV_46.segcode", "HKBSA");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_2", "500");
		bpd.setProperty("Params_45.DauerLastSEPANewPar1.ParDauerLastSEPANew.turnusmonths", "0102030612");
		bpd.setProperty("Params_68.TAN2StepPar6.ParTAN2Step.TAN2StepParams_4.name", "Smart-TAN plus optisch / USB");
		bpd.setProperty("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.03.xsd");
		return bpd;
	}

	public static Properties buildUPD() {
		Properties upd = new Properties();
		upd.setProperty("_hbciversion", "300");
		upd.setProperty("KInfo.AllowedGV_3.code", "HKSSP");
		upd.setProperty("KInfo.AllowedGV_18.code", "HKKAA");
		upd.setProperty("KInfo.AllowedGV_3.reqSigs", "1");
		upd.setProperty("UPA.SegHead.code", "HIUPA");
		upd.setProperty("KInfo.AllowedGV_20.reqSigs", "1");
		upd.setProperty("UPA.usage", "0");
		upd.setProperty("KInfo.AllowedGV_24.reqSigs", "1");
		upd.setProperty("KInfo.AllowedGV_12.code", "HKBMB");
		upd.setProperty("KInfo.konto", "Termineinlage");
		return upd;
	}
	
	public static Properties buildBPD2() {
		Properties bpd = new Properties();
		bpd.setProperty("Params_31.Template2DPar.ParTemplate2D.dummy", "0;1;110000");
		bpd.setProperty("Params_52.Template2Par.SegHead.code", "HIBMLS");
		bpd.setProperty("Params_58.Template2DPar.ParTemplate2D.dummy_3", "20000");
		bpd.setProperty("Params_50.Template2DPar.SegHead.code", "HIIPSS");
		bpd.setProperty("Params_15.SEPAInfoPar1.ParSEPAInfo.suppformats_2", "sepade:xsd:pain.001.001.04.xsd");
		return bpd;
	}

	public static Properties buildUPD2() {
		Properties upd = new Properties();
		upd.setProperty("_hbciversion", "200");
		upd.setProperty("KInfo.AllowedGV_3.code", "HKSSP");
		upd.setProperty("KInfo.AllowedGV_18.code", "HKKAA");
		upd.setProperty("KInfo.AllowedGV_12.code", "HKBMB");
		upd.setProperty("KInfo.konto", "Kontokorrent");
		return upd;
	}

	public static Recipient createSampleRecipient01() {
		Recipient r = new Recipient();
		r.setName("Erika Mustermann");
		r.setIban("DE12345678901234567890");
		r.setBic("BYLADEM1001");
		r.setSource(Source.IMPORT_INITIAL);
		return r;
	}

	public static Recipient createSamplerecipient02() {
		Recipient r1 = new Recipient();
		r1.setName("Dup");
		r1.setIban("DE99999999999999999999");
		r1.setSource(Source.IMPORT_INITIAL);
		return r1;
	}

	public static Recipient createSampleRecipient03() {
		Recipient r2 = new Recipient();
		r2.setName("DupUpdated");
		r2.setIban("DE99999999999999999999");
		r2.setSource(Source.MANUELL);
		return r2;
	}
	
	public static Recipient createRecipientWithParams(String recipientName, Source source, String iban) {
		Recipient r = new Recipient();
		r.setName(recipientName);
		r.setIban(iban);
		r.setSource(source);
		return r;
	}
	
	public static MoneyTransfer createSampleMoneytransfer01(int accountId) {
		MoneyTransfer mt = new MoneyTransfer();
		mt.setAccountId(accountId);
		mt.setAmount(new BigDecimal(100.0));
		mt.setOrderType(OrderType.TRANSFER);
		mt.setPurpose("Bezahlung Rechung Nr 1234");
		mt.setMoneytransferStatus(MoneyTransferStatus.SENT);
		mt.setSource(Source.MANUELL);
		return mt;
	}

}
