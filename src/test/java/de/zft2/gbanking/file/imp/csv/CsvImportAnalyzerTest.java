package de.zft2.gbanking.file.imp.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Analysis;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Match;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Problem;

class CsvImportAnalyzerTest {

	@TempDir
	Path tempDir;

	@Test
	void repositoryShouldLoadBundledDefinitionsWithoutCreatingUserFile() {
		Path definitionFile = tempDir.resolve("properties/import/csv.properties");

		List<CsvImportDefinition> definitions = new CsvImportDefinitionRepository(definitionFile).load();

		assertEquals(List.of("Buchung: GBanking", "Buchung: Kreditkarte Instabank", "Buchung: Kreditkarte Bank Norwegian"),
				definitions.stream().limit(3).map(CsvImportDefinition::getName).toList());
		assertEquals(List.of("Buchung: VR-NetWorld", "Buchung: DKB Web", "Buchung: N26 Deutsch", "Buchung: N26 English",
				"Buchung: Commerzbank Web", "Buchung: Commerzbank Web erweitert", "Buchung: Atruvia VR-Banken und GLS",
				"Buchung: Sparda-Bank West", "Buchung: Postbank Web", "Buchung: Vivid"),
				definitions.stream().skip(3).map(CsvImportDefinition::getName).toList());
		assertFalse(Files.exists(definitionFile));
	}

	@Test
	void bundledDefinitionsShouldRecognizeDocumentedFormats() throws Exception {
		assertBundledFormat("Buchung: VR-NetWorld", """
				Kontoname;Kontonummer;Bankleitzahl;Datum;Valuta;Zahlungspflichtiger/-empfänger;ZP/ZE Konto/IBAN;ZP/ZE Bankleitzahl/BIC;Verwendungszweck;Kategorie;Betrag;Währung
				Girokonto;123456;10020000;20.08.2026;20.08.2026;Beispiel GmbH;DE02120300000000202051;BYLADEM1001;Rechnung;Wohnen;-12,34;EUR
				""");
		assertBundledFormat("Buchung: DKB Web", """
				"Girokonto";"DE02120300000000202051"
				"Kontostand vom 20.08.2026:";"1.234,56 €"
				""
				"Buchungsdatum";"Wertstellung";"Status";"Zahlungspflichtige*r";"Zahlungsempfänger*in";"Verwendungszweck";"Umsatztyp";"IBAN";"Betrag (€)";"Gläubiger-ID";"Mandatsreferenz";"Kundenreferenz"
				"20.08.26";"20.08.26";"Gebucht";"Beispiel GmbH";"Max Mustermann";"Rechnung";"Ausgang";"DE02120300000000202051";"-12,34";"";"";"REF-1"
				""");
		assertBundledFormat("Buchung: N26 Deutsch", """
				"Datum","Empfänger","Kontonummer","Transaktionstyp","Verwendungszweck","Kategorie","Betrag (EUR)","Betrag (Fremdwährung)","Fremdwährung","Wechselkurs"
				"2026-08-20","Beispiel GmbH","DE02120300000000202051","Lastschrift","Rechnung","Shopping","-12.34","","",""
				""");
		assertBundledFormat("Buchung: N26 English", """
				"Date","Payee","Account number","Transaction type","Payment reference","Category","Amount (EUR)","Amount (Foreign Currency)","Type Foreign Currency","Exchange Rate"
				"2026-08-20","Example Ltd","DE02120300000000202051","Outgoing Transfer","Invoice","Shopping","-12.34","","",""
				""");
		assertBundledFormat("Buchung: Commerzbank Web", """
				Buchungstag;Wertstellung;Umsatzart;Buchungstext;Betrag;Währung;IBAN Kontoinhaber;Kategorie
				20.08.2026;20.08.2026;Überweisung;Rechnung;-12,34;EUR;DE02120300000000202051;Geschäftlich
				""");
		assertBundledFormat("Buchung: Commerzbank Web erweitert", """
				Buchungstag;Wertstellung;Umsatzart;Buchungstext;Betrag;Währung;IBAN Kontoinhaber;Kategorie;Sender;Empfänger;Verwendungszweck
				20.08.2026;20.08.2026;Überweisung;Rechnung;-12,34;EUR;DE02120300000000202051;Geschäftlich;Max Mustermann;Beispiel GmbH;Rechnung 123
				""");
		assertBundledFormat("Buchung: Atruvia VR-Banken und GLS", atruviaCsv("Waehrung", "Glaeubiger ID"));
		assertBundledFormat("Buchung: Sparda-Bank West", atruviaCsv("Währung", "Gläubiger ID"));
		assertBundledFormat("Buchung: Postbank Web", """
				Umsätze
				Konto;Girokonto
				IBAN;DE02120300000000202051
				Währung;EUR
				Zeitraum;01.08.2026 - 20.08.2026
				Kontostand;1.234,56
				Nur gebuchte Umsätze
				Buchungstag;Wert;Umsatzart;Begünstigter / Auftraggeber;Verwendungszweck;IBAN / Kontonummer;BIC;Kundenreferenz;Mandatsreferenz;Gläubiger ID;Fremde Gebühren;Betrag;Abweichender Empfänger;Anzahl der Aufträge;Anzahl der Schecks;Soll;Haben;Währung
				20.8.2026;20.8.2026;Überweisung;Beispiel GmbH;Rechnung;DE02120300000000202051;BYLADEM1001;REF-1;;;;12,34;;;;12,34;;EUR
				""");
		assertBundledFormat("Buchung: Vivid", """
				Completed date,Counterparty name,Reference,Payment amount,Payment currency
				20.08.2026,Example Ltd,Invoice,-12.34,EUR
				""");
	}

