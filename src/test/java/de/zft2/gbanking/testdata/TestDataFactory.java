package de.zft2.gbanking.testdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BusinessCase;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.HbciEncodingFilterType;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import de.zft2.gbanking.db.dao.enu.OrderType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.TanProcedure;

public final class TestDataFactory {

	private static final Random RANDOM = new Random();

	private TestDataFactory() {
	}

	public static BankAccess createSampleBankAccess(String blz) {
		BankAccess bankAccess = new BankAccess();
		bankAccess.setBankName("TestBank-" + blz);
		bankAccess.getFints().setCountry("DE");
		bankAccess.getFints().setBlz(blz);
		bankAccess.getFints().setHbciURL("https://hbci.test/" + blz);
		bankAccess.getFints().setPort(3000);
		bankAccess.getFints().setUserId("user-" + blz);
		bankAccess.getFints().setCustomerId("cust-" + blz);
		bankAccess.getFints().setSysId("sys-" + blz);
		bankAccess.getFints().setTanProcedure(TanProcedure.APP_SECUREGO_PLUS);
		bankAccess.getFints().setAllowedTwostepMechanisms(Arrays.asList("SMS", "APP"));
		bankAccess.getFints().setHbciVersion("300");
		bankAccess.getFints().setBpdVersion("1");
		bankAccess.getFints().setUpdVersion("1");
		bankAccess.getFints().setFilterType(HbciEncodingFilterType.NONE);
		bankAccess.setActive(true);
		bankAccess.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return bankAccess;
	}

	public static BankAccount createSampleAccount(Integer bankAccessId) {
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccessId);
		account.setAccountName("Giro-" + UUID.randomUUID().toString().substring(0, 6));
		account.setCurrency("EUR");
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setSource(Source.ONLINE);
		int randomNumber = RANDOM.nextInt(900000000);
		account.setIban("DE" + (100000000 + randomNumber));
		account.setBic("TESTBIC");
		account.setNumber(String.valueOf(randomNumber));
		account.setSubnumber("00");
		account.setBankName("TestBank");
		account.setBlz("00000000");
		account.setHbciAccountType(0);
		account.setLimit("1000");
		account.setCustomerid("CUST-1");
		account.setOwnerName("Max Mustermann");
		account.setOwnerName2("Erika Mustermann");
		account.setCountry("DE");
		account.setCreditorid("CR-1");
		account.setSEPAAccount(true);
		account.setAccountState(AccountState.ACTIVE);
		account.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return account;
	}

	public static BankAccount createAccountWithParams(Integer bankAccessId, String accountName, Source source,
			AccountType accountType, AccountState accountState, String owner, String iban) {
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccessId);
		account.setAccountName(accountName);
		account.setCurrency("EUR");
		account.setAccountType(accountType);
		account.setSource(source);
		account.setIban(iban);
		account.setOwnerName(owner);
		account.setCountry("DE");
		account.setSEPAAccount(true);
		account.setAccountState(accountState);
		account.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return account;
	}

	public static BankAccount createForBankAccess(Integer bankAccessId) {
		BankAccount account = new BankAccount();
		account.setBankAccessId(bankAccessId);
		return account;
	}

	public static BankAccount createWithId(int id) {
		BankAccount account = new BankAccount();
		account.setId(id);
		return account;
	}

	public static BankAccount createWithIdAndBankAccess(int id, Integer bankAccessId) {
		BankAccount account = createWithId(id);
		account.setBankAccessId(bankAccessId);
		return account;
	}

	public static BankAccount createEuroAccount(int id) {
		BankAccount account = createWithId(id);
		account.setBaseCurrency(Currency.EUR);
		return account;
	}

	public static BankAccount createImportedAccount(String iban, String number) {
		BankAccount account = new BankAccount();
		account.setIban(iban);
		account.setNumber(number);
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		account.setSource(Source.IMPORT_INITIAL);
		account.setAccountState(AccountState.ACTIVE);
		account.setCurrency("EUR");
		return account;
	}

	public static BankAccount createImportedAccount(String iban, String number, String bic) {
		BankAccount account = createImportedAccount(iban, number);
		account.setBic(bic);
		return account;
	}

	public static Booking createSampleBooking(Integer bankAccountId) {
		return createBooking(bankAccountId, null, "Miete", new BigDecimal("1200.00"), BookingType.REMOVAL);
	}

	public static Booking createSampleBookingWithRecipient(Integer bankAccountId, Integer recipientId) {
		return createBooking(bankAccountId, recipientId, "Bareinzahlung", new BigDecimal("50.00"), BookingType.DEPOSIT);
	}

	public static Booking createSampleBooking2(Integer bankAccountId) {
		return createBooking(bankAccountId, null, "Kreditrate", new BigDecimal("400.00"), BookingType.REMOVAL);
	}

	public static Booking createBookingWithParams(Integer bankAccountId, Integer recipientId, String purpose,
			long amount, BookingType bookingType) {
		return createBooking(bankAccountId, recipientId, purpose, BigDecimal.valueOf(amount), bookingType);
	}

	public static Category createSampleCategory(String categoryName) {
		Category category = new Category(categoryName);
		category.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return category;
	}

	public static Recipient createSampleRecipient01() {
		Recipient recipient = new Recipient();
		recipient.setName("Erika Mustermann");
		recipient.setIban("DE12345678901234567890");
		recipient.setBic("BYLADEM1001");
		recipient.setSource(Source.IMPORT_INITIAL);
		return recipient;
	}

	public static Recipient createSampleRecipient02() {
		Recipient recipient = new Recipient();
		recipient.setName("Dup");
		recipient.setIban("DE99999999999999999999");
		recipient.setSource(Source.IMPORT_INITIAL);
		return recipient;
	}

	public static Recipient createSampleRecipient03() {
		Recipient recipient = new Recipient();
		recipient.setName("DupUpdated");
		recipient.setIban("DE99999999999999999999");
		recipient.setSource(Source.MANUELL);
		return recipient;
	}

	public static Recipient createRecipientWithParams(String recipientName, Source source, String iban) {
		Recipient recipient = new Recipient();
		recipient.setName(recipientName);
		recipient.setIban(iban);
		recipient.setSource(source);
		return recipient;
	}

	public static MoneyTransfer createSampleMoneyTransfer(int accountId) {
		MoneyTransfer moneyTransfer = new MoneyTransfer();
		moneyTransfer.setAccountId(accountId);
		moneyTransfer.setAmount(BigDecimal.valueOf(100));
		moneyTransfer.setOrderType(OrderType.TRANSFER);
		moneyTransfer.setPurpose("Bezahlung Rechung Nr 1234");
		moneyTransfer.setMoneytransferStatus(MoneyTransferStatus.SENT);
		moneyTransfer.setSource(Source.MANUELL);
		return moneyTransfer;
	}

	public static BusinessCase createBusinessCase(String caseValue) {
		BusinessCase businessCase = new BusinessCase();
		businessCase.setCaseValue(caseValue);
		return businessCase;
	}

	private static Booking createBooking(Integer bankAccountId, Integer recipientId, String purpose, BigDecimal amount,
			BookingType bookingType) {
		Booking booking = new Booking();
		booking.setAccountId(bankAccountId);
		if (recipientId != null) {
			booking.setRecipientId(recipientId);
		}
		LocalDate date = LocalDate.now(ZoneId.systemDefault());
		booking.setDateBooking(date);
		booking.setDateValue(date);
		booking.setPurpose(purpose);
		booking.setAmount(amount);
		booking.setBookingType(bookingType);
		booking.setSource(Source.ONLINE);
		return booking;
	}
}
