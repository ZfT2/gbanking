package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer;
import de.zft2.gbanking.file.imp.csv.CsvImportDefinitionRepository;
import de.zft2.gbanking.service.stock.PortfolioPerformanceExportService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceImportService.XmlImportAssignments;
import de.zft2.gbanking.service.stock.StockPortfolioService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;
import de.zft2.gbanking.testdata.TestDataFactory;

class PortfolioPerformanceFileServiceTest extends DBControllerIntegrationBaseTest {

	@TempDir
	Path files;

	@Test
	void csvFormatsShouldImportIdempotentlyAndExport() throws Exception {
		PortfolioSummary portfolio = createPortfolio();
		createSource();
		PortfolioPerformanceImportService importer = new PortfolioPerformanceImportService();
		Path securities = write("securities.csv", """
				ISIN;WKN;Ticker-Symbol;Wertpapiername;Währung;Notiz;Gesamtkostenquote (TER);Fondsgröße;Anbieter;Kaufgebühr (prozentual);Verwaltungsgebühr (prozentual)
				DE0000000001;ABC123;ABC.DE;Beispiel AG;EUR;;;;Beispielbank;;
				""");
		Path transactions = write("transactions.csv", transactionHeader()
				+ "2026-01-02T12:00;Kauf;102,00;EUR;;;;2,00;;10;DE0000000001;ABC123;ABC.DE;Beispiel AG;Erstkauf\n");
		Path accountTransactions = write("account.csv", transactionHeader()
				+ "2026-01-01T00:00;Einlage;500,00;EUR;;;;;;0;;;;;Start\n"
				+ "2026-01-02T12:00;Kauf;-102,00;EUR;;;;2,00;;10;DE0000000001;ABC123;ABC.DE;Beispiel AG;Erstkauf\n"
				+ "2026-03-01T00:00;Dividende;8,00;EUR;;;;;2,00;10;DE0000000001;ABC123;ABC.DE;Beispiel AG;Ausschüttung\n");
		Path prices = write("prices.csv", "Datum;DE0000000001\n2026-03-02;12,50\n");

		assertEquals(1, importer.importFile(securities, ExportType.STOCK_PP_SECURITIES_CSV, portfolio).securities());
		assertEquals(1, importer.importFile(transactions, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio).transactions());
		assertEquals(1, importer.importFile(accountTransactions, ExportType.STOCK_PP_ACCOUNT_TRANSACTIONS_CSV, portfolio).transactions());
		assertEquals(1, importer.importFile(prices, ExportType.STOCK_PP_PRICES_CSV, portfolio).prices());
		assertTrue(importer.importFile(transactions, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio).alreadyImported());

		StockPortfolioService portfolioService = new StockPortfolioService();
		assertEquals(0, new BigDecimal("10").compareTo(portfolioService.getPositions(portfolio.portfolioId()).get(0).quantity()));
		assertEquals(0, new BigDecimal("10").compareTo(portfolioService.getPositions(portfolio.portfolioId()).get(0).acquisitionPrice()));
		assertEquals(2, portfolioService.getTransactions(portfolio.portfolioId()).size());
		assertEquals(3, db.getAllByParent(Booking.class, portfolio.settlementAccountId()).size());

		PortfolioPerformanceExportService exporter = new PortfolioPerformanceExportService();
		Path exportedTransactions = files.resolve("export-transactions.csv");
		Path exportedAccounts = files.resolve("export-accounts.csv");
		Path exportedSecurities = files.resolve("export-securities.csv");
		Path exportedPrices = files.resolve("export-prices.csv");
		exporter.exportFile(exportedTransactions, ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio);
		exporter.exportFile(exportedAccounts, ExportType.STOCK_PP_ACCOUNT_TRANSACTIONS_CSV, portfolio);
		exporter.exportFile(exportedSecurities, ExportType.STOCK_PP_SECURITIES_CSV, portfolio);
		exporter.exportFile(exportedPrices, ExportType.STOCK_PP_PRICES_CSV, portfolio);

		assertTrue(Files.readString(exportedTransactions).contains("2026-01-02T12:00;Kauf;102,00;EUR"));
		assertTrue(Files.readString(exportedAccounts).contains("2026-03-01T00:00;Dividende;8,00;EUR"));
		assertTrue(Files.readString(exportedSecurities).contains("DE0000000001;ABC123;ABC.DE;Beispiel AG;EUR"));
		assertTrue(Files.readString(exportedPrices).contains("2026-03-02;12,5"));
		int securityId = db.getAll(StockSecurity.class).get(0).getId();
		int importedPriceId = portfolioService.getPrices(securityId).get(0).priceId();
		portfolioService.deletePrice(securityId, importedPriceId, true);
		Path pricesAfterDeletion = files.resolve("export-prices-after-deletion.csv");
		exporter.exportFile(pricesAfterDeletion, ExportType.STOCK_PP_PRICES_CSV, portfolio);
		assertFalse(Files.readString(pricesAfterDeletion).contains("2026-03-02;12,5"));
	}

