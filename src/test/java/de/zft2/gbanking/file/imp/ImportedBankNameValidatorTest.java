package de.zft2.gbanking.file.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.db.dao.enu.InstituteStatus;
import de.zft2.gbanking.db.dao.enu.Source;

class ImportedBankNameValidatorTest {

	private final ImportedBankNameValidator validator = new ImportedBankNameValidator(null);

	@Test
	void validate_shouldAcceptEquivalentDkbNamesAndReportDeutscheBank() {
		List<Institute> institutes = List.of(
				institute("12030000", "BYLADEM1001", "Deutsche Kreditbank Berlin AG", InstituteStatus.ACTIVE),
				institute("10070000", "DEUTDEFFXXX", "Deutsche Bank AG", InstituteStatus.ACTIVE),
				institute("37050198", "COLSDE33XXX", "Spk KölnBonn", InstituteStatus.ACTIVE));
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2024, 1, 1), "Deutsche Kreditbank", "12030000", "BYLADEM1001"),
				booking(2, LocalDate.of(2024, 1, 2), "Deutsche Kreditbank AG, Berlin", "12030000", "BYLADEM1001"),
				booking(3, LocalDate.of(2024, 1, 3), "Deutsche Kreditbank AG", "12030000", "BYLADEM1001"),
				booking(4, LocalDate.of(2024, 1, 4), "DKB AG", "12030000", "BYLADEM1001"),
				booking(5, LocalDate.of(2024, 1, 5), "Deutsche Bank", "12030000", "BYLADEM1001"));

		Booking unconfirmedEvidence = booking(10, LocalDate.of(2024, 1, 5), "Spk KölnBonn", "12030000", "BYLADEM1001");
		List<ImportedBankNameFinding> findings = validator.validate(imported, List.of(unconfirmedEvidence), institutes);

		assertEquals(1, findings.size());
		assertEquals(5, findings.get(0).bookingId());
		assertEquals("Testkonto 5", findings.get(0).accountName());
		assertEquals("Deutsche Bank", findings.get(0).currentBankName());
		assertTrue(findings.get(0).candidateBankNames().stream().noneMatch("Deutsche Bank"::equalsIgnoreCase));
		assertTrue(findings.get(0).candidateBankNames().stream().noneMatch("Spk KölnBonn"::equalsIgnoreCase));
	}

	@Test
	void validate_shouldNotReportUnknownNameWithoutConfidentAlternativeAssignment() {
		Booking imported = booking(1, LocalDate.of(2010, 1, 1), "Historische Regionalbank", "12030000", "BYLADEM1001");
		Institute dkb = institute("12030000", "BYLADEM1001", "Deutsche Kreditbank AG", InstituteStatus.ACTIVE);

		assertTrue(validator.validate(List.of(imported), List.of(), List.of(dkb)).isEmpty());
	}

	@Test
	void validate_shouldAcceptCommaAndPlaceVariants() {
		Institute postbank = institute("25010030", "PBNKDEFFXXX", "Postbank", "Hannover", InstituteStatus.ACTIVE);
		Institute nameUsedElsewhere = institute("99999999", "TESTDEFFXXX", "Postbank Hannover", InstituteStatus.ACTIVE);
		Booking officialName = booking(3, LocalDate.of(2024, 1, 3), "Postbank", "25010030", "PBNKDEFFXXX");
		Booking withoutComma = booking(1, LocalDate.of(2024, 1, 1), "Postbank Hannover", "25010030", "PBNKDEFFXXX");
		Booking withComma = booking(2, LocalDate.of(2024, 1, 2), "Postbank, Hannover", "25010030", "PBNKDEFFXXX");

		assertTrue(validator.validate(List.of(officialName, withoutComma), List.of(), List.of(postbank, nameUsedElsewhere)).isEmpty());
		assertTrue(validator.validate(List.of(officialName, withComma), List.of(), List.of(postbank, nameUsedElsewhere)).isEmpty());
	}

	@Test
	void validate_shouldReportRepeatedBankNameAssignedToAnotherBlz() {
		Institute dkb = institute("12030000", "BYLADEM1001", "Deutsche Kreditbank", "Berlin", InstituteStatus.ACTIVE);
		Institute akf = institute("33020000", "AKFBDE31XXX", "akf bank", "Wuppertal", InstituteStatus.ACTIVE);
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2013, 3, 1), "Deutsche Kreditbank", "12030000", null),
				booking(2, LocalDate.of(2013, 3, 1), "akf bank Wuppertal", "12030000", null),
				booking(3, LocalDate.of(2013, 4, 1), "akf bank, Wuppertal", "12030000", null),
				booking(4, LocalDate.of(2013, 5, 1), "akf bank Wuppertal", "12030000", null));

		List<ImportedBankNameFinding> findings = validator.validate(imported, List.of(), List.of(dkb, akf));

		assertEquals(3, findings.size());
		assertTrue(findings.stream().allMatch(finding -> "Deutsche Kreditbank".equals(finding.suggestedBankName())));
		assertTrue(findings.stream().flatMap(finding -> finding.candidateBankNames().stream())
				.noneMatch(name -> name.toLowerCase().contains("akf")));
	}

	@Test
	void validate_shouldAcceptHistoricalNamesFromSameBicLineage() {
		List<Institute> institutes = List.of(
				institute("31010833", "SCFBDE33XXX", "Openbank Deutschland", "Mönchengladbach", InstituteStatus.ACTIVE),
				institute("10120600", "SCFBDE33XXX", "Santander Consumer Bank", "Frankfurt", InstituteStatus.DUPLICATE),
				institute("79032038", "BSHADE71XXX", "MERKUR PRIVATBANK", "Hammelburg", InstituteStatus.ACTIVE),
				institute(null, "BSHADE71", "Bank Schilling & Co Aktiengesellschaft", "Hammelburg", InstituteStatus.ACTIVE));
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2008, 1, 1), "Santander Consumer Bank, Mönchengladbach", "31010833", null),
				booking(2, LocalDate.of(2008, 1, 2), "Bank Schilling & Co Hammelburg", "79032038", null));

		assertTrue(validator.validate(imported, List.of(), institutes).isEmpty());
	}

	@Test
	void validate_shouldAcceptHistoricalRegionalBankNamesForSamePlace() {
		List<Institute> institutes = List.of(
				institute("66190000", "GENODE61KA1", "Volksbank pur", "Karlsruhe", InstituteStatus.ACTIVE),
				institute("99999998", "TESTDEFF001", "Volksbank Karlsruhe", "Karlsruhe", InstituteStatus.DUPLICATE),
				institute("38060186", "GENODED1BRS", "Volksbank Köln Bonn", "Bonn Rhein-Sieg", InstituteStatus.ACTIVE),
				institute("99999999", "TESTDEFF002", "Volksbank Bonn Rhein-Sieg", "Bonn", InstituteStatus.DUPLICATE));
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2005, 1, 1), "Volksbank Karlsruhe", "66190000", null),
				booking(2, LocalDate.of(2005, 1, 2), "Volksbank Bonn Rhein-Sieg", "38060186", null));

		assertTrue(validator.validate(imported, List.of(), institutes).isEmpty());
	}

	@Test
	void validate_shouldAcceptHistoricalAndEquivalentBankNames() {
		List<Institute> institutes = List.of(
				institute(null, "RABONL2U", "RABOBANK NEDERLAND", "UTRECHT", InstituteStatus.ACTIVE),
				institute(null, "RABONL2X", "Coöperatieve Centrale Raiffeisen-Boerenleenbank B.A.", null,
						InstituteStatus.DUPLICATE),
				institute(null, "BFSWDE33HAN", "SozialBank", "Hannover", InstituteStatus.ACTIVE),
				institute(null, "BFSWDE33", "Bank für Sozialwirtschaft AG", null, InstituteStatus.DUPLICATE),
				institute("51220200", "ESSEDEFFXXX", "Skandinaviska Enskilda Banken", "Frankfurt", InstituteStatus.ACTIVE),
				institute("30120200", null, "SEB Merchant Banking", "Düsseldorf", InstituteStatus.DUPLICATE),
				institute("20130600", "BARCDEHAXXX", "BAWAG Niederlassung Deutschland", "Hamburg", InstituteStatus.ACTIVE),
				institute("20130600", null, "Barclays Bank Ireland Hamburg Branch", "Hamburg", InstituteStatus.DUPLICATE),
				institute("99999999", null, "Barclaycard Barclays Bank Hamburg", "Hamburg", InstituteStatus.DUPLICATE),
				institute(null, "WBAGDE61", "Oldenburgische Landesbank", "Oldenburg", InstituteStatus.ACTIVE),
				institute(null, "WUEHDE61", "Wüstenrot Bank Pfandbriefbank", "Ludwigsburg", InstituteStatus.DUPLICATE),
				institute(null, "CPDIDE51", "GE Capital Direkt", "Mainz", InstituteStatus.ACTIVE),
				institute(null, "CMCIDEFF", "TARGOBANK Direkt", "Mainz", InstituteStatus.DUPLICATE),
				institute("30020900", "CMCIDEDD", "TARGOBANK AG", "Düsseldorf", InstituteStatus.ACTIVE));
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2005, 1, 1), "Coöperatieve Centrale Raiffeisen-Boerenleenbank B.A.", null,
						"RABONL2UXXX"),
				booking(2, LocalDate.of(2005, 1, 2), "Bank für Sozialwirtschaft, Hannover", null, "BFSWDE33HAN"),
				booking(3, LocalDate.of(2005, 1, 3), "SEB Merchant Banking Frankfurt am Main", "51220200", null),
				booking(4, LocalDate.of(2005, 1, 4), "Barclaycard Barclays Bank Hamburg", "20130600", null),
				booking(5, LocalDate.of(2015, 1, 1), "Wüstenrot Bank Pfandbriefbank, Ludwigsburg", null, "WBAGDE61XXX"),
				booking(6, LocalDate.of(2017, 1, 1), "TARGOBANK Direkt, Mainz", null, "CPDIDE51"),
				booking(7, LocalDate.of(2025, 1, 1), "Oldenburgische Landesbank AG", null, "WBAGDE61XXX"),
				booking(8, LocalDate.of(2010, 1, 1), "GE Capital Direkt, Mainz", null, "CPDIDE51"));

		assertTrue(validator.validate(imported, List.of(), institutes).isEmpty());
	}

	@Test
	void validate_shouldAcceptKreissparkasseAbbreviation() {
		List<Institute> institutes = List.of(
				institute(null, "HEISDE66XXX", "Kreissparkasse Heilbronn", "Heilbronn", InstituteStatus.ACTIVE),
				institute(null, "COKSDE33XXX", "Kreissparkasse Köln", "Köln", InstituteStatus.ACTIVE),
				institute("38250110", "WELADED1EUS", "Kreissparkasse Euskirchen", "Euskirchen", InstituteStatus.ACTIVE));
		List<Booking> imported = List.of(
				booking(1, LocalDate.of(2010, 1, 1), "KSK Heilbronn", null, "HEISDE66XXX"),
				booking(2, LocalDate.of(2010, 1, 2), "KSK Köln", null, "COKSDE33XXX"),
				booking(3, LocalDate.of(2010, 1, 3), "KSK Euskirchen", "38250110", null));

		assertTrue(validator.validate(imported, List.of(), institutes).isEmpty());
	}

	@Test
	void validate_shouldAcceptHistoricalSparkasseNameVariant() {
		Institute current = institute("50850150", "HELADEF1DAS", "Sparkasse Darmstadt und Dieburg", "Darmstadt",
				InstituteStatus.ACTIVE);
		Institute archived = institute("50850150", "HELADEF1DAS", "Sparkasse Darmstadt", "Darmstadt", InstituteStatus.ARCHIVED);
		Institute nameUsedElsewhere = institute("99999999", "TESTDEFFXXX", "Stadt- und Kreis-Sparkasse Darmstadt",
				InstituteStatus.ACTIVE);
		Booking imported = booking(1, LocalDate.of(2010, 1, 1), "Stadt- und Kreis-Sparkasse Darmstadt", "50850150",
				"HELADEF1DAS");

		assertTrue(validator.validate(List.of(imported), List.of(), List.of(current, archived, nameUsedElsewhere)).isEmpty());
	}

	@Test
	void validate_shouldPreferHistoricallyClosestExistingBankName() {
		Institute santander = institute("37020600", "SCFBDE33XXX", "Santanderbank", InstituteStatus.ARCHIVED);
		Institute openbank = institute("37020600", "OPENDEFFXXX", "Openbank", InstituteStatus.ACTIVE);
		Institute deutscheBank = institute("10070000", "DEUTDEFFXXX", "Deutsche Bank", InstituteStatus.ACTIVE);
		Booking existingSantander = booking(10, LocalDate.of(2016, 6, 1), "Santanderbank", "37020600", "SCFBDE33XXX");
		Booking existingOpenbank = booking(11, LocalDate.of(2026, 6, 1), "Openbank", "37020600", "OPENDEFFXXX");
		Booking currentOpenbank = booking(20, LocalDate.of(2026, 6, 1), "Openbank", "37020600", "OPENDEFFXXX");
		Booking incorrect = booking(21, LocalDate.of(2018, 3, 1), "Deutsche Bank", "37020600", "SCFBDE33XXX");

		ImportedBankNameFinding finding = validator.validate(List.of(currentOpenbank, incorrect), List.of(existingSantander, existingOpenbank),
				List.of(santander, openbank, deutscheBank)).get(0);

		assertEquals("Santanderbank", finding.suggestedBankName());
		assertTrue(finding.candidateBankNames().containsAll(List.of("Santanderbank", "Openbank")));
	}

	@Test
	void validate_shouldUseBicAndAccountNumberFallbacksInFinding() {
		Institute dkb = institute(null, "BYLADEM1001", "Deutsche Kreditbank AG", InstituteStatus.ACTIVE);
		Institute deutscheBank = institute(null, "DEUTDEFFXXX", "Deutsche Bank AG", InstituteStatus.ACTIVE);
		Booking imported = booking(7, LocalDate.of(2025, 2, 3), "Deutsche Bank", null, "BYLADEM1001");
		imported.getRecipient().setIban(null);
		imported.getRecipient().setAccountNumber("1234567890");
		Booking valid = booking(6, LocalDate.of(2025, 2, 2), "Deutsche Kreditbank AG", null, "BYLADEM1001");

		ImportedBankNameFinding finding = validator.validate(List.of(valid, imported), List.of(), List.of(dkb, deutscheBank)).get(0);

		assertEquals("1234567890", finding.accountIdentifier());
		assertEquals("BYLADEM1001", finding.bankIdentifier());
	}

	@Test
	void validate_shouldUseKnownBicWhenBlzIsUnknown() {
		Institute dkb = institute(null, "BYLADEM1001", "Deutsche Kreditbank AG", InstituteStatus.ACTIVE);
		Institute deutscheBank = institute(null, "DEUTDEFFXXX", "Deutsche Bank AG", InstituteStatus.ACTIVE);
		Booking imported = booking(8, LocalDate.of(2025, 2, 3), "Deutsche Bank", "99999999", "BYLADEM1001");
		Booking valid = booking(6, LocalDate.of(2025, 2, 2), "Deutsche Kreditbank AG", "99999999", "BYLADEM1001");

		ImportedBankNameFinding finding = validator.validate(List.of(valid, imported), List.of(), List.of(dkb, deutscheBank)).get(0);

		assertEquals("99999999", finding.bankIdentifier());
		assertEquals("Deutsche Kreditbank AG", finding.suggestedBankName());
	}

	private static Booking booking(int id, LocalDate date, String bankName, String blz, String bic) {
		Recipient recipient = new Recipient("Max Mustermann", "DE00123456789012345678", bic, "1234567890", blz, bankName, Source.IMPORT);
		Booking booking = new Booking();
		booking.setId(id);
		booking.setAccountName("Testkonto " + id);
		booking.setDateBooking(date);
		booking.setPurpose("Testbuchung " + id);
		booking.setAmount(BigDecimal.valueOf(id));
		booking.setRecipient(recipient);
		return booking;
	}

	private static Institute institute(String blz, String bic, String bankName, InstituteStatus status) {
		return institute(blz, bic, bankName, null, status);
	}

	private static Institute institute(String blz, String bic, String bankName, String place, InstituteStatus status) {
		Institute institute = new Institute();
		institute.setBlz(blz);
		institute.setBic(bic);
		institute.setBankName(bankName);
		institute.setPlace(place);
		institute.setStateType(status);
		return institute;
	}
}
