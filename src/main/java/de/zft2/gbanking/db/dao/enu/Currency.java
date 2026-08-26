package de.zft2.gbanking.db.dao.enu;

import java.util.Locale;

import de.zft2.gbanking.db.enu.IdType;
import de.zft2.gbanking.exception.GBankingException;

public enum Currency implements IdType {

	EUR(1),
	USD(2),
	GBP(3),
	CHF(4),
	JPY(5),
	CAD(6),
	AUD(7),
	CNY(8),
	SEK(9),
	NOK(10),
	DKK(11),
	PLN(12),
	CZK(13),
	HUF(14),
	TRY(15),
	NZD(16),
	SGD(17),
	HKD(18),
	KRW(19),
	INR(20),
	BRL(21),
	MXN(22),
	ZAR(23),
	RUB(24),
	AED(25),
	SAR(26),
	ILS(27),
	THB(28),
	IDR(29),
	MYR(30),
	PHP(31),
	TWD(32),
	VND(33),
	RON(34),
	ISK(35),
	UAH(36),
	CLP(37),
	COP(38),
	ARS(39),
	PEN(40);

	private final int dbStateId;

	Currency(int dbStateId) {
		this.dbStateId = dbStateId;
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
}