	@Test
	void stockCsvImportShouldUseConfiguredColumnMappings() throws Exception {
		PortfolioSummary portfolio = createPortfolio();
		createSource();
		Path definitions = write("custom-csv-format.properties", """
				[Depotumsatz: Eigenes Format]
				Transaktion.Datum=Handelstag
				Transaktion.Typ=Vorgang
				Transaktion.Wert=Gesamtwert
				Transaktion.Buchungswaehrung=Devisen
				Transaktion.Stueck=Anzahl
				Wertpapier.Name=Titel
				Wertpapier.Isin=Kennung
				Format.DecimalSeparator=.
				Format.ThousandSeparator=,
				""");
		Path transactions = write("custom-transactions.csv", """
				Handelstag;Vorgang;Gesamtwert;Devisen;Anzahl;Titel;Kennung
				2026-05-02T10:30;Kauf;51.25;EUR;5;Konfiguriert AG;DE0000000099
				""");
		CsvImportAnalyzer analyzer = new CsvImportAnalyzer(new CsvImportDefinitionRepository(definitions));

		var result = new PortfolioPerformanceImportService(analyzer).importFile(transactions,
				ExportType.STOCK_PP_TRANSACTIONS_CSV, portfolio, "Depotumsatz: Eigenes Format");

		assertEquals(1, result.securities());
		assertEquals(1, result.transactions());
		assertEquals("Konfiguriert AG", db.getAll(StockSecurity.class).get(0).getName());
	}

	@Test
	void totalXmlShouldImportSecuritiesTransactionsAccountBookingsAndPrices() throws Exception {
		PortfolioSummary portfolio = createPortfolio();
		createSource();
		Path xml = write("portfolio.xml", """
				<client><version>70</version><securities><security><uuid>security-1</uuid><name>XML AG</name><currencyCode>EUR</currencyCode><isin>DE0000000002</isin><prices><price t="2026-04-02" v="1500000000"/></prices></security></securities><accounts><account><uuid>account-1</uuid><name>Konto</name><currencyCode>EUR</currencyCode><transactions><account-transaction><uuid>deposit-1</uuid><date>2026-04-01T00:00</date><currencyCode>EUR</currencyCode><amount>10000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction><account-transaction><uuid>dividend-1</uuid><date>2026-04-03T00:00</date><currencyCode>EUR</currencyCode><amount>800</amount><security reference="../../../../../securities/security"/><shares>1000000000</shares><units><unit type="TAX"><amount currency="EUR" amount="200"/></unit></units><type>DIVIDENDS</type></account-transaction></transactions></account></accounts><portfolios><portfolio><uuid>portfolio-1</uuid><name>Depot</name><transactions><portfolio-transaction><uuid>buy-1</uuid><date>2026-04-01T12:00</date><currencyCode>EUR</currencyCode><amount>10200</amount><security reference="../../../../securities/security"/><shares>1000000000</shares><units><unit type="FEE"><amount currency="EUR" amount="200"/></unit></units><type>BUY</type></portfolio-transaction></transactions></portfolio></portfolios></client>
				""");

		var result = new PortfolioPerformanceImportService().importFile(xml, ExportType.STOCK_PP_XML, portfolio);

		assertEquals(1, result.securities());
		assertEquals(2, result.transactions());
		assertEquals(3, result.bookings());
		assertEquals(1, result.prices());
		assertEquals("XML AG", db.getAll(StockSecurity.class).get(0).getName());
		assertEquals(0, new BigDecimal("10").compareTo(
				new StockPortfolioService().getPositions(portfolio.portfolioId()).get(0).quantity()));
	}

