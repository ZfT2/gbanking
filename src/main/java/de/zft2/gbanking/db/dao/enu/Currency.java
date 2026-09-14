package de.zft2.gbanking.db.dao.enu;

import java.util.Locale;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.exception.GBankingException;

public enum Currency implements IdType {

	EUR(1, 2),
	USD(2, 2),
	GBP(3, 2),
	CHF(4, 2),
	JPY(5, 0),
	CAD(6, 2),
	AUD(7, 2),
	CNY(8, 2),
	SEK(9, 2),
	NOK(10, 2),
	DKK(11, 2),
	PLN(12, 2),
	CZK(13, 2),
	HUF(14, 2),
	TRY(15, 2),
	NZD(16, 2),
	SGD(17, 2),
	HKD(18, 2),
	KRW(19, 0),
	INR(20, 2),
	BRL(21, 2),
	MXN(22, 2),
	ZAR(23, 2),
	RUB(24, 2),
	AED(25, 2),
	SAR(26, 2),
	ILS(27, 2),
	THB(28, 2),
	IDR(29, 2),
	MYR(30, 2),
	PHP(31, 2),
	TWD(32, 2),
	VND(33, 0),
	RON(34, 2),
	ISK(35, 0),
	UAH(36, 2),
	CLP(37, 0),
	COP(38, 2),
	ARS(39, 2),
	PEN(40, 2);

	private final int dbStateId;
	private final int minorUnitDigits;

	Currency(int dbStateId, int minorUnitDigits) {
		this.dbStateId = dbStateId;
		this.minorUnitDigits = minorUnitDigits;
	}

	public static Currency forInt(int intValue) {
		return IdType.forId(Currency.class, intValue);
	}

	public static Currency forCode(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		try {
			return valueOf(normalized);
		} catch (IllegalArgumentException ignored) {
			throw new GBankingException("Die Währung " + normalized + " wird noch nicht unterstützt.");
		}
	}

	public static Currency forCodeOrDefault(String value, Currency defaultCurrency) {
		Currency currency = forCode(value);
		return currency != null ? currency : defaultCurrency;
	}

	@Override
	public int getDbStateId() {
		return dbStateId;
	}

	public int getMinorUnitDigits() {
		return minorUnitDigits;
	}
}
