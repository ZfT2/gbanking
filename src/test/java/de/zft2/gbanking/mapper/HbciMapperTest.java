package de.zft2.gbanking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kapott.hbci.structures.Konto;

import de.zft2.gbanking.db.dao.BankAccount;

class HbciMapperTest {

	@Test
	void mapKontoToBankAccount_shouldUseZeroWhenHbciAccountTypeIsMissing() {
		Konto konto = createKonto(null);

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(0, bankAccount.getHbciAccountType());
	}

	@Test
	void mapKontoToBankAccount_shouldUseZeroWhenHbciAccountTypeIsInvalid() {
		Konto konto = createKonto("Girokonto");

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(0, bankAccount.getHbciAccountType());
	}

	@Test
	void mapKontoToBankAccount_shouldPreserveNumericHbciAccountType() {
		Konto konto = createKonto("0001");

		BankAccount bankAccount = HbciMapper.mapKontoToBankAccount("Sparkasse", konto);

		assertEquals(1, bankAccount.getHbciAccountType());
	}

	private static Konto createKonto(String hbciAccountType) {
		Konto konto = new Konto();
		konto.country = "DE";
		konto.blz = "12345678";
		konto.number = "1234567890";
		konto.iban = "DE02123456781234567890";
		konto.bic = "TESTDEFFXXX";
		konto.name = "Max Mustermann";
		konto.type = "Girokonto";
		konto.acctype = hbciAccountType;
		konto.curr = "EUR";
		konto.allowedGVs = List.of();
		return konto;
	}
}
