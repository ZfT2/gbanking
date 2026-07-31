package de.zft2.gbanking.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;

import java.time.Month;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class BookingCsvFormatTest {

	@Test
	void parseAmountShouldSupportGermanAndEnglishSeparators() {
		assertEquals(new BigDecimal("1234.56"), BookingCsvFormat.parseAmount("1.234,56"));
		assertEquals(new BigDecimal("1234.56"), BookingCsvFormat.parseAmount("1,234.56"));
		assertEquals(new BigDecimal("-12.50"), BookingCsvFormat.parseAmount("-12,5"));
		assertNull(BookingCsvFormat.parseAmount(null));
		assertNull(BookingCsvFormat.parseAmount("   "));
	}

	@Test
	void formatAmountShouldUseGermanDecimalSeparatorAndTwoDecimals() {
		assertEquals("1234,13", BookingCsvFormat.formatAmount(new BigDecimal("1234.125")));
		assertEquals("-12,50", BookingCsvFormat.formatAmount(new BigDecimal("-12.5")));
		assertNull(BookingCsvFormat.formatAmount(null));
	}

	@Test
	void parseDateShouldSupportIsoAndGermanDateFormats() {
		LocalDate date = LocalDate.of(2026, Month.APRIL, 10);

		assertEquals(date, BookingCsvFormat.parseDate("2026-04-10"));
		assertEquals(date, BookingCsvFormat.parseDate("10.04.2026"));
		assertEquals(date, BookingCsvFormat.parseDate("10.04.26"));
		assertNull(BookingCsvFormat.parseDate(null));
		assertNull(BookingCsvFormat.parseDate("   "));
	}

	@Test
	void textShouldTrimMappedValuesAndTreatBlankOrMissingHeadersAsNull() throws Exception {
		String csv = "KONTO_NAME;BETRAG\n  Girokonto  ;   \n";
		try (CSVParser parser = BookingCsvFormat.importFormat().parse(new StringReader(csv))) {
			CSVRecord csvRecord = parser.iterator().next();

			assertEquals("Girokonto", BookingCsvFormat.text(csvRecord, BookingCsvFormat.ACCOUNT_NAME));
			assertNull(BookingCsvFormat.text(csvRecord, BookingCsvFormat.AMOUNT));
			assertNull(BookingCsvFormat.text(csvRecord, BookingCsvFormat.RECIPIENT_BANK));
			assertNull(BookingCsvFormat.text(null, BookingCsvFormat.ACCOUNT_NAME));
		}
	}
}