	@Test
	void totalXmlShouldPreviewAndOptionallyImportAccountsWithoutPortfolioRelation() throws Exception {
		PortfolioSummary portfolio = createPortfolio();
		createSource();
		Path xml = write("additional-accounts.xml", """
				<client><version>70</version><securities/><accounts>
				<account><uuid>settlement-1</uuid><name>Depotkonto</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>settlement-deposit</uuid><date>2026-01-01T00:00</date><currencyCode>EUR</currencyCode><amount>10000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				</transactions></account>
				<account><uuid>independent-1</uuid><name>Tagesgeld PP</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>extra-deposit</uuid><date>2026-01-02T00:00</date><currencyCode>EUR</currencyCode><amount>10000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				<account-transaction><uuid>extra-fee</uuid><date>2026-01-03T00:00</date><currencyCode>EUR</currencyCode><amount>500</amount><shares>0</shares><type>FEES</type></account-transaction>
				</transactions></account>
				</accounts><portfolios><portfolio><uuid>portfolio-1</uuid><name>Depot</name>
				<referenceAccount reference="../../../accounts/account"/><transactions/></portfolio></portfolios></client>
				""");
		PortfolioPerformanceImportService importer = new PortfolioPerformanceImportService();

		var preview = importer.previewXml(xml);
		assertEquals(1, preview.additionalAccounts().size());
		assertEquals("Tagesgeld PP", preview.additionalAccounts().get(0).name());

		var withoutAdditionalAccounts = importer.importFile(xml, ExportType.STOCK_PP_XML, portfolio, false);
		assertEquals(0, withoutAdditionalAccounts.accounts());
		assertEquals(2, db.getAll(BankAccount.class).size());

		var withAdditionalAccounts = importer.importFile(xml, ExportType.STOCK_PP_XML, portfolio, true);
		assertEquals(1, withAdditionalAccounts.accounts());
		assertEquals(2, withAdditionalAccounts.bookings());
		BankAccount imported = db.getAll(BankAccount.class).stream()
				.filter(account -> "PORTFOLIO_PERFORMANCE:ACCOUNT:independent-1".equals(account.getProviderAccountId()))
				.findFirst().orElseThrow();
		assertTrue(imported.isOfflineAccount());
		assertEquals(0, new BigDecimal("95.00").compareTo(imported.getBalance()));
		assertEquals(2, db.getAllByParent(Booking.class, imported.getId()).size());
	}

	@Test
	void totalXmlWithoutSelectionShouldCreateAllMappedPortfoliosAndAccounts() throws Exception {
		createSource();
		Path xml = write("multiple-portfolios.xml", """
				<client><version>70</version><securities/><accounts>
				<account><uuid>settlement-a</uuid><name>Verrechnung A</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>deposit-a</uuid><date>2026-01-01T00:00</date><currencyCode>EUR</currencyCode><amount>10000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				</transactions></account>
				<account><uuid>settlement-b</uuid><name>Verrechnung B</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>deposit-b</uuid><date>2026-02-01T00:00</date><currencyCode>EUR</currencyCode><amount>20000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				</transactions></account>
				<account><uuid>independent</uuid><name>Tagesgeld</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>deposit-extra</uuid><date>2026-03-01T00:00</date><currencyCode>EUR</currencyCode><amount>5000</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				</transactions></account>
				</accounts><portfolios>
				<portfolio><uuid>portfolio-a</uuid><name>Depot A</name><referenceAccount reference="../../../accounts/account"/><transactions/></portfolio>
				<portfolio><uuid>portfolio-b</uuid><name>Depot B</name><referenceAccount reference="../../../accounts/account[2]"/><transactions/></portfolio>
				</portfolios></client>
				""");
		PortfolioPerformanceImportService importer = new PortfolioPerformanceImportService();

		var preview = importer.previewXml(xml);
		assertEquals(2, preview.portfolios().size());
		assertEquals(3, preview.accounts().size());
		assertEquals(1, preview.additionalAccounts().size());
		XmlImportAssignments assignments = new XmlImportAssignments(
				Map.of("portfolio-a", 0, "portfolio-b", 0),
				Map.of("settlement-a", 0, "settlement-b", 0, "independent", 0));

		var result = importer.importFile(xml, ExportType.STOCK_PP_XML, null, assignments);

		assertEquals(2, result.portfolios());
		assertEquals(5, result.accounts());
		assertEquals(3, result.bookings());
		assertEquals(2, db.getAll(StockPortfolio.class).size());
		assertEquals(2, accountsOfType(AccountType.DEPOT).size());
		assertEquals(2, accountsOfType(AccountType.DEPOT_ACCOUNT).size());
		assertEquals(1, accountsOfType(AccountType.CURRENT_ACCOUNT).size());
		assertTrue(importer.previewXml(xml).portfolios().stream()
				.allMatch(portfolio -> portfolio.suggestedTargetId() != null));
	}

