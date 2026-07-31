package de.zft2.gbanking.file;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.util.TypeConverter;

public final class BookingCsvFormat {

	public static final String ACCOUNT_NAME = "KONTO_NAME";
	public static final String ACCOUNT_IBAN = "KONTO_IBAN";
	public static final String ACCOUNT_NUMBER = "KONTO_NR";
	public static final String ACCOUNT_BANK = "KONTO_BANK";
	public static final String ACCOUNT_BIC = "KONTO_BIC";
	public static final String DATE_BOOKING = "BUCHUNGSDATUM";
	public static final String DATE_VALUE = "WERTSTELLUNG";
	public static final String AMOUNT = "BETRAG";
	public static final String CURRENCY = "WAEHRUNG";
	public static final String PURPOSE = "VERWENDUNGSZWECK";
	public static final String RECIPIENT_NAME = "EMPFAENGER_NAME";
	public static final String RECIPIENT_IBAN = "EMPFAENGER_IBAN";
	public static final String RECIPIENT_BIC = "EMPFAENGER_BIC";
	public static final String RECIPIENT_ACCOUNT_NUMBER = "EMPFAENGER_KONTONR";
	public static final String RECIPIENT_BLZ = "EMPFAENGER_BLZ";
	public static final String RECIPIENT_BANK = "EMPFAENGER_BANK";
	public static final String BOOKING_TYPE = "BUCHUNGSTYP";
	public static final String CATEGORY = "KATEGORIE";
	public static final String SEPA_CUSTOMER_REF = "SEPA_CUSTOMER_REF";
	public static final String SEPA_CREDITOR_ID = "SEPA_CREDITOR_ID";
	public static final String SEPA_END_TO_END = "SEPA_END_TO_END";
	public static final String SEPA_MANDATE = "SEPA_MANDATE";
	public static final String SEPA_PERSON_ID = "SEPA_PERSON_ID";
	public static final String SEPA_PURPOSE = "SEPA_PURPOSE";
	public static final String SEPA_TYPE = "SEPA_TYPE";

	protected static final String[] HEADERS = {
			ACCOUNT_NAME,
			ACCOUNT_IBAN,
			ACCOUNT_NUMBER,
			ACCOUNT_BANK,
			ACCOUNT_BIC,
			DATE_BOOKING,
			DATE_VALUE,
			AMOUNT,
			CURRENCY,
			PURPOSE,
			RECIPIENT_NAME,
			RECIPIENT_IBAN,
			RECIPIENT_BIC,
			RECIPIENT_ACCOUNT_NUMBER,
			RECIPIENT_BLZ,
			RECIPIENT_BANK,
			BOOKING_TYPE,
			CATEGORY,
			SEPA_CUSTOMER_REF,
			SEPA_CREDITOR_ID,
			SEPA_END_TO_END,
			SEPA_MANDATE,
			SEPA_PERSON_ID,
			SEPA_PURPOSE,
			SEPA_TYPE
	};

	private BookingCsvFormat() {
	}

	public static CSVFormat exportFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(HEADERS).get();
	}

	public static CSVFormat importFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).setTrim(true).get();
	}

	public static String text(CSVRecord csvRecord, String header) {
		if (csvRecord == null || !csvRecord.isMapped(header)) {
			return null;
		}
		return trimToNull(csvRecord.get(header));
	}

	public static String formatDate(LocalDate date) {
		return TypeConverter.toDateStringShort(date);
	}

	public static LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim());
		} catch (Exception e) {
			return TypeConverter.toLocalDateFromDateStrFlexible(value.trim());
		}
	}

	public static String formatAmount(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return String.format(Locale.GERMANY, "%.2f", value.setScale(2, RoundingMode.HALF_UP));
	}

	public static BigDecimal parseAmount(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String normalized = normalizeAmount(value.trim());
		return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
	}

	private static String normalizeAmount(String value) {
		if (value.contains(",") && value.contains(".")) {
			return value.lastIndexOf(',') > value.lastIndexOf('.') ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
		}
		return value.contains(",") ? value.replace(',', '.') : value;
	}
}
