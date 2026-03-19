package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.fp32xmlextract.data.Booking.SepaTyp;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.AccountState;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;

class DBControllerBookingTest extends DBControllerIntegrationBaseTest {

	// ------------------------------------------------------------
	// Tests - Booking insertion
	// ------------------------------------------------------------

	@Test
	void insertBooking_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking = TestData.createSampleBooking(acc.getId());
		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);

		assertEquals("Miete", booking.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking.getAmount());
		assertEquals("EUR", booking.getCurrency());
		assertEquals(BookingType.REMOVAL, booking.getBookingType());
		assertEquals(Source.ONLINE, booking.getSource());

		Calendar dateCurrentWithoutSeconds = getCalendarWithoutTime(Calendar.getInstance());
		Calendar dateBookingWithoutSeconds = getCalendarWithoutTime(booking.getDateBooking());
		Calendar dateValueWithoutSeconds = getCalendarWithoutTime(booking.getDateValue());
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
		booking.setSepaCustomerRef("Customer");
		booking.setSepaCreditorId("sepaCreditor");
		booking.setSepaEndToEnd("EndToEnd");
		booking.setSepaMandate("sepaMandate");
		booking.setSepaPersonId("sepaPersonId");
		booking.setSepaPurpose("sepaPurpose");
		booking.setSepaTyp(SepaTyp.BANK_TRANSFER_ONLINE);

		db.insertOrUpdate(booking);

		assertTrue(booking.getId() > 0);

		assertEquals("Miete", booking.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking.getAmount());
		assertEquals("EUR", booking.getCurrency());
		assertEquals(BookingType.REMOVAL, booking.getBookingType());
		assertEquals(Source.ONLINE, booking.getSource());
		Calendar dateCurrentWithoutSeconds = getCalendarWithoutTime(Calendar.getInstance());
		Calendar dateBookingWithoutSeconds = getCalendarWithoutTime(booking.getDateBooking());
		Calendar dateValueWithoutSeconds = getCalendarWithoutTime(booking.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertEquals("Customer", booking.getSepaCustomerRef());
		assertEquals("sepaCreditor", booking.getSepaCreditorId());
		assertEquals("EndToEnd", booking.getSepaEndToEnd());
		assertEquals("sepaMandate", booking.getSepaMandate());
		assertEquals("sepaPersonId", booking.getSepaPersonId());
		assertEquals("sepaPurpose", booking.getSepaPurpose());
		assertEquals(SepaTyp.BANK_TRANSFER_ONLINE, booking.getSepaTyp());
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
		//acc02 = db.getById(BankAccount.class, acc01.getId(), ResultType.FULL);

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
	void insertMultipleBookings01_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking01 = TestData.createSampleBooking(acc.getId());
		booking01.setSepaCustomerRef("Customer");
		booking01.setSepaCreditorId("sepaCreditor");
		booking01.setSepaEndToEnd("EndToEnd");
		booking01.setSepaMandate("sepaMandate");
		booking01.setSepaPersonId("sepaPersonId");
		booking01.setSepaPurpose("sepaPurpose");
		booking01.setSepaTyp(SepaTyp.BANK_TRANSFER_ONLINE);
		
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
		assertEquals("EUR", booking01.getCurrency());
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		Calendar dateCurrentWithoutSeconds = getCalendarWithoutTime(Calendar.getInstance());
		Calendar dateBookingWithoutSeconds = getCalendarWithoutTime(booking01.getDateBooking());
		Calendar dateValueWithoutSeconds = getCalendarWithoutTime(booking01.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertEquals("Customer", booking01.getSepaCustomerRef());
		assertEquals("sepaCreditor", booking01.getSepaCreditorId());
		assertEquals("EndToEnd", booking01.getSepaEndToEnd());
		assertEquals("sepaMandate", booking01.getSepaMandate());
		assertEquals("sepaPersonId", booking01.getSepaPersonId());
		assertEquals("sepaPurpose", booking01.getSepaPurpose());
		assertEquals(SepaTyp.BANK_TRANSFER_ONLINE, booking01.getSepaTyp());
		
		booking02 = findById(bookingList, booking02.getId());
		
		assertEquals("Kreditrate", booking02.getPurpose());
		assertEquals(new BigDecimal("400.00"), booking02.getAmount());
		assertEquals("EUR", booking02.getCurrency());
		assertEquals(BookingType.REMOVAL, booking02.getBookingType());
		assertEquals(Source.ONLINE, booking02.getSource());
	}
	
	@Test
	void insertMultipleBookings02_shouldWork() {

		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc);

		Booking booking01 = TestData.createSampleBooking(acc.getId());
		booking01.setSepaCustomerRef("Customer");
		booking01.setSepaCreditorId("sepaCreditor");
		booking01.setSepaEndToEnd("EndToEnd");
		booking01.setSepaMandate("sepaMandate");
		booking01.setSepaPersonId("sepaPersonId");
		booking01.setSepaPurpose("sepaPurpose");
		booking01.setSepaTyp(SepaTyp.BANK_TRANSFER_ONLINE);
		
		Booking booking02 = TestData.createSampleBooking2(acc.getId());

		Set<Booking> bookingSet = db.insertAll(new HashSet<>(Arrays.asList(booking01, booking02)));

		assertNotNull(bookingSet);
		assertTrue(booking01.getId() > 0);
		assertTrue(booking02.getId() > 0);
		assertNotEquals(booking01.getId(), booking02.getId());
	
		booking01 = findById(bookingSet, booking01.getId());

		assertEquals("Miete", booking01.getPurpose());
		assertEquals(new BigDecimal("1200.00"), booking01.getAmount());
		assertEquals("EUR", booking01.getCurrency());
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		Calendar dateCurrentWithoutSeconds = getCalendarWithoutTime(Calendar.getInstance());
		Calendar dateBookingWithoutSeconds = getCalendarWithoutTime(booking01.getDateBooking());
		Calendar dateValueWithoutSeconds = getCalendarWithoutTime(booking01.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);

		assertEquals("Customer", booking01.getSepaCustomerRef());
		assertEquals("sepaCreditor", booking01.getSepaCreditorId());
		assertEquals("EndToEnd", booking01.getSepaEndToEnd());
		assertEquals("sepaMandate", booking01.getSepaMandate());
		assertEquals("sepaPersonId", booking01.getSepaPersonId());
		assertEquals("sepaPurpose", booking01.getSepaPurpose());
		assertEquals(SepaTyp.BANK_TRANSFER_ONLINE, booking01.getSepaTyp());
		
		booking02 = findById(bookingSet, booking02.getId());
		
		assertEquals("Kreditrate", booking02.getPurpose());
		assertEquals(new BigDecimal("400.00"), booking02.getAmount());
		assertEquals("EUR", booking02.getCurrency());
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
		Booking booking01 = TestData.createBookingWithParams(acc01.getId(), recipient01.getId(), "Umbuchung auf TG", -500.00, BookingType.REMOVAL, Source.ONLINE);
		db.insertOrUpdate(booking01);
		booking01 = db.getByIdFull(Booking.class, booking01.getId());
		
		Recipient recipient02 = TestData.createRecipientWithParams("Max Mustermann", Source.ONLINE, "DE12345678001");
		db.insertOrUpdate(recipient02);
		Booking booking02 = TestData.createBookingWithParams(acc02.getId(), recipient02.getId(), "Umbuchung auf TG", 500.00, BookingType.DEPOSIT, Source.ONLINE);
		db.insertOrUpdate(booking02);
		booking02 = db.getByIdFull(Booking.class, booking02.getId());
		
		Booking rebookingToCheck = db.findCrossBooking(booking01);

		assertEquals(booking02.getPurpose(), rebookingToCheck.getPurpose());
		assertEquals(booking02.getAmount(), rebookingToCheck.getAmount()/*.multiply(new BigDecimal(-1))*/);
		assertEquals("EUR", rebookingToCheck.getCurrency());
		assertEquals(BookingType.REMOVAL, booking01.getBookingType());
		assertEquals(BookingType.DEPOSIT, booking02.getBookingType());
		assertEquals(BookingType.DEPOSIT, rebookingToCheck.getBookingType());
		assertEquals(Source.ONLINE, booking01.getSource());
		assertEquals(Source.ONLINE, rebookingToCheck.getSource());
		Calendar dateCurrentWithoutSeconds = getCalendarWithoutTime(Calendar.getInstance());
		Calendar dateBookingWithoutSeconds = getCalendarWithoutTime(rebookingToCheck.getDateBooking());
		Calendar dateValueWithoutSeconds = getCalendarWithoutTime(rebookingToCheck.getDateValue());
		assertEquals(dateCurrentWithoutSeconds, dateBookingWithoutSeconds);
		assertEquals(dateCurrentWithoutSeconds, dateValueWithoutSeconds);
	}

}