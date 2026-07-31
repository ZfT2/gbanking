package de.zft2.gbanking.file.imp;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

enum CreditcardCsvFormat {

	DETAILED(
			CreditcardCsvFormat.TRANSACTION_DATE,
			"Text",
			"Type",
			CreditcardCsvFormat.CURRENCY_AMOUNT,
			CreditcardCsvFormat.CURRENCY_RATE,
			CreditcardCsvFormat.CURRENCY,
			CreditcardCsvFormat.AMOUNT,
			CreditcardCsvFormat.MERCHANT_AREA,
			CreditcardCsvFormat.MERCHANT_CATEGORY,
			CreditcardCsvFormat.BOOK_DATE,
			CreditcardCsvFormat.VALUE_DATE),
	SIMPLE(
			CreditcardCsvFormat.DATE,
			CreditcardCsvFormat.DESCRIPTION,
			CreditcardCsvFormat.SIMPLE_VALUE_DATE,
			CreditcardCsvFormat.FROM_ACCOUNT,
			CreditcardCsvFormat.TO_ACCOUNT);

	static final String TRANSACTION_DATE = "TransactionDate";
	static final String TEXT = "Text";
	static final String TYPE = "Type";
	static final String CURRENCY_AMOUNT = "Currency Amount";
	static final String CURRENCY_RATE = "Currency Rate";
	static final String CURRENCY = "Currency";
	static final String AMOUNT = "Amount";
	static final String MERCHANT_AREA = "Merchant Area";
	static final String MERCHANT_CATEGORY = "Merchant Category";
	static final String BOOK_DATE = "BookDate";
	static final String VALUE_DATE = "ValueDate";

	static final String DATE = "Datum";
	static final String DESCRIPTION = "Beschreibung";
	static final String SIMPLE_VALUE_DATE = "Wertstellungsdatum";
	static final String FROM_ACCOUNT = "Aus dem Konto";
	static final String TO_ACCOUNT = "Auf das Konto";

	private final Set<String> requiredHeaders;

	CreditcardCsvFormat(String... requiredHeaders) {
		this.requiredHeaders = Set.copyOf(Arrays.asList(requiredHeaders));
	}

	static Optional<CreditcardCsvFormat> detect(Collection<String> headers) {
		for (CreditcardCsvFormat format : values()) {
			if (headers.containsAll(format.requiredHeaders)) {
				return Optional.of(format);
			}
		}
		return Optional.empty();
	}
}