	@Test
	void repositoryShouldAddUserDefinitionsToBundledDefaults() throws Exception {
		Path definitionFile = tempDir.resolve("csv.properties");
		Files.writeString(definitionFile, """
				[Buchung: Eigene Bank]
				Betrag=Umsatz
				Zweck=Beschreibung
				""".stripIndent(), StandardCharsets.UTF_8);

		List<CsvImportDefinition> definitions = new CsvImportDefinitionRepository(definitionFile).load();

		assertEquals(14, definitions.size());
		assertEquals("Buchung: Eigene Bank", definitions.get(definitions.size() - 1).getName());
	}

	@Test
	void repositoryShouldPreferUserDefinitionWithSameName() throws Exception {
		Path definitionFile = tempDir.resolve("csv.properties");
		Files.writeString(definitionFile, """
				[Buchung: GBanking]
				Betrag=Eigener Betrag
				Zweck=Eigener Zweck
				""".stripIndent(), StandardCharsets.UTF_8);

		CsvImportDefinition definition = new CsvImportDefinitionRepository(definitionFile).load().stream()
				.filter(candidate -> "Buchung: GBanking".equals(candidate.getName())).findFirst().orElseThrow();

		assertEquals(List.of("Eigener Betrag"), definition.getSourceFields(CsvImportTarget.AMOUNT));
		assertEquals(List.of("Eigener Zweck"), definition.getSourceFields(CsvImportTarget.PURPOSE));
		assertEquals(List.of(), definition.getSourceFields(CsvImportTarget.DATE));
	}

