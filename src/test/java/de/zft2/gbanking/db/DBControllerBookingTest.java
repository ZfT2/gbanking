package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.concurrent.CancellationSupport;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.BookingAdditionalDetails;
import de.zft2.gbanking.db.dao.BookingCreditCardDetails;
import de.zft2.gbanking.db.dao.BookingFee;
import de.zft2.gbanking.db.dao.BookingForeignCurrencyDetails;
import de.zft2.gbanking.db.dao.BookingNoteDetails;
import de.zft2.gbanking.db.dao.BookingSepaDetails;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.SepaType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.exception.GBankingException;

class DBControllerBookingTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - Booking insertion
	// ------------------------------------------------------------

	@Test
	void insertBooking_shouldWork() throws Exception {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);
		assertBookingDetailCounts(booking.getId(), 0, 0, 0, 0);

		assertEquals("Miete", booking.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking.getAmount());
		assertEquals(BookingType.REMOVAL, booking.getBookingType());
		assertEquals(Source.ONLINE, booking.getSource());

		LocalDate dateCurrentWithoutSeconds = getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault()));
		LocalDate dateBookingWithoutSeconds = getCalendarWithoutTime(booking.getDateBooking());
		LocalDate dateValueWithoutSeconds = getCalendarWithoutTime(booking.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);
	}

	@Test
	void insertBookingWithSepaInformation_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		booking.setSepaDetails(createSepaDetails());

		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);

		assertEquals("Miete", booking.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking.getAmount());
		assertEquals(BookingType.REMOVAL, booking.getBookingType());
		assertEquals(Source.ONLINE, booking.getSource());
		LocalDate dateCurrentWithoutSeconds = getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault()));
		LocalDate dateBookingWithoutSeconds = getCalendarWithoutTime(booking.getDateBooking());
		LocalDate dateValueWithoutSeconds = getCalendarWithoutTime(booking.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertBookingSepaInfo(booking, "Customer");
	}

	@Test
	void insertBookingWithSepaAndAdditionalInformation_shouldPersistInSubTables() throws Exception {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		booking.setSepaDetails(createSepaDetails());
		booking.setAdditionalDetails(createAdditionalDetails());
		booking.setCreditCardDetails(createCreditCardDetails());
		booking.setForeignCurrencyDetails(createForeignCurrencyDetails());
		booking.setFee(createFee());
		booking.setNoteDetails(createNoteDetails("Beleg anfordern", true));

		db.insertOrUpdate(booking);
		BookingSepaDetails updatedSepaDetails = booking.getSepaDetails();
		assertNotNull(updatedSepaDetails);
		updatedSepaDetails.setCustomerRef("Customer-Updated");
		booking.setSepaDetails(updatedSepaDetails);
		BookingAdditionalDetails updatedAdditionalDetails = booking.getAdditionalDetails();
		assertNotNull(updatedAdditionalDetails);
		updatedAdditionalDetails.setInstref("INST-2");
		booking.setAdditionalDetails(updatedAdditionalDetails);
		db.insertOrUpdate(booking);

		Booking reloaded = db.getByIdFull(Booking.class, booking.getId());

		assertBookingSepaInfo(reloaded);
		assertBookingAdditionalInfo(reloaded);
		assertBookingAdditionalCreditcardInfo(reloaded);
		assertNotNull(reloaded.getNoteDetails());
		assertEquals("Beleg anfordern", reloaded.getNoteDetails().getNote());
		assertTrue(reloaded.getNoteDetails().isReviewRequired());

		assertFalse(tableHasColumn("booking", "sepaCustomerRef"));
		assertFalse(tableHasColumn("booking", "addInstref"));
		assertTrue(tableHasColumn("bookingAdditionalSepa", "sepa_customer_ref"));
		assertTrue(tableHasColumn("bookingAdditionalSepa", "updatedAt"));
		assertTrue(tableHasColumn("bookingAdditionalNote", "note"));
		assertTrue(tableHasColumn("bookingAdditionalNote", "review_required"));
		assertTrue(tableHasColumn("bookingAdditionalNote", "updatedAt"));
		assertTrue(tableHasColumn("bookingAdditional", "add_instref"));
		assertTrue(tableHasColumn("bookingAdditional", "updatedAt"));
		assertTrue(tableHasColumn("bookingAdditionalCreditcard", "creditcard_transaction_date"));
		assertFalse(tableHasColumn("bookingAdditionalCreditcard", "creditcard_currency_rate"));
		assertTrue(tableHasColumn("bookingAdditionalCreditcard", "updatedAt"));
		assertTrue(tableHasColumn("bookingAdditionalForeigncurrency", "exchangeRateToBaseCurrency"));
		assertTrue(tableHasColumn("bookingFee", "currency"));
		assertBookingDetailsStoredInSubTables(booking.getId());
		assertBookingFullUpdatedAtIsNewestDetailTimestamp(booking.getId());
	}

	private BookingSepaDetails createSepaDetails() {
		BookingSepaDetails details = new BookingSepaDetails();
		details.setCustomerRef("Customer");
		details.setCreditorId("sepaCreditor");
		details.setEndToEnd("EndToEnd");
		details.setMandate("sepaMandate");
		details.setPersonId("sepaPersonId");
		details.setPurpose("sepaPurpose");
		details.setType(SepaType.BANK_TRANSFER_ONLINE);
		return details;
	}

	private BookingAdditionalDetails createAdditionalDetails() {
		BookingAdditionalDetails details = new BookingAdditionalDetails();
		details.setInstref("INST-1");
		details.setGvcode("166");
		details.setText("SEPA CREDIT TRANSFER");
		details.setPrimanota("PN-1");
		details.setKey("KEY-1");
		details.setStorno(Boolean.TRUE);
		details.setRawData("RAW");
		details.setSepa(Boolean.TRUE);
		details.setCamt(Boolean.FALSE);
		details.setBankSaldo(new BigDecimal("3456.78"));
		return details;
	}

	private BookingCreditCardDetails createCreditCardDetails() {
		BookingCreditCardDetails details = new BookingCreditCardDetails();
		details.setTransactionDate(LocalDate.of(2022, Month.AUGUST, 14));
		details.setType("Einkauf");
		details.setMerchantArea("SZCZECIN");
		details.setMerchantCategory("Service Stations");
		return details;
	}

	private BookingForeignCurrencyDetails createForeignCurrencyDetails() {
		BookingForeignCurrencyDetails details = new BookingForeignCurrencyDetails();
		details.setForeignAmount(new BigDecimal("-182.52"));
		details.setForeignCurrency(Currency.PLN);
		details.setExchangeRateToBaseCurrency(new BigDecimal("0.214880561"));
		return details;
	}

	private BookingFee createFee() {
		BookingFee fee = new BookingFee();
		fee.setAmount(new BigDecimal("1.23"));
		fee.setCurrency(Currency.EUR);
		return fee;
	}

	private BookingNoteDetails createNoteDetails(String note, boolean reviewRequired) {
		BookingNoteDetails details = new BookingNoteDetails();
		details.setNote(note);
		details.setReviewRequired(reviewRequired);
		return details;
	}

	private void assertBookingAdditionalInfo(Booking reloaded) {
		BookingAdditionalDetails details = reloaded.getAdditionalDetails();
		assertNotNull(details);
		assertEquals("INST-2", details.getInstref());
		assertEquals("166", details.getGvcode());
		assertEquals("SEPA CREDIT TRANSFER", details.getText());
		assertEquals("PN-1", details.getPrimanota());
		assertEquals("KEY-1", details.getKey());
		assertEquals(Boolean.TRUE, details.getStorno());
		assertEquals("RAW", details.getRawData());
		assertEquals(Boolean.TRUE, details.getSepa());
		assertEquals(Boolean.FALSE, details.getCamt());
		assertEquals(new BigDecimal("3456.78"), details.getBankSaldo());
	}

	private void assertBookingAdditionalCreditcardInfo(Booking reloaded) {
		BookingCreditCardDetails details = reloaded.getCreditCardDetails();
		assertNotNull(details);
		assertEquals(LocalDate.of(2022, Month.AUGUST, 14), details.getTransactionDate());
		assertEquals("Einkauf", details.getType());
		assertEquals("SZCZECIN", details.getMerchantArea());
		assertEquals("Service Stations", details.getMerchantCategory());
		BookingForeignCurrencyDetails foreign = reloaded.getForeignCurrencyDetails();
		assertNotNull(foreign);
		assertEquals(0, new BigDecimal("-182.52").compareTo(foreign.getForeignAmount()));
		assertEquals(Currency.PLN, foreign.getForeignCurrency());
		assertEquals(0, new BigDecimal("0.214880561").compareTo(foreign.getExchangeRateToBaseCurrency()));
		assertNotNull(reloaded.getFee());
		assertEquals(new BigDecimal("1.23"), reloaded.getFee().getAmount());
		assertEquals(Currency.EUR, reloaded.getFee().getCurrency());
	}

	private void assertBookingSepaInfo(Booking reloaded) {
		assertBookingSepaInfo(reloaded, "Customer-Updated");
	}

	private void assertBookingSepaInfo(Booking booking, String customerRef) {
		BookingSepaDetails details = booking.getSepaDetails();
		assertNotNull(details);
		assertEquals(customerRef, details.getCustomerRef());
		assertEquals("sepaCreditor", details.getCreditorId());
		assertEquals("EndToEnd", details.getEndToEnd());
		assertEquals("sepaMandate", details.getMandate());
		assertEquals("sepaPersonId", details.getPersonId());
		assertEquals("sepaPurpose", details.getPurpose());
		assertEquals(SepaType.BANK_TRANSFER_ONLINE, details.getType());
	}

	@Test
	void insertBookingWithoutDetailData_shouldNotCreateSubTableRows() throws Exception {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		BookingSepaDetails sepaDetails = new BookingSepaDetails();
		sepaDetails.setCustomerRef(" ");
		sepaDetails.setPurpose("");
		booking.setSepaDetails(sepaDetails);
		BookingAdditionalDetails additionalDetails = new BookingAdditionalDetails();
		additionalDetails.setInstref(" ");
		additionalDetails.setText("");
		booking.setAdditionalDetails(additionalDetails);
		BookingCreditCardDetails creditCardDetails = new BookingCreditCardDetails();
		creditCardDetails.setType(" ");
		creditCardDetails.setMerchantArea("");
		booking.setCreditCardDetails(creditCardDetails);
		booking.setNoteDetails(createNoteDetails(" ", false));

		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);
		assertBookingDetailCounts(booking.getId(), 0, 0, 0, 0);

		Booking reloaded = db.getByIdFull(Booking.class, booking.getId());

		assertNull(reloaded.getSepaDetails());
		assertNull(reloaded.getAdditionalDetails());
		assertNull(reloaded.getCreditCardDetails());
		assertNull(reloaded.getNoteDetails());
	}

	@Test
	void updateBookingWithoutDetailData_shouldRemoveSubTableRows() throws Exception {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		booking.setSepaCustomerRef("Customer");
		BookingAdditionalDetails additionalDetails = new BookingAdditionalDetails();
		additionalDetails.setInstref("INST-1");
		booking.setAdditionalDetails(additionalDetails);
		BookingCreditCardDetails creditCardDetails = new BookingCreditCardDetails();
		creditCardDetails.setType("Einkauf");
		booking.setCreditCardDetails(creditCardDetails);
		booking.setNoteDetails(createNoteDetails("Prüfen", true));
		db.insertOrUpdate(booking);
		assertBookingDetailCounts(booking.getId(), 1, 1, 1, 1);

		booking.setSepaDetails(null);
		booking.setAdditionalDetails(null);
		booking.setCreditCardDetails(null);
		booking.setNoteDetails(null);
		db.insertOrUpdate(booking);

		assertBookingDetailCounts(booking.getId(), 0, 0, 0, 0);
	}

	@Test
	void deleteBooking_shouldCascadeToSubTables() throws Exception {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		booking.setSepaCustomerRef("Customer");
		BookingAdditionalDetails additionalDetails = new BookingAdditionalDetails();
		additionalDetails.setInstref("INST-1");
		booking.setAdditionalDetails(additionalDetails);
		BookingCreditCardDetails creditCardDetails = new BookingCreditCardDetails();
		creditCardDetails.setType("Einkauf");
		booking.setCreditCardDetails(creditCardDetails);
		booking.setNoteDetails(createNoteDetails("Prüfen", true));
		db.insertOrUpdate(booking);
		assertBookingDetailCounts(booking.getId(), 1, 1, 1, 1);

		boolean deleted = db.delete(booking, null);

		assertTrue(deleted);
		assertBookingDetailCounts(booking.getId(), 0, 0, 0, 0);
	}

	@Test
	void updateBookingAdditionalNote_shouldOnlyUpdateAnnotationForOnlineBooking() throws Exception {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);
		booking.setNoteDetails(createNoteDetails("Rechnung zuordnen", true));

		db.updateBookingAdditionalNote(booking);

		Booking reloaded = db.getByIdFull(Booking.class, booking.getId());
		assertEquals("Miete", reloaded.getPurpose());
		assertEquals(Source.ONLINE, reloaded.getSource());
		assertNotNull(reloaded.getNoteDetails());
		assertEquals("Rechnung zuordnen", reloaded.getNoteDetails().getNote());
		assertTrue(reloaded.getNoteDetails().isReviewRequired());
		assertBookingDetailCounts(booking.getId(), 0, 0, 1, 0);

		booking.setNoteDetails(null);
		db.updateBookingAdditionalNote(booking);

		reloaded = db.getByIdFull(Booking.class, booking.getId());
		assertNull(reloaded.getNoteDetails());
		assertBookingDetailCounts(booking.getId(), 0, 0, 0, 0);
	}
	
	// ------------------------------------------------------------
	// Tests - Booking update
	// ------------------------------------------------------------
	
	@Test
	void updateBookingSourceOneAccount_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);
		
		Booking booking01 = TestData.createSampleBooking(acc01.getId());
		db.insertOrUpdate(booking01);
		Booking booking02 = TestData.createSampleBooking(acc01.getId());
		db.insertOrUpdate(booking02);
		
		acc01 = db.getByIdFull(BankAccount.class, acc01.getId());

		booking01.setSource(Source.MANUELL);
		booking02.setSource(Source.MANUELL);

		int result = db.executeSimpleUpdate(Arrays.asList(acc01), StatementsConfig.StatementType.UPDATE_BOOKING_SOURCE, Booking.class);

		assertTrue(result >= 0);

		assertEquals(Source.MANUELL, booking01.getSource());
		assertEquals(Source.MANUELL, booking02.getSource());
	}
	
	@Test
	void updateBookingSourceMultipleAccounts_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		BankAccount acc02 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc02);
		
		Booking booking01 = TestData.createSampleBooking(acc01.getId());
		db.insertOrUpdate(booking01);
		Booking booking02 = TestData.createSampleBooking(acc01.getId());
		db.insertOrUpdate(booking02);
		Booking booking03 = TestData.createSampleBooking(acc02.getId());
		db.insertOrUpdate(booking03);
		
		acc01 = db.getByIdFull(BankAccount.class, acc01.getId());

		booking01.setSource(Source.MANUELL);
		booking02.setSource(Source.MANUELL);
		booking03.setSource(Source.MANUELL);

		int result = db.executeSimpleUpdate(Arrays.asList(acc01), StatementsConfig.StatementType.UPDATE_BOOKING_SOURCE, Booking.class);

		assertTrue(result >= 0);

		assertEquals(Source.MANUELL, booking01.getSource());
		assertEquals(Source.MANUELL, booking02.getSource());
		assertEquals(Source.MANUELL, booking03.getSource());
	}

	@Test
	void updateBookingsWithRecipients_shouldOnlyAssignMissingRecipient() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);
		Recipient recipient01 = TestData.createRecipientWithParams("Max Mustermann", Source.ONLINE, "DE12345678001");
		db.insertOrUpdate(recipient01);
		Recipient recipient02 = TestData.createRecipientWithParams("Erika Mustermann", Source.ONLINE, "DE12345678002");
		db.insertOrUpdate(recipient02);

		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);

		assertTrue(db.updateBookingsWithRecipients(Map.of(recipient01, Set.of(booking.getId()))));
		assertEquals(recipient01.getId(), db.getByIdFull(Booking.class, booking.getId()).getRecipientId());

		Map<Recipient, Set<Integer>> invalidUpdate = Map.of(recipient02, Set.of(booking.getId()));
		assertThrows(GBankingException.class, () -> db.updateBookingsWithRecipients(invalidUpdate));
		assertEquals(recipient01.getId(), db.getByIdFull(Booking.class, booking.getId()).getRecipientId());
	}

	@Test
	void systemBooking_shouldRejectRecipientReplacementAfterInitialAssignment() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);
		Recipient recipient01 = TestData.createRecipientWithParams("Max Mustermann", Source.ONLINE, "DE12345678001");
		db.insertOrUpdate(recipient01);
		Recipient recipient02 = TestData.createRecipientWithParams("Erika Mustermann", Source.ONLINE, "DE12345678002");
		db.insertOrUpdate(recipient02);

		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);
		db.updateBookingsWithRecipients(Map.of(recipient01, Set.of(booking.getId())));

		booking = db.getByIdFull(Booking.class, booking.getId());
		booking.setRecipientId(recipient02.getId());
		Booking bookingToUpdate = booking;
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(bookingToUpdate));

		assertEquals(recipient01.getId(), db.getByIdFull(Booking.class, booking.getId()).getRecipientId());
	}

	@Test
	void manualBooking_shouldAllowRecipientReplacement() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);
		Recipient recipient01 = TestData.createRecipientWithParams("Max Mustermann", Source.MANUELL, "DE12345678001");
		db.insertOrUpdate(recipient01);
		Recipient recipient02 = TestData.createRecipientWithParams("Erika Mustermann", Source.MANUELL, "DE12345678002");
		db.insertOrUpdate(recipient02);

		Booking booking = TestData.createSampleBooking(acc.getId());
		booking.setSource(Source.MANUELL);
		booking.setRecipientId(recipient01.getId());
		db.insertOrUpdate(booking);

		booking.setRecipientId(recipient02.getId());
		db.insertOrUpdate(booking);

		assertEquals(recipient02.getId(), db.getByIdFull(Booking.class, booking.getId()).getRecipientId());
	}
	
	@Test
	void insertMultipleBookings01_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking01 = TestData.createSampleBooking(acc.getId());
		booking01.setSepaDetails(createSepaDetails());
		
		Booking booking02 = TestData.createSampleBooking2(acc.getId());

		boolean result = db.insertAccountBookings(Arrays.asList(booking01, booking02));

		assertTrue(result);
		assertTrue(booking01.getId() > 0);
		assertTrue(booking02.getId() > 0);
		assertNotEquals(booking01.getId(), booking02.getId());
		
		List<Booking> bookingList = db.getAllByParentFull(Booking.class, acc.getId());
		
		booking01 = findById(bookingList, booking01.getId());

		assertEquals("Miete", booking01.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking01.getAmount());
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		LocalDate dateCurrentWithoutSeconds = getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault()));
		LocalDate dateBookingWithoutSeconds = getCalendarWithoutTime(booking01.getDateBooking());
		LocalDate dateValueWithoutSeconds = getCalendarWithoutTime(booking01.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertBookingSepaInfo(booking01, "Customer");
		
		booking02 = findById(bookingList, booking02.getId());
		
		assertEquals("Kreditrate", booking02.getPurpose());
		assertEquals(new BigDecimal("400.00"), booking02.getAmount());
		assertEquals(BookingType.REMOVAL, booking02.getBookingType());
		assertEquals(Source.ONLINE, booking02.getSource());
	}

	@Test
	void fullBookingList_shouldReuseJoinedRelationsWithoutAdditionalQueries() {
		BankAccess bankAccess = db.insertOrUpdate(TestData.createSampleBankAccess("44444444"));
		BankAccount account = db.insertOrUpdate(TestData.createSampleAccount(bankAccess.getId()));
		Recipient recipient = TestData.createSampleRecipient01();
		recipient.setAccountNumber("4711");
		recipient.setBlz("10010010");
		recipient.setBank("Testbank");
		recipient.setNote("Bevorzugter Empfänger");
		recipient.setDefault(true);
		recipient = db.insertOrUpdate(recipient);
		Category category = db.insertOrUpdate(TestData.createSampleCategory("Wohnen:Miete"));
		Recipient expectedRecipient = db.getById(Recipient.class, recipient.getId());
		Category expectedCategory = db.getById(Category.class, category.getId());

		Booking firstBooking = TestData.createSampleBookingWithRecipient(account.getId(), recipient.getId());
		firstBooking.setCategory(category);
		db.insertOrUpdate(firstBooking);
		Booking secondBooking = TestData.createSampleBookingWithRecipient(account.getId(), recipient.getId());
		secondBooking.setCategory(category);
		db.insertOrUpdate(secondBooking);

		QueryMeasurement<List<Booking>> listMeasurement = measureQueries(
				() -> db.getAllByParentFull(Booking.class, account.getId()));

		assertEquals(1, listMeasurement.queryCount());
		assertEquals(2, listMeasurement.result().size());
		for (Booking booking : listMeasurement.result()) {
			assertBookingRelations(booking, expectedRecipient, expectedCategory);
		}

		Booking byIdResult = db.getByIdFull(Booking.class, firstBooking.getId());

		assertBookingRelations(byIdResult, expectedRecipient, expectedCategory);
	}

	@Test
	void getByIdFull_shouldPreserveCancellationDuringRelationLoading() {
		BankAccess bankAccess = db.insertOrUpdate(TestData.createSampleBankAccess("44444444"));
		BankAccount account = db.insertOrUpdate(TestData.createSampleAccount(bankAccess.getId()));
		Recipient recipient = db.insertOrUpdate(TestData.createSampleRecipient01());
		Booking booking = db.insertOrUpdate(TestData.createSampleBookingWithRecipient(account.getId(), recipient.getId()));
		AtomicInteger cancellationChecks = new AtomicInteger();

		assertThrows(CancellationException.class,
				() -> CancellationSupport.runWithCancellation(() -> cancellationChecks.incrementAndGet() >= 3,
						() -> db.getByIdFull(Booking.class, booking.getId())));
	}
	
	@Test
	void insertMultipleBookings02_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking01 = TestData.createSampleBooking(acc.getId());
		booking01.setSepaDetails(createSepaDetails());
		
		Booking booking02 = TestData.createSampleBooking2(acc.getId());

		Set<Booking> bookingSet = db.insertAll(new HashSet<>(Arrays.asList(booking01, booking02)));

		assertNotNull(bookingSet);
		assertTrue(booking01.getId() > 0);
		assertTrue(booking02.getId() > 0);
		assertNotEquals(booking01.getId(), booking02.getId());
	
		booking01 = findById(bookingSet, booking01.getId());

		assertEquals("Miete", booking01.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking01.getAmount());
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		LocalDate dateCurrentWithoutSeconds = getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault()));
		LocalDate dateBookingWithoutSeconds = getCalendarWithoutTime(booking01.getDateBooking());
		LocalDate dateValueWithoutSeconds = getCalendarWithoutTime(booking01.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertBookingSepaInfo(booking01, "Customer");
		
		booking02 = findById(bookingSet, booking02.getId());
		
		assertEquals("Kreditrate", booking02.getPurpose());
		assertEquals(new BigDecimal("400.00"), booking02.getAmount());
		assertEquals(BookingType.REMOVAL, booking02.getBookingType());
		assertEquals(Source.ONLINE, booking02.getSource());
	}
	
	@Test
	void findCrossBooking_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		
		BankAccount acc01 = TestData.createAccountWithParams(ba.getId(), "Girokonto 01", Source.ONLINE, AccountType.CURRENT_ACCOUNT, AccountState.ACTIVE, "Max Mustermann", "DE12345678001");
		db.insertOrUpdate(acc01);
		BankAccount acc02 = TestData.createAccountWithParams(ba.getId(), "Tagesgeld 01", Source.ONLINE, AccountType.OVERNIGHT_MONEY, AccountState.ACTIVE, "Max Mustermann", "DE12345678002");
		db.insertOrUpdate(acc02);
		
		Recipient recipient01 = TestData.createRecipientWithParams("Max Mustermann", Source.ONLINE, "DE12345678002");
		db.insertOrUpdate(recipient01);
		Booking booking01 = TestData.createBookingWithParams(acc01.getId(), recipient01.getId(), "Umbuchung auf TG", -500.00, BookingType.REMOVAL);
		db.insertOrUpdate(booking01);
		booking01 = db.getByIdFull(Booking.class, booking01.getId());
		
		Recipient recipient02 = TestData.createRecipientWithParams("Max Mustermann", Source.ONLINE, "DE12345678001");
		db.insertOrUpdate(recipient02);
		Booking booking02 = TestData.createBookingWithParams(acc02.getId(), recipient02.getId(), "Umbuchung auf TG", 500.00, BookingType.DEPOSIT);
		db.insertOrUpdate(booking02);
		booking02 = db.getByIdFull(Booking.class, booking02.getId());
		
		Booking rebookingToCheck = db.findCrossBooking(booking01);

		assertEquals(booking02.getPurpose(), rebookingToCheck.getPurpose());
		assertEquals(booking02.getAmount(), rebookingToCheck.getAmount()/*.multiply(new BigDecimal(-1))*/);
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(BookingType.DEPOSIT, booking02.getBookingType());
		assertEquals(BookingType.DEPOSIT, rebookingToCheck.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		assertEquals(Source.ONLINE, rebookingToCheck.getSource());
		LocalDate dateCurrentWithoutSeconds = getCalendarWithoutTime(LocalDate.now(ZoneId.systemDefault()));
		LocalDate dateBookingWithoutSeconds = getCalendarWithoutTime(rebookingToCheck.getDateBooking());
		LocalDate dateValueWithoutSeconds = getCalendarWithoutTime(rebookingToCheck.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);
	}

	private boolean tableHasColumn(String tableName, String columnName) throws SQLException {
		try (Statement stmt = DBController.getConnection().createStatement();
				ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
			while (rs.next()) {
				if (columnName.equals(rs.getString("name"))) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertBookingDetailsStoredInSubTables(int bookingId) throws SQLException {
		String sql = """
				SELECT bse.sepa_customer_ref, bse.updatedAt AS sepaUpdatedAt,
					bad.add_instref, bad.add_bank_saldo, bad.updatedAt AS additionalUpdatedAt,
					baf.foreignAmount, baf.foreignCurrency, baf.exchangeRateToBaseCurrency,
					baf.updatedAt AS foreignUpdatedAt, bfe.amount AS feeAmount, bfe.currency AS feeCurrency,
					bfe.updatedAt AS feeUpdatedAt, bac.updatedAt AS creditcardUpdatedAt,
					bno.note, bno.review_required, bno.updatedAt AS noteUpdatedAt
				FROM bookingAdditionalSepa bse
				JOIN bookingAdditional bad ON bad.booking_id = bse.booking_id
				JOIN bookingAdditionalCreditcard bac ON bac.booking_id = bse.booking_id
				JOIN bookingAdditionalForeigncurrency baf ON baf.booking_id = bse.booking_id
				JOIN bookingFee bfe ON bfe.booking_id = bse.booking_id
				JOIN bookingAdditionalNote bno ON bno.booking_id = bse.booking_id
				WHERE bse.booking_id = ?
				""";
		try (PreparedStatement ps = DBController.getConnection().prepareStatement(sql)) {
			ps.setInt(1, bookingId);
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Customer-Updated", rs.getString("sepa_customer_ref"));
				assertEquals("INST-2", rs.getString("add_instref"));
				assertEquals(new BigDecimal("3456.78"), rs.getBigDecimal("add_bank_saldo"));
				assertEquals(new BigDecimal("-182.52"), rs.getBigDecimal("foreignAmount"));
				assertEquals(Currency.PLN.getDbStateId(), rs.getInt("foreignCurrency"));
				assertEquals(0, new BigDecimal("0.214880561").compareTo(rs.getBigDecimal("exchangeRateToBaseCurrency")));
				assertEquals(new BigDecimal("1.23"), rs.getBigDecimal("feeAmount"));
				assertEquals(Currency.EUR.getDbStateId(), rs.getInt("feeCurrency"));
				assertEquals("Beleg anfordern", rs.getString("note"));
				assertTrue(rs.getBoolean("review_required"));
				assertNotNull(rs.getString("sepaUpdatedAt"));
				assertNotNull(rs.getString("additionalUpdatedAt"));
				assertNotNull(rs.getString("creditcardUpdatedAt"));
				assertNotNull(rs.getString("foreignUpdatedAt"));
				assertNotNull(rs.getString("feeUpdatedAt"));
				assertNotNull(rs.getString("noteUpdatedAt"));
				assertFalse(rs.next());
			}
		}
	}

	private void assertBookingFullUpdatedAtIsNewestDetailTimestamp(int bookingId) throws SQLException {
		String sql = """
				SELECT b.updatedAt AS bookingUpdatedAt,
					bse.updatedAt AS sepaUpdatedAt,
					bad.updatedAt AS additionalUpdatedAt,
					bac.updatedAt AS creditcardUpdatedAt,
					baf.updatedAt AS foreignUpdatedAt,
					bfe.updatedAt AS feeUpdatedAt,
					bno.updatedAt AS noteUpdatedAt,
					bf.updatedAt AS fullUpdatedAt
				FROM booking b
				LEFT JOIN bookingAdditionalSepa bse ON bse.booking_id = b.id
				LEFT JOIN bookingAdditional bad ON bad.booking_id = b.id
				LEFT JOIN bookingAdditionalCreditcard bac ON bac.booking_id = b.id
				LEFT JOIN bookingAdditionalForeigncurrency baf ON baf.booking_id = b.id
				LEFT JOIN bookingFee bfe ON bfe.booking_id = b.id
				LEFT JOIN bookingAdditionalNote bno ON bno.booking_id = b.id
				JOIN bookingFull bf ON bf.id = b.id
				WHERE b.id = ?
				""";
		try (PreparedStatement ps = DBController.getConnection().prepareStatement(sql)) {
			ps.setInt(1, bookingId);
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next());
				String bookingUpdatedAt = rs.getString("bookingUpdatedAt");
				String sepaUpdatedAt = rs.getString("sepaUpdatedAt");
				String additionalUpdatedAt = rs.getString("additionalUpdatedAt");
				String creditcardUpdatedAt = rs.getString("creditcardUpdatedAt");
				String noteUpdatedAt = rs.getString("noteUpdatedAt");
				String foreignUpdatedAt = rs.getString("foreignUpdatedAt");
				String feeUpdatedAt = rs.getString("feeUpdatedAt");
				String newestUpdatedAt = newest(bookingUpdatedAt, sepaUpdatedAt, additionalUpdatedAt,
						creditcardUpdatedAt, noteUpdatedAt, foreignUpdatedAt, feeUpdatedAt);

				assertEquals(newestUpdatedAt, rs.getString("fullUpdatedAt"));
			}
		}
	}

	private static <T> QueryMeasurement<T> measureQueries(Supplier<T> operation) {
		Connection originalConnection = DBController.getConnection();
		AtomicInteger queryCount = new AtomicInteger();
		Connection countingConnection = (Connection) Proxy.newProxyInstance(
				DBControllerBookingTest.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, arguments) -> {
					Object result = invoke(originalConnection, method, arguments);
					if (result instanceof PreparedStatement preparedStatement) {
						return countingStatement(preparedStatement, queryCount);
					}
					return result;
				});
		DbConnectionHandler.connection = countingConnection;
		try {
			return new QueryMeasurement<>(operation.get(), queryCount.get());
		} finally {
			DbConnectionHandler.connection = originalConnection;
		}
	}

	private static PreparedStatement countingStatement(PreparedStatement statement, AtomicInteger queryCount) {
		return (PreparedStatement) Proxy.newProxyInstance(
				DBControllerBookingTest.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class },
				(proxy, method, arguments) -> {
					if ("executeQuery".equals(method.getName())) {
						queryCount.incrementAndGet();
					}
					return invoke(statement, method, arguments);
				});
	}

	private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
		try {
			return method.invoke(target, arguments);
		} catch (InvocationTargetException exception) {
			throw exception.getTargetException();
		}
	}

	private record QueryMeasurement<T>(T result, int queryCount) {
	}

	private static void assertBookingRelations(Booking booking, Recipient expectedRecipient, Category expectedCategory) {
		Recipient recipient = booking.getRecipient();
		assertNotNull(recipient);
		assertEquals(expectedRecipient.getId(), recipient.getId());
		assertEquals(expectedRecipient.getName(), recipient.getName());
		assertEquals(expectedRecipient.getIban(), recipient.getIban());
		assertEquals(expectedRecipient.getBic(), recipient.getBic());
		assertEquals(expectedRecipient.getAccountNumber(), recipient.getAccountNumber());
		assertEquals(expectedRecipient.getBlz(), recipient.getBlz());
		assertEquals(expectedRecipient.getBank(), recipient.getBank());
		assertEquals(expectedRecipient.getSource(), recipient.getSource());
		assertEquals(expectedRecipient.getNote(), recipient.getNote());
		assertEquals(expectedRecipient.isDefault(), recipient.isDefault());
		assertEquals(expectedRecipient.getUpdatedAt(), recipient.getUpdatedAt());

		Category category = booking.getCategory();
		assertNotNull(category);
		assertEquals(expectedCategory.getId(), category.getId());
		assertEquals(expectedCategory.getName(), category.getName());
		assertEquals(expectedCategory.getParentId(), category.getParentId());
		assertEquals(expectedCategory.getFullName(), category.getFullName());
		assertEquals(expectedCategory.getUpdatedAt(), category.getUpdatedAt());
	}

	private String newest(String first, String... others) {
		String newest = first;
		for (String candidate : others) {
			if (candidate != null && candidate.compareTo(newest) > 0) {
				newest = candidate;
			}
		}
		return newest;
	}

	private void assertBookingDetailCounts(int bookingId, int expectedSepaCount, int expectedAdditionalCount, int expectedNoteCount,
			int expectedCreditcardCount)
			throws SQLException {
		assertEquals(expectedSepaCount, countBookingDetailRows("bookingAdditionalSepa", bookingId));
		assertEquals(expectedAdditionalCount, countBookingDetailRows("bookingAdditional", bookingId));
		assertEquals(expectedNoteCount, countBookingDetailRows("bookingAdditionalNote", bookingId));
		assertEquals(expectedCreditcardCount, countBookingDetailRows("bookingAdditionalCreditcard", bookingId));
	}

	private int countBookingDetailRows(String tableName, int bookingId) throws SQLException {
		String sql = "SELECT COUNT(*) AS count FROM " + tableName + " WHERE booking_id = ?";
		try (PreparedStatement ps = DBController.getConnection().prepareStatement(sql)) {
			ps.setInt(1, bookingId);
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next());
				return rs.getInt("count");
			}
		}
	}

}
