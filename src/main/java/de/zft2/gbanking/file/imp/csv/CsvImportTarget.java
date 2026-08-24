package de.zft2.gbanking.file.imp.csv;

import java.util.HashMap;
import java.util.Map;

public enum CsvImportTarget {

	ACCOUNT_NAME("Konto.Name"),
	ACCOUNT_IBAN("Konto.Iban"),
	ACCOUNT_NUMBER("Konto.Nummer"),
	ACCOUNT_BANK("Konto.Bank"),
	ACCOUNT_BIC("Konto.Bic"),
	DATE("Datum"),
	VALUE_DATE("Valuta"),
	AMOUNT("Betrag"),
	CURRENCY("Waehrung"),
	PURPOSE("Zweck"),
	RECIPIENT_NAME("Name"),
	RECIPIENT_IBAN("Iban"),
	RECIPIENT_BIC("Bic"),
	RECIPIENT_ACCOUNT_NUMBER("Empfaenger.Kontonummer"),
	RECIPIENT_BANK_CODE("Empfaenger.Blz"),
	RECIPIENT_BANK("Empfaenger.Bank"),
	BOOKING_TYPE("Buchungstyp"),
	MAIN_CATEGORY("Hauptkategorie"),
	SUB_CATEGORY("Unterkategorie"),
	SEPA_CUSTOMER_REFERENCE("Sepa.Kundenreferenz"),
	SEPA_CREDITOR_ID("Sepa.GlaeubigerId"),
	SEPA_END_TO_END("Sepa.EndToEnd"),
	SEPA_MANDATE("Sepa.Mandat"),
	SEPA_PERSON_ID("Sepa.PersonenId"),
	SEPA_PURPOSE("Sepa.Zweck"),
	SEPA_TYPE("Sepa.Typ"),
	CREDITCARD_TRANSACTION_DATE("Kreditkarte.Transaktionsdatum"),
	CREDITCARD_TYPE("Kreditkarte.Typ"),
	CREDITCARD_CURRENCY_AMOUNT("Kreditkarte.Waehrungsbetrag"),
	CREDITCARD_CURRENCY_RATE("Kreditkarte.Wechselkurs"),
	CREDITCARD_CURRENCY("Kreditkarte.Waehrung"),
	CREDITCARD_MERCHANT_AREA("Kreditkarte.Haendlerregion"),
	CREDITCARD_MERCHANT_CATEGORY("Kreditkarte.Haendlerkategorie");

	private static final Map<String, CsvImportTarget> TARGETS_BY_PROPERTY = createTargetMap();

	private final String propertyName;

	CsvImportTarget(String propertyName) {
		this.propertyName = propertyName;
	}

	public String getPropertyName() {
		return propertyName;
	}

	public static CsvImportTarget forPropertyName(String propertyName) {
		return propertyName != null ? TARGETS_BY_PROPERTY.get(propertyName.toLowerCase(java.util.Locale.ROOT)) : null;
	}

	public boolean isAccountIdentifier() {
		return this == ACCOUNT_NAME || this == ACCOUNT_IBAN || this == ACCOUNT_NUMBER;
	}

	private static Map<String, CsvImportTarget> createTargetMap() {
		Map<String, CsvImportTarget> targets = new HashMap<>();
		for (CsvImportTarget target : values()) {
			targets.put(target.propertyName.toLowerCase(java.util.Locale.ROOT), target);
		}
		return Map.copyOf(targets);
	}
}