	@Test
	void analyzerShouldAcceptCopiedMoneyplexDefinition() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: Moneyplex Bank]
				Datum=Buchungstag
				Valuta=Wertstellungstag
				Betrag=Umsatz
				Waehrung=Waehrung
				Zweck=Verwendungszweck;Buchungstext
				Name=Beguenstigter/Zahlungspflichtiger
				Iban=Kontonummer/IBAN
				Bic=BLZ/BIC
				Hauptkategorie=Kategorie
				Unterkategorie=Unterkategorie
				Format.DecimalSeparator=,
				Format.ThousandSeparator=.
				Default.Waehrung=EUR
				Extra.NoZweckColumnNames=1
				""");
		Path csv = csv("""
				Buchungstag;Wertstellungstag;Umsatz;Waehrung;Verwendungszweck;Buchungstext;Beguenstigter/Zahlungspflichtiger;Kontonummer/IBAN;BLZ/BIC;Kategorie;Unterkategorie
				20.08.2026;21.08.2026;-1.234,56;EUR;Rechnung;August;Beispiel GmbH;DE02120300000000202051;BYLADEM1001;Wohnen;Nebenkosten
				""");

		Analysis analysis = analyzer.analyze(csv);

		assertNull(analysis.problem());
		assertEquals(1, analysis.matches().size());
		Match match = analysis.matches().get(0);
		assertEquals("Buchung: Moneyplex Bank", match.definition().getName());
		assertEquals(Set.of(), match.missingRequiredHeaders());
		assertEquals(Set.of(), match.missingOptionalHeaders());
		assertEquals(Set.of(), match.unknownHeaders());
	}

	@Test
	void analyzerShouldSupportMoneyplexExtraHeaderWithoutExplicitMappings() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: CSV-Import Targobank]
				Extra.Header=Datum;Zweck;Betrag;;;Iban;Bic
				Format.DecimalSeparator=,
				Format.ThousandSeparator=.
				Extra.Separator=,
				Default.Waehrung=EUR
				""");
		Path csv = csv("20.08.2026,Test,\"-12,34\",,,DE02120300000000202051,BYLADEM1001");

		Analysis analysis = analyzer.analyze(csv);
		Match match = analysis.matches().get(0);
		CsvImportData data = analyzer.read(csv, match.definition());

		assertNull(analysis.problem());
		assertEquals("-12,34", data.rows().get(0).text("Betrag"));
		assertEquals("DE02120300000000202051", data.rows().get(0).text("Iban"));
	}

	@Test
	void analyzerShouldIgnoreDefinitionsThatCannotParseTheFile() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: Headerless]
				Extra.Header=Betrag

				[Buchung: Matching]
				Betrag=Amount
				Zweck=Purpose
				""");
		Path csv = csv("Amount;Purpose" + System.lineSeparator() + "12,34;Test");

		Analysis analysis = analyzer.analyze(csv);

		assertNull(analysis.problem());
		assertEquals("Buchung: Matching", analysis.matches().get(0).definition().getName());
	}

	@Test
	void analyzerShouldReportUnknownAndMissingOptionalColumnsSeparately() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: Minimal]
				Datum=Datum
				Betrag=Betrag
				Zweck=Beschreibung
				""");
		Path csv = csv("Datum;Betrag;Zusatz" + System.lineSeparator() + "20.08.2026;10,00;X");

		Match match = analyzer.analyze(csv).matches().get(0);

		assertEquals(Set.of("Zusatz"), match.unknownHeaders());
		assertEquals(Set.of("Beschreibung"), match.missingOptionalHeaders());
	}

	@Test
	void analyzerShouldReportMissingDatabaseRequiredAmountColumn() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: Minimal]
				Datum=Datum
				Betrag=Betrag
				Zweck=Beschreibung
				""");

		Analysis analysis = analyzer.analyze(csv("Datum;Beschreibung" + System.lineSeparator() + "20.08.2026;Test"));

		assertEquals(Problem.MISSING_REQUIRED_FIELDS, analysis.problem());
		assertEquals(Set.of("Betrag"), analysis.matches().get(0).missingRequiredHeaders());
	}

	@Test
	void analyzerShouldReportUnknownDefinitionWithoutHeaderOverlap() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: Minimal]
				Betrag=Betrag
				""");

		Analysis analysis = analyzer.analyze(csv("Foo;Bar" + System.lineSeparator() + "1;2"));

		assertEquals(Problem.UNKNOWN_DEFINITION, analysis.problem());
		assertEquals(List.of(), analysis.matches());
	}

	@Test
	void analyzerShouldReportAllLikelyDefinitionsWithMissingRequiredColumns() throws Exception {
		CsvImportAnalyzer analyzer = analyzer("""
				[Buchung: First]
				Datum=CustomDatum
				Betrag=Haben

				[Buchung: Second]
				Datum=CustomDatum
				Betrag=Soll
				""");

		Analysis analysis = analyzer.analyze(csv("CustomDatum" + System.lineSeparator() + "20.08.2026"));

		assertEquals(Problem.MISSING_REQUIRED_FIELDS, analysis.problem());
		assertEquals(List.of("Buchung: First", "Buchung: Second"),
				analysis.matches().stream().map(match -> match.definition().getName()).toList());
	}

	private CsvImportAnalyzer analyzer(String definitions) throws Exception {
		Path definitionFile = tempDir.resolve("csv-" + System.nanoTime() + ".properties");
		Files.writeString(definitionFile, definitions.stripIndent(), StandardCharsets.UTF_8);
		return new CsvImportAnalyzer(new CsvImportDefinitionRepository(definitionFile), new CsvImportDataReader());
	}

	private void assertBundledFormat(String expectedDefinition, String content) throws Exception {
		Path definitionFile = tempDir.resolve("missing-user-definitions.properties");
		Analysis analysis = new CsvImportAnalyzer(new CsvImportDefinitionRepository(definitionFile)).analyze(csv(content));

		assertNull(analysis.problem(), expectedDefinition);
		assertEquals(List.of(expectedDefinition), analysis.matches().stream().map(match -> match.definition().getName()).toList());
		assertEquals(Set.of(), analysis.matches().get(0).missingOptionalHeaders(), expectedDefinition);
		assertEquals(Set.of(), analysis.matches().get(0).unknownHeaders(), expectedDefinition);
	}

	private String atruviaCsv(String currencyHeader, String creditorHeader) {
		return """
				Bezeichnung Auftragskonto;IBAN Auftragskonto;BIC Auftragskonto;Bankname Auftragskonto;Buchungstag;Valutadatum;Name Zahlungsbeteiligter;IBAN Zahlungsbeteiligter;BIC (SWIFT-Code) Zahlungsbeteiligter;Buchungstext;Verwendungszweck;Betrag;%s;Saldo nach Buchung;Bemerkung;Gekennzeichneter Umsatz;%s;Mandatsreferenz
				Girokonto;DE02120300000000202051;BYLADEM1001;Beispielbank;20.08.2026;20.08.2026;Beispiel GmbH;DE02120300000000202052;BYLADEM1002;Lastschrift;Rechnung;-12,34;EUR;987,66;;;DE98ZZZ09999999999;MANDAT-1
				""".formatted(currencyHeader, creditorHeader);
	}

	private Path csv(String content) throws Exception {
		Path csvFile = tempDir.resolve("import-" + System.nanoTime() + ".csv");
		Files.writeString(csvFile, content.stripIndent(), StandardCharsets.UTF_8);
		return csvFile;
	}
}