	@Test
	void totalXmlShouldMapPortfolioAndAccountToExistingTargets() throws Exception {
		PortfolioSummary portfolio = createPortfolio();
		createSource();
		Path xml = write("mapped-portfolio.xml", """
				<client><version>70</version><securities/><accounts>
				<account><uuid>settlement</uuid><name>Importkonto</name><currencyCode>EUR</currencyCode><transactions>
				<account-transaction><uuid>deposit</uuid><date>2026-01-01T00:00</date><currencyCode>EUR</currencyCode><amount>12500</amount><shares>0</shares><type>DEPOSIT</type></account-transaction>
				</transactions></account></accounts><portfolios>
				<portfolio><uuid>portfolio</uuid><name>Importdepot</name><referenceAccount reference="../../../accounts/account"/><transactions/></portfolio>
				</portfolios></client>
				""");
		XmlImportAssignments assignments = new XmlImportAssignments(
				Map.of("portfolio", portfolio.portfolioId()),
				Map.of("settlement", portfolio.settlementAccountId()));

		var result = new PortfolioPerformanceImportService()
				.importFile(xml, ExportType.STOCK_PP_XML, portfolio, assignments);

		assertEquals(0, result.portfolios());
		assertEquals(0, result.accounts());
		assertEquals(1, result.bookings());
		assertEquals(2, db.getAll(BankAccount.class).size());
		assertEquals(1, db.getAll(StockPortfolio.class).size());
		assertEquals(0, new BigDecimal("125.00").compareTo(
				db.getById(BankAccount.class, portfolio.settlementAccountId()).getBalance()));
	}

	private List<BankAccount> accountsOfType(AccountType type) {
		return db.getAll(BankAccount.class).stream().filter(account -> account.getAccountType() == type).toList();
	}

	private PortfolioSummary createPortfolio() {
		BankAccount depot = createAccount("Depot", AccountType.DEPOT);
		BankAccount settlement = createAccount("Verrechnung", AccountType.CURRENT_ACCOUNT);
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setId(100);
		portfolio.setAccountId(depot.getId());
		portfolio.setCurrentSettlementRelationId(200);
		portfolio.setOpenedAt(LocalDate.of(2026, 1, 1));
		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setId(200);
		relation.setPortfolioId(100);
		relation.setAccountId(settlement.getId());
		relation.setValidFrom(portfolio.getOpenedAt());
		db.executeInTransaction(() -> {
			db.insertOrUpdate(portfolio);
			db.insertOrUpdate(relation);
			return null;
		});
		return new StockPortfolioService().getPortfolios().get(0);
	}

	private BankAccount createAccount(String name, AccountType type) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName(name);
		account.setAccountType(type);
		account.setBaseCurrency(Currency.EUR);
		return db.insertOrUpdate(account);
	}

	private void createSource() {
		StockDataSource source = new StockDataSource();
		source.setId(3);
		source.setSourceCode("PORTFOLIO_PERFORMANCE");
		source.setSourceName("Portfolio Performance");
		source.setSourceType(StockDataSourceType.FILE);
		db.insertOrUpdate(source);
	}

	private Path write(String name, String content) throws Exception {
		Path file = files.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private static String transactionHeader() {
		return "Datum;Typ;Wert;Buchungswährung;Bruttobetrag;Währung Bruttobetrag;Wechselkurs;Gebühren;Steuern;Stück;ISIN;WKN;Ticker-Symbol;Wertpapiername;Notiz\n";
	}
}
