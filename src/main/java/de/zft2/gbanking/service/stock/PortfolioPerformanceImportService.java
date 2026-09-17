package de.zft2.gbanking.service.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockImportRecordStatus;
import de.zft2.gbanking.db.dao.enu.StockImportStatus;
import de.zft2.gbanking.db.dao.enu.StockNumericValueType;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionEditField;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockImportBatch;
import de.zft2.gbanking.db.dao.stock.StockImportRecord;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionMetadata;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Analysis;
import de.zft2.gbanking.file.imp.csv.CsvImportAnalyzer.Match;
import de.zft2.gbanking.file.imp.csv.CsvImportData;
import de.zft2.gbanking.file.imp.csv.CsvImportDefinition;
import de.zft2.gbanking.file.imp.csv.CsvImportDefinitionType;
import de.zft2.gbanking.file.imp.csv.CsvImportTarget;
import de.zft2.gbanking.file.imp.csv.CsvImportValueParser;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Document;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Account;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Price;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Portfolio;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Security;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Transaction;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;

public class PortfolioPerformanceImportService extends AbstractDbService {

	private static final String SOURCE_CODE = "PORTFOLIO_PERFORMANCE";
	private static final String IMPORTER_KEY = "PORTFOLIO_PERFORMANCE";
	private static final String IMPORTER_VERSION = "1";
	private static final int QUANTITY_SCALE = StockNumericValueType.QUANTITY.getScaleDigits();
	private static final int PRICE_SCALE = StockNumericValueType.PRICE.getScaleDigits();
	private final CsvImportAnalyzer csvImportAnalyzer;

	public PortfolioPerformanceImportService() {
		this(new CsvImportAnalyzer());
	}

	public PortfolioPerformanceImportService(CsvImportAnalyzer csvImportAnalyzer) {
		this.csvImportAnalyzer = Objects.requireNonNull(csvImportAnalyzer);
	}

	public ImportResult importFile(Path file, ExportType importType, PortfolioSummary portfolio) throws IOException {
		return importFile(file, importType, portfolio, false);
	}

	public ImportResult importFile(Path file, ExportType importType, PortfolioSummary portfolio,
			boolean importAdditionalAccounts) throws IOException {
		return importFile(file, importType, portfolio, importAdditionalAccounts, null, null);
	}

	public ImportResult importFile(Path file, ExportType importType, PortfolioSummary portfolio,
			XmlImportAssignments assignments) throws IOException {
		return importFile(file, importType, portfolio, false, assignments, null);
	}

	public ImportResult importFile(Path file, ExportType importType, PortfolioSummary portfolio,
			String csvDefinitionName) throws IOException {
		return importFile(file, importType, portfolio, false, null, csvDefinitionName);
	}

	private ImportResult importFile(Path file, ExportType importType, PortfolioSummary portfolio,
			boolean importAdditionalAccounts, XmlImportAssignments assignments, String csvDefinitionName) throws IOException {
		if (portfolio != null || importType != ExportType.STOCK_PP_XML) {
			requirePortfolio(portfolio);
		}
		byte[] content = Files.readAllBytes(file);
		String contentHash = hash(content, portfolio != null ? portfolio.portfolioId() : 0,
				importAdditionalAccounts, assignments, importType.name() + ":" + nullToEmpty(csvDefinitionName));
		StockDataSource source = requireSource();
		if (isAlreadyImported(source.getId(), contentHash)) {
			return new ImportResult(0, 0, 0, 0, 0, 0, true);
		}

		return dbController.executeInTransaction(() -> {
			StockImportBatch batch = createBatch(source, file, importType, contentHash);
			StockImportRecord record = createRecord(batch, importType, contentHash);
			Counters counters = new Counters();
			try {
				importByType(file, importType, portfolio, source, record, counters,
						importAdditionalAccounts, assignments, csvDefinitionName);
				batch.setImportStatus(StockImportStatus.IMPORTED);
				batch.setCompletedAt(LocalDateTime.now());
				dbController.insertOrUpdate(batch);
				return counters.result(false);
			} catch (RuntimeException | IOException exception) {
				throw new GBankingException("Portfolio-Performance-Import fehlgeschlagen: " + exception.getMessage(), exception);
			}
		});
	}

	private void importByType(Path file, ExportType importType, PortfolioSummary portfolio, StockDataSource source,
			StockImportRecord record, Counters counters, boolean importAdditionalAccounts,
			XmlImportAssignments assignments, String csvDefinitionName) throws IOException {
		switch (importType) {
		case STOCK_PP_XML -> importDocument(new PortfolioPerformanceXmlReader().read(file), portfolio, source, record,
				counters, new PriceImportCache(), importAdditionalAccounts, assignments);
		case STOCK_PP_TRANSACTIONS_CSV -> importTransactions(readTransactions(file, csvDefinition(file,
				CsvImportDefinitionType.STOCK_PORTFOLIO_TRANSACTION, csvDefinitionName)), false, portfolio, source, record,
				new HashMap<>(), counters);
		case STOCK_PP_ACCOUNT_TRANSACTIONS_CSV -> importTransactions(readTransactions(file, csvDefinition(file,
				CsvImportDefinitionType.STOCK_ACCOUNT_TRANSACTION, csvDefinitionName)), true, portfolio, source, record,
				new HashMap<>(), counters);
		case STOCK_PP_SECURITIES_CSV -> importSecurities(readSecurities(file, csvDefinition(file,
				CsvImportDefinitionType.STOCK_SECURITY, csvDefinitionName)), source, counters);
		case STOCK_PP_PRICES_CSV -> importPriceCsv(file, csvDefinition(file,
				CsvImportDefinitionType.STOCK_SECURITY_PRICE, csvDefinitionName), portfolio, source, record, counters,
				new PriceImportCache());
		default -> throw new GBankingException("Unbekanntes Portfolio-Performance-Importformat: " + importType);
		}
	}

	public XmlImportPreview previewXml(Path file) throws IOException {
		Document document = new PortfolioPerformanceXmlReader().read(file);
		List<XmlPortfolio> portfolios = document.portfolios().stream()
				.map(portfolio -> new XmlPortfolio(portfolio.externalId(), portfolio.name(),
						portfolio.settlementAccountExternalId(), settlementAccountName(document, portfolio),
						settlementAccountCurrency(document, portfolio), portfolio.transactions().size(),
						suggestedPortfolioTarget(portfolio.externalId())))
				.toList();
		Set<String> additionalAccountIds = document.additionalAccounts().stream()
				.map(Account::externalId).collect(java.util.stream.Collectors.toSet());
		List<XmlAccount> accounts = document.accounts().stream()
				.map(account -> new XmlAccount(account.externalId(), account.name(), account.currency(),
						account.transactions().size(), additionalAccountIds.contains(account.externalId()),
						suggestedAccountTarget(account.externalId())))
				.toList();
		return new XmlImportPreview(portfolios, accounts);
	}

	private static String settlementAccountName(Document document, Portfolio portfolio) {
		return document.accounts().stream()
				.filter(account -> Objects.equals(account.externalId(), portfolio.settlementAccountExternalId()))
				.map(Account::name).findFirst().orElse("");
	}

	private static Currency settlementAccountCurrency(Document document, Portfolio portfolio) {
		return document.accounts().stream()
				.filter(account -> Objects.equals(account.externalId(), portfolio.settlementAccountExternalId()))
				.map(Account::currency).findFirst().orElse(null);
	}

	private Integer suggestedPortfolioTarget(String externalId) {
		BankAccount account = findProviderAccount(SOURCE_CODE + ":PORTFOLIO:" + externalId);
		if (account == null) {
			return null;
		}
		return dbController.getAll(StockPortfolio.class).stream()
				.filter(portfolio -> portfolio.getAccountId() == account.getId()).map(StockPortfolio::getId)
				.findFirst().orElse(null);
	}

	private Integer suggestedAccountTarget(String externalId) {
		BankAccount account = findProviderAccount(SOURCE_CODE + ":ACCOUNT:" + externalId);
		return account != null ? account.getId() : null;
	}

	public List<XmlTargetOption> getPortfolioImportTargets() {
		return new StockPortfolioService().getPortfolios().stream()
				.map(portfolio -> new XmlTargetOption(portfolio.portfolioId(), portfolio.displayName(),
						portfolio.settlementCurrency(), portfolio.settlementAccountId(), null))
				.sorted(Comparator.comparing(XmlTargetOption::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	public List<XmlTargetOption> getAccountImportTargets() {
		return dbController.getAll(BankAccount.class).stream()
				.filter(account -> account.getAccountType() != AccountType.DEPOT)
				.map(account -> new XmlTargetOption(account.getId(), account.getAccountName(),
						account.getBaseCurrency(), null, account.getAccountType()))
				.sorted(Comparator.comparing(XmlTargetOption::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	private void importDocument(Document document, PortfolioSummary portfolio, StockDataSource source,
			StockImportRecord record, Counters counters, PriceImportCache priceCache,
			boolean importAdditionalAccounts, XmlImportAssignments assignments) {
		Map<String, StockSecurity> securitiesByExternalId = new HashMap<>();
		for (Security securityData : document.securities()) {
			StockSecurity security = ensureSecurity(securityData, source, counters);
			securitiesByExternalId.put(securityData.externalId(), security);
			for (Price price : securityData.prices()) {
				if (savePrice(security, price, source, record, securityData.ticker(), priceCache)) {
					counters.prices++;
				}
			}
		}
		if (assignments != null) {
			validateAssignments(document, assignments);
			importMappedPortfolios(document, assignments, source, record, securitiesByExternalId, counters);
		} else if (portfolio != null) {
			importIntoSelectedPortfolio(document, portfolio, source, record, securitiesByExternalId, counters);
		} else {
			importAllPortfolios(document, source, record, securitiesByExternalId, counters);
		}
		if (assignments != null) {
			document.additionalAccounts().stream()
					.filter(account -> assignments.accountTargets().containsKey(account.externalId()))
					.forEach(account -> importAdditionalAccount(account,
							assignments.accountTargets().get(account.externalId()), counters));
		} else if (importAdditionalAccounts) {
			document.additionalAccounts().forEach(account -> importAdditionalAccount(account, counters));
		}
	}

	private static void validateAssignments(Document document, XmlImportAssignments assignments) {
		Set<String> portfolioIds = document.portfolios().stream().map(Portfolio::externalId)
				.collect(java.util.stream.Collectors.toSet());
		Set<String> linkedAccountIds = document.portfolios().stream().map(Portfolio::settlementAccountExternalId)
				.collect(java.util.stream.Collectors.toSet());
		if (!assignments.portfolioTargets().keySet().containsAll(portfolioIds)
				|| !assignments.accountTargets().keySet().containsAll(linkedAccountIds)) {
			throw new GBankingException("Die Depot- und Kontozuordnung ist unvollständig");
		}
		if (hasDuplicateExistingTarget(assignments.portfolioTargets())
				|| hasDuplicateExistingTarget(assignments.accountTargets())) {
			throw new GBankingException("Ein vorhandenes Depot oder Konto darf nur einmal zugeordnet werden");
		}
	}

	private static boolean hasDuplicateExistingTarget(Map<String, Integer> assignments) {
		Set<Integer> targetIds = new java.util.HashSet<>();
		return assignments.values().stream().filter(targetId -> targetId > 0)
				.anyMatch(targetId -> !targetIds.add(targetId));
	}

	private void importIntoSelectedPortfolio(Document document, PortfolioSummary target, StockDataSource source,
			StockImportRecord record, Map<String, StockSecurity> securitiesByExternalId, Counters counters) {
		if (document.portfolios().size() != 1) {
			throw new GBankingException("Die XML-Datei enthält " + document.portfolios().size()
					+ " Depots. Heben Sie die Depotauswahl auf, um alle Depots neu anzulegen und zu importieren.");
		}
		Portfolio sourcePortfolio = document.portfolios().get(0);
		importTransactions(sourcePortfolio.transactions(), false, target, source, record,
				securitiesByExternalId, counters);
		Account settlementAccount = findAccount(document, sourcePortfolio.settlementAccountExternalId());
		if (settlementAccount != null) {
			importTransactions(accountTransactions(settlementAccount), true, target, source, record,
					securitiesByExternalId, counters);
		}
	}

	private void importAllPortfolios(Document document, StockDataSource source, StockImportRecord record,
			Map<String, StockSecurity> securitiesByExternalId, Counters counters) {
		Map<String, List<ImportedPortfolio>> portfoliosBySettlementAccount = new LinkedHashMap<>();
		for (Portfolio sourcePortfolio : document.portfolios()) {
			Account settlementAccount = findAccount(document, sourcePortfolio.settlementAccountExternalId());
			if (settlementAccount == null) {
				throw new GBankingException("Für das Depot '" + sourcePortfolio.name()
						+ "' ist in der XML-Datei kein Verrechnungskonto hinterlegt");
			}
			PortfolioSummary target = ensureImportedPortfolio(sourcePortfolio, settlementAccount, 0, 0, counters);
			importTransactions(sourcePortfolio.transactions(), false, target, source, record,
					securitiesByExternalId, counters);
			portfoliosBySettlementAccount.computeIfAbsent(settlementAccount.externalId(), ignored -> new ArrayList<>())
					.add(new ImportedPortfolio(sourcePortfolio, target));
		}
		for (Map.Entry<String, List<ImportedPortfolio>> entry : portfoliosBySettlementAccount.entrySet()) {
			Account account = findAccount(document, entry.getKey());
			importSettlementAccount(account, entry.getValue(), source, record, securitiesByExternalId, counters);
			BankAccount targetAccount = dbController.getById(BankAccount.class,
					entry.getValue().get(0).target().settlementAccountId());
			updateImportedAccountBalance(account, targetAccount);
		}
	}

	private void importMappedPortfolios(Document document, XmlImportAssignments assignments,
			StockDataSource source, StockImportRecord record, Map<String, StockSecurity> securitiesByExternalId,
			Counters counters) {
		Map<String, List<ImportedPortfolio>> portfoliosBySettlementAccount = new LinkedHashMap<>();
		for (Portfolio sourcePortfolio : document.portfolios()) {
			Integer portfolioTargetId = assignments.portfolioTargets().get(sourcePortfolio.externalId());
			Integer accountTargetId = assignments.accountTargets().get(sourcePortfolio.settlementAccountExternalId());
			if (portfolioTargetId == null || accountTargetId == null) {
				throw new GBankingException("Die Zuordnung für das Depot '" + sourcePortfolio.name() + "' ist unvollständig");
			}
			Account settlementAccount = findAccount(document, sourcePortfolio.settlementAccountExternalId());
			if (settlementAccount == null) {
				throw new GBankingException("Für das Depot '" + sourcePortfolio.name()
						+ "' ist in der XML-Datei kein Verrechnungskonto hinterlegt");
			}
			PortfolioSummary target = ensureImportedPortfolio(sourcePortfolio, settlementAccount,
					portfolioTargetId, accountTargetId, counters);
			importTransactions(sourcePortfolio.transactions(), false, target, source, record,
					securitiesByExternalId, counters);
			portfoliosBySettlementAccount.computeIfAbsent(settlementAccount.externalId(), ignored -> new ArrayList<>())
					.add(new ImportedPortfolio(sourcePortfolio, target));
		}
		for (Map.Entry<String, List<ImportedPortfolio>> entry : portfoliosBySettlementAccount.entrySet()) {
			Account sourceAccount = findAccount(document, entry.getKey());
			importSettlementAccount(sourceAccount, entry.getValue(), source, record, securitiesByExternalId, counters);
			BankAccount targetAccount = dbController.getById(BankAccount.class,
					entry.getValue().get(0).target().settlementAccountId());
			updateImportedAccountBalance(sourceAccount, targetAccount);
		}
	}

	private void importSettlementAccount(Account account, List<ImportedPortfolio> linkedPortfolios,
			StockDataSource source, StockImportRecord record, Map<String, StockSecurity> securitiesByExternalId,
			Counters counters) {
		int accountId = linkedPortfolios.get(0).target().settlementAccountId();
		for (Transaction transaction : accountTransactions(account)) {
			ImportedType type = ImportedType.from(transaction.type());
			if (type.generalBookingOnly() || (type == ImportedType.INTEREST && !hasSecurity(transaction))) {
				if (saveGeneralBooking(accountId, transaction, type)) {
					counters.bookings++;
				}
				continue;
			}
			List<ImportedPortfolio> candidates = linkedPortfolios.stream()
					.filter(portfolio -> usesSecurity(portfolio.source(), transaction.securityExternalId())).toList();
			if (candidates.isEmpty() && linkedPortfolios.size() == 1) {
				candidates = linkedPortfolios;
			}
			if (candidates.size() != 1) {
				throw new GBankingException("Die Wertpapiertransaktion " + transaction.externalId()
						+ " des gemeinsam genutzten Verrechnungskontos '" + account.name()
						+ "' kann keinem Depot eindeutig zugeordnet werden");
			}
			importTransactions(List.of(transaction), true, candidates.get(0).target(), source, record,
					securitiesByExternalId, counters);
		}
	}

	private static boolean usesSecurity(Portfolio portfolio, String securityExternalId) {
		return securityExternalId != null && portfolio.transactions().stream()
				.anyMatch(transaction -> securityExternalId.equals(transaction.securityExternalId()));
	}

	private PortfolioSummary ensureImportedPortfolio(Portfolio data, Account settlementData,
			int mappedPortfolioId, int mappedAccountId, Counters counters) {
		if (mappedPortfolioId > 0) {
			PortfolioSummary target = new StockPortfolioService().getPortfolios().stream()
					.filter(portfolio -> portfolio.portfolioId() == mappedPortfolioId).findFirst()
					.orElseThrow(() -> new GBankingException("Das zugeordnete Depot wurde nicht gefunden"));
			if (mappedAccountId != target.settlementAccountId()) {
				throw new GBankingException("Das zugeordnete Verrechnungskonto passt nicht zum Depot '"
						+ target.displayName() + "'");
			}
			return target;
		}
		BankAccount settlementAccount = resolveImportedAccount(settlementData, AccountType.DEPOT_ACCOUNT,
				mappedAccountId, counters);
		String providerAccountId = SOURCE_CODE + ":PORTFOLIO:" + data.externalId();
		BankAccount depotAccount = findProviderAccount(providerAccountId);
		if (depotAccount == null) {
			depotAccount = new BankAccount();
			depotAccount.setProviderAccountId(providerAccountId);
			depotAccount.setAccountName(data.name());
			depotAccount.setBaseCurrency(settlementData.currency());
			depotAccount.setAccountType(AccountType.DEPOT);
			depotAccount.setSource(Source.IMPORT_INITIAL);
			depotAccount.setOfflineAccount(true);
			depotAccount.setAccountState(data.retired() ? AccountState.INACTIVE : AccountState.ACTIVE);
			depotAccount = dbController.insertOrUpdate(depotAccount);
			counters.accounts++;
		}

		int depotAccountId = depotAccount.getId();
		StockPortfolio portfolio = dbController.getAll(StockPortfolio.class).stream()
				.filter(candidate -> candidate.getAccountId() == depotAccountId).findFirst().orElse(null);
		LocalDate openedAt = earliestDate(data, settlementData);
		if (portfolio != null) {
			PortfolioSummary existing = requirePortfolioSummary(portfolio.getId());
			if (existing.settlementAccountId() != settlementAccount.getId()) {
				throw new GBankingException("Das bereits importierte Depot '" + data.name()
						+ "' ist mit einem anderen Verrechnungskonto verknüpft");
			}
			return existing;
		}
		portfolio = new StockPortfolio();
		portfolio.setAccountId(depotAccount.getId());
		portfolio.setCurrentSettlementRelationId(0);
		portfolio.setOpenedAt(openedAt);
		portfolio = dbController.insertOrUpdate(portfolio);

		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(settlementAccount.getId());
		relation.setValidFrom(openedAt);
		relation = dbController.insertOrUpdate(relation);
		portfolio.setCurrentSettlementRelationId(relation.getId());
		dbController.insertOrUpdate(portfolio);
		counters.portfolios++;
		return requirePortfolioSummary(portfolio.getId());
	}

	private static PortfolioSummary requirePortfolioSummary(int portfolioId) {
		return new StockPortfolioService().getPortfolios().stream()
				.filter(portfolio -> portfolio.portfolioId() == portfolioId).findFirst()
				.orElseThrow(() -> new GBankingException("Das importierte Depot wurde nicht gefunden"));
	}

	private static LocalDate earliestDate(Portfolio portfolio, Account settlementAccount) {
		return java.util.stream.Stream.concat(portfolio.transactions().stream(), settlementAccount.transactions().stream())
				.map(Transaction::date).filter(Objects::nonNull).map(LocalDateTime::toLocalDate)
				.min(Comparator.naturalOrder()).orElseGet(LocalDate::now);
	}

	private static Account findAccount(Document document, String externalId) {
		return document.accounts().stream().filter(account -> Objects.equals(account.externalId(), externalId))
				.findFirst().orElse(null);
	}

	private static List<Transaction> accountTransactions(Account account) {
		return account.transactions().stream().filter(transaction -> !isBuyOrSell(transaction.type())).toList();
	}

	private void importAdditionalAccount(Account data, Counters counters) {
		importAdditionalAccount(data, 0, counters);
	}

	private void importAdditionalAccount(Account data, int mappedAccountId, Counters counters) {
		BankAccount account = resolveImportedAccount(data, AccountType.CURRENT_ACCOUNT, mappedAccountId, counters);
		for (Transaction transaction : data.transactions()) {
			ImportedType type = ImportedType.from(transaction.type());
			if (transaction.bookingCurrency() != data.currency()) {
				throw new GBankingException("Die Buchungswährung " + transaction.bookingCurrency()
						+ " passt nicht zur Währung des Kontos '" + data.name() + "'");
			}
			if (saveGeneralBooking(account.getId(), transaction, type)) {
				counters.bookings++;
			}
		}
		updateImportedAccountBalance(data, account);
	}

	private BankAccount resolveImportedAccount(Account data, AccountType accountType, int mappedAccountId,
			Counters counters) {
		if (mappedAccountId <= 0) {
			return ensureImportedAccount(data, accountType, counters);
		}
		BankAccount account = dbController.getById(BankAccount.class, mappedAccountId);
		if (account == null || account.getAccountType() == AccountType.DEPOT
				|| account.getBaseCurrency() != data.currency()) {
			throw new GBankingException("Das zugeordnete Konto für '" + data.name() + "' ist nicht kompatibel");
		}
		if (accountType == AccountType.DEPOT_ACCOUNT && account.getAccountType() != AccountType.CURRENT_ACCOUNT
				&& account.getAccountType() != AccountType.DEPOT_ACCOUNT) {
			throw new GBankingException("Das zugeordnete Verrechnungskonto für '" + data.name()
					+ "' hat keine passende Kontoart");
		}
		return account;
	}

	private BankAccount ensureImportedAccount(Account data, AccountType accountType, Counters counters) {
		String providerAccountId = SOURCE_CODE + ":ACCOUNT:" + data.externalId();
		BankAccount account = findProviderAccount(providerAccountId);
		if (account != null) {
			if (accountType == AccountType.DEPOT_ACCOUNT && account.getAccountType() != AccountType.DEPOT_ACCOUNT) {
				account.setAccountType(AccountType.DEPOT_ACCOUNT);
				dbController.insertOrUpdate(account);
			}
			return account;
		}
		account = new BankAccount();
		account.setProviderAccountId(providerAccountId);
		account.setAccountName(data.name());
		account.setBaseCurrency(data.currency());
		account.setAccountType(accountType);
		account.setSource(Source.IMPORT_INITIAL);
		account.setOfflineAccount(true);
		account.setAccountState(data.retired() ? AccountState.INACTIVE : AccountState.ACTIVE);
		account = dbController.insertOrUpdate(account);
		counters.accounts++;
		return account;
	}

	private BankAccount findProviderAccount(String providerAccountId) {
		return dbController.getAll(BankAccount.class).stream()
				.filter(candidate -> providerAccountId.equals(candidate.getProviderAccountId()))
				.findFirst().orElse(null);
	}

	private void updateImportedAccountBalance(Account data, BankAccount account) {
		if (account == null) {
			return;
		}
		BigDecimal balance = data.transactions().stream()
				.map(transaction -> accountAmount(transaction, ImportedType.from(transaction.type())))
				.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
		account.setBalance(balance);
		dbController.insertOrUpdate(account);
	}

	private static boolean isBuyOrSell(String type) {
		ImportedType importedType = ImportedType.from(type);
		return importedType == ImportedType.BUY || importedType == ImportedType.SELL;
	}

	private CsvImportDefinition csvDefinition(Path file, CsvImportDefinitionType expectedType,
			String definitionName) throws IOException {
		Match match;
		if (definitionName != null) {
			match = csvImportAnalyzer.match(file, definitionName);
			if (match == null || match.definition().getType() != expectedType) {
				throw new GBankingException("CSV-Importformat '" + definitionName + "' wurde nicht gefunden");
			}
		} else {
			Analysis analysis = csvImportAnalyzer.analyze(file, expectedType);
			if (analysis.problem() != null || analysis.matches().size() != 1) {
				throw new GBankingException("Für die CSV-Datei wurde kein eindeutiges Importformat vom Typ '"
						+ expectedType + "' gefunden");
			}
			match = analysis.matches().get(0);
		}
		if (!match.hasRequiredHeaders()) {
			throw new GBankingException("Im CSV-Importformat '" + match.definition().getName()
					+ "' fehlen Pflichtspalten: " + String.join(", ", match.missingRequiredHeaders()));
		}
		return match.definition();
	}

	private CsvImportData readCsv(Path file, CsvImportDefinition definition) throws IOException {
		return csvImportAnalyzer.read(file, definition);
	}

	private Map<String, StockSecurity> priceColumns(CsvImportData data, CsvImportDefinition definition,
			Map<String, StockSecurity> securities) {
		Set<String> dateHeaders = Set.copyOf(definition.getSourceFields(CsvImportTarget.STOCK_PRICE_DATE));
		Map<String, StockSecurity> columns = new LinkedHashMap<>();
		for (String header : data.headers()) {
			if (dateHeaders.contains(header)) {
				continue;
			}
			StockSecurity security = securities.get(normalize(header));
			if (security == null) {
				throw new GBankingException("Für die Kursspalte '" + header
						+ "' wurde kein Wertpapier gefunden. Bitte zuerst die Wertpapier-Stammdaten importieren.");
			}
			columns.put(header, security);
		}
		return columns;
	}

	private static String value(CsvImportData.Row row, CsvImportDefinition definition, CsvImportTarget target) {
		for (String header : definition.getSourceFields(target)) {
			String value = row.text(header);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String requiredValue(CsvImportData.Row row, CsvImportDefinition definition,
			CsvImportTarget target) {
		String value = value(row, definition, target);
		if (value == null) {
			throw new GBankingException("Pflichtfeld '" + target.getPropertyName() + "' fehlt in Zeile "
					+ row.lineNumber());
		}
		return value;
	}

	private static BigDecimal decimal(CsvImportData.Row row, CsvImportDefinition definition,
			CsvImportTarget target) {
		return decimal(value(row, definition, target), definition, target.getPropertyName(), row.lineNumber());
	}

	private static BigDecimal decimal(String value, CsvImportDefinition definition, String field, long row) {
		try {
			return CsvImportValueParser.parseDecimal(value, definition);
		} catch (NumberFormatException exception) {
			throw new GBankingException("Ungültige Zahl in Feld '" + field + "', Zeile " + row + ": " + value,
					exception);
		}
	}

	private static LocalDateTime dateTime(CsvImportData.Row row, CsvImportDefinition definition,
			CsvImportTarget target) {
		String value = requiredValue(row, definition, target);
		try {
			return CsvImportValueParser.parseDateTime(value, definition);
		} catch (DateTimeException | NumberFormatException exception) {
			throw new GBankingException("Ungültiges Datum in Zeile " + row.lineNumber() + ": " + value, exception);
		}
	}

	private List<Security> readSecurities(Path file, CsvImportDefinition definition) throws IOException {
		List<Security> result = new ArrayList<>();
		for (CsvImportData.Row row : readCsv(file, definition).rows()) {
			result.add(new Security(null, requiredValue(row, definition, CsvImportTarget.SECURITY_NAME),
					Currency.forCode(requiredValue(row, definition, CsvImportTarget.SECURITY_CURRENCY)),
					value(row, definition, CsvImportTarget.SECURITY_ISIN),
					value(row, definition, CsvImportTarget.SECURITY_WKN),
					value(row, definition, CsvImportTarget.SECURITY_TICKER),
					value(row, definition, CsvImportTarget.SECURITY_PROVIDER), List.of()));
		}
		return result;
	}

	private List<Transaction> readTransactions(Path file, CsvImportDefinition definition) throws IOException {
		List<Transaction> result = new ArrayList<>();
		for (CsvImportData.Row row : readCsv(file, definition).rows()) {
			result.add(new Transaction(null, dateTime(row, definition, CsvImportTarget.STOCK_DATE),
					requiredValue(row, definition, CsvImportTarget.STOCK_TRANSACTION_TYPE),
					decimal(row, definition, CsvImportTarget.STOCK_VALUE),
					Currency.forCode(requiredValue(row, definition, CsvImportTarget.STOCK_BOOKING_CURRENCY)),
					decimal(row, definition, CsvImportTarget.STOCK_GROSS_VALUE),
					Currency.forCode(value(row, definition, CsvImportTarget.STOCK_GROSS_CURRENCY)),
					decimal(row, definition, CsvImportTarget.STOCK_EXCHANGE_RATE),
					decimal(row, definition, CsvImportTarget.STOCK_FEES),
					decimal(row, definition, CsvImportTarget.STOCK_TAXES),
					decimal(row, definition, CsvImportTarget.STOCK_QUANTITY), null,
					value(row, definition, CsvImportTarget.SECURITY_ISIN),
					value(row, definition, CsvImportTarget.SECURITY_WKN),
					value(row, definition, CsvImportTarget.SECURITY_TICKER),
					value(row, definition, CsvImportTarget.SECURITY_NAME),
					value(row, definition, CsvImportTarget.STOCK_NOTE)));
		}
		return result;
	}

	private void importSecurities(List<Security> securities, StockDataSource source, Counters counters) {
		for (Security security : securities) {
			ensureSecurity(security, source, counters);
		}
	}

	private void importPriceCsv(Path file, CsvImportDefinition definition, PortfolioSummary portfolio, StockDataSource source,
			StockImportRecord record, Counters counters, PriceImportCache priceCache) throws IOException {
		CsvImportData data = readCsv(file, definition);
		Map<String, StockSecurity> securities = securityLookup();
		Map<String, StockSecurity> columns = priceColumns(data, definition, securities);
		Currency fallbackCurrency = portfolio.settlementCurrency();
		for (CsvImportData.Row row : data.rows()) {
			LocalDate priceDate = dateTime(row, definition, CsvImportTarget.STOCK_PRICE_DATE).toLocalDate();
			for (Map.Entry<String, StockSecurity> column : columns.entrySet()) {
				BigDecimal priceValue = decimal(row.text(column.getKey()), definition, column.getKey(), row.lineNumber());
				if (priceValue != null) {
					StockSecurity security = column.getValue();
					if (security.getDefaultQuoteCurrency() == null && fallbackCurrency != null) {
						security.setDefaultQuoteCurrency(fallbackCurrency);
						dbController.insertOrUpdate(security);
					}
					if (savePrice(security, new Price(priceDate, priceValue), source, record,
							column.getKey(), priceCache)) {
						counters.prices++;
					}
				}
			}
		}
	}

	private void importTransactions(List<Transaction> transactions, boolean accountFile, PortfolioSummary portfolio,
			StockDataSource source, StockImportRecord record, Map<String, StockSecurity> securitiesByExternalId,
			Counters counters) {
		for (Transaction data : transactions) {
			ImportedType type = ImportedType.from(data.type());
			if (accountFile && (type.generalBookingOnly()
					|| (type == ImportedType.INTEREST && !hasSecurity(data)))) {
				validateBookingCurrency(portfolio, data);
				if (saveGeneralBooking(portfolio, data, type)) {
					counters.bookings++;
				}
				continue;
			}
			if (accountFile && (type == ImportedType.BUY || type == ImportedType.SELL)
					&& transactionExists(portfolio.portfolioId(), transactionFingerprint(portfolio, data, type,
							resolveSecurity(data, securitiesByExternalId, source, counters)))) {
				continue;
			}
			if (!type.stockTransaction()) {
				continue;
			}
			StockSecurity security = resolveSecurity(data, securitiesByExternalId, source, counters);
			if (saveStockTransaction(portfolio, data, type, security, source, record)) {
				counters.transactions++;
				if (hasCashValue(data, type)) {
					counters.bookings++;
				}
			}
		}
	}

	private static boolean hasSecurity(Transaction data) {
		return data.securityExternalId() != null || data.isin() != null || data.wkn() != null
				|| data.ticker() != null || data.securityName() != null;
	}

	private StockSecurity resolveSecurity(Transaction data, Map<String, StockSecurity> securitiesByExternalId,
			StockDataSource source, Counters counters) {
		StockSecurity external = data.securityExternalId() != null
				? securitiesByExternalId.get(data.securityExternalId()) : null;
		if (external != null) {
			return external;
		}
		if (data.securityName() == null && data.isin() == null && data.wkn() == null && data.ticker() == null) {
			throw new GBankingException("Für die Transaktion am " + data.date().toLocalDate() + " fehlt das Wertpapier");
		}
		Security securityData = new Security(null,
				data.securityName() != null ? data.securityName() : firstNonBlank(data.isin(), data.wkn(), data.ticker()),
				data.grossCurrency() != null ? data.grossCurrency() : data.bookingCurrency(), data.isin(), data.wkn(),
				data.ticker(), null, List.of());
		return ensureSecurity(securityData, source, counters);
	}

	private StockSecurity ensureSecurity(Security data, StockDataSource source, Counters counters) {
		Map<String, StockSecurity> lookup = securityLookup();
		StockSecurity security = findSecurity(data, lookup);
		if (security == null) {
			security = new StockSecurity();
			security.setSecurityType(StockSecurityType.OTHER);
			security.setName(data.name());
			security.setIssuer(data.issuer());
			security.setDefaultQuantityType(StockQuantityType.UNITS);
			security.setDefaultQuoteCurrency(data.currency());
			security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
			security.setSecurityState(StockSecurityState.ACTIVE);
			security = dbController.insertOrUpdate(security);
			counters.securities++;
		} else if (security.getDefaultQuoteCurrency() == null && data.currency() != null) {
			security.setDefaultQuoteCurrency(data.currency());
			dbController.insertOrUpdate(security);
		}
		ensureIdentifier(security, source, StockIdentifierType.ISIN, data.isin());
		ensureIdentifier(security, source, StockIdentifierType.WKN, data.wkn());
		ensureIdentifier(security, source, StockIdentifierType.TICKER, data.ticker());
		return security;
	}

	private StockSecurity findSecurity(Security data, Map<String, StockSecurity> lookup) {
		for (String identifier : List.of(nullToEmpty(data.isin()), nullToEmpty(data.wkn()), nullToEmpty(data.ticker()))) {
			StockSecurity security = lookup.get(normalize(identifier));
			if (security != null) {
				return security;
			}
		}
		String name = normalize(data.name());
		return dbController.getAll(StockSecurity.class).stream()
				.filter(candidate -> normalize(candidate.getName()).equals(name))
				.filter(candidate -> data.currency() == null || candidate.getDefaultQuoteCurrency() == null
						|| candidate.getDefaultQuoteCurrency() == data.currency())
				.findFirst().orElse(null);
	}

	private Map<String, StockSecurity> securityLookup() {
		Map<Integer, StockSecurity> securities = new HashMap<>();
		dbController.getAll(StockSecurity.class).forEach(security -> securities.put(security.getId(), security));
		Map<String, StockSecurity> result = new HashMap<>();
		for (StockSecurityIdentifier identifier : dbController.getAll(StockSecurityIdentifier.class)) {
			if (identifier.getValidTo() == null) {
				StockSecurity security = securities.get(identifier.getSecurityId());
				putLookup(result, identifier.getIdentifierValue(), security);
				if (identifier.getIdentifierType() == StockIdentifierType.TICKER) {
					putLookup(result, stripTickerSuffix(identifier.getIdentifierValue()), security);
				}
			}
		}
		return result;
	}

	private static void putLookup(Map<String, StockSecurity> lookup, String key, StockSecurity security) {
		if (security != null && key != null && !key.isBlank()) {
			lookup.putIfAbsent(normalize(key), security);
		}
	}

	private void ensureIdentifier(StockSecurity security, StockDataSource source, StockIdentifierType type, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		boolean exists = dbController.getAll(StockSecurityIdentifier.class).stream()
				.anyMatch(identifier -> identifier.getIdentifierType() == type && identifier.getValidTo() == null
						&& identifier.getIdentifierValue().equalsIgnoreCase(value));
		if (!exists) {
			StockSecurityIdentifier identifier = new StockSecurityIdentifier();
			identifier.setSecurityId(security.getId());
			identifier.setSourceId(source.getId());
			identifier.setIdentifierType(type);
			identifier.setIdentifierValue(value);
			dbController.insertOrUpdate(identifier);
		}
	}

	private boolean savePrice(StockSecurity security, Price priceData, StockDataSource source,
			StockImportRecord record, String providerSymbol, PriceImportCache cache) {
		Currency currency = security.getDefaultQuoteCurrency();
		if (currency == null) {
			throw new GBankingException("Für das Wertpapier '" + security.getName() + "' fehlt die Kurswährung");
		}
		StockSecurityPriceSource priceSource = getOrCreatePriceSource(security, source, cache);
		long priceE8 = scaled(priceData.value(), PRICE_SCALE, "Kurs");
		LocalDateTime quotedAt = priceData.date().atStartOfDay();
		PriceValueKey valueKey = new PriceValueKey(priceSource.getId(), quotedAt, priceE8);
		if (cache.pricesByValue.containsKey(valueKey)) {
			return false;
		}
		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setImportRecordId(record.getId());
		price.setQuotedAt(quotedAt);
		price.setPriceE8(priceE8);
		price.setQuoteCurrency(currency);
		price.setQuotationType(StockQuotationType.ABSOLUTE);
		price.setPriceType(StockPriceType.CLOSE);
		price.setExternalReference(priceData.date() + ":" + nullToEmpty(providerSymbol));
		PriceDateKey dateKey = new PriceDateKey(priceSource.getId(), quotedAt);
		StockSecurityPrice previous = cache.latestPriceByDate.get(dateKey);
		if (previous != null) {
			price.setSupersedesPriceId(previous.getId());
		}
		price = dbController.insertOrUpdate(price);
		cache.pricesByValue.put(valueKey, price);
		cache.latestPriceByDate.put(dateKey, price);
		return true;
	}

	private StockSecurityPriceSource getOrCreatePriceSource(StockSecurity security, StockDataSource source,
			PriceImportCache cache) {
		PriceSourceKey key = new PriceSourceKey(security.getId(), source.getId(), null);
		return cache.priceSources.computeIfAbsent(key, ignored -> {
					StockSecurityPriceSource created = new StockSecurityPriceSource();
					created.setSecurityId(security.getId());
					created.setSourceId(source.getId());
					created.setProviderSymbol(null);
					created.setPriority(source.getDefaultPriority());
					return dbController.insertOrUpdate(created);
				});
	}

	private boolean saveStockTransaction(PortfolioSummary portfolio, Transaction data, ImportedType importedType,
			StockSecurity security, StockDataSource source, StockImportRecord record) {
		validateTransaction(data, importedType, portfolio, security);
		String fingerprint = transactionFingerprint(portfolio, data, importedType, security);
		if (transactionExists(portfolio.portfolioId(), fingerprint)) {
			return false;
		}
		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolio.portfolioId());
		transaction.setSourceId(source.getId());
		transaction.setImportRecordId(record.getId());
		transaction.setTransactionType(importedType.stockType());
		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setTradeAt(data.date());
		transaction.setSettlementDueAt(data.date());
		transaction.setSettledAt(data.date());
		transaction.setCashValueAt(data.date());
		transaction.setProviderBookedAt(data.date());
		transaction.setSettlementDateInferred(true);
		transaction.setFingerprint(fingerprint);
		transaction.setEditableFieldMask(editableFieldMask(data));
		transaction = dbController.insertOrUpdate(transaction);

		createSecurityLeg(transaction, security, data, importedType);
		Booking booking = createTransactionBooking(portfolio, data, importedType, security);
		createCashLegs(transaction, portfolio, data, importedType, booking);
		if (data.note() != null) {
			StockTransactionMetadata metadata = new StockTransactionMetadata();
			metadata.setTransactionId(transaction.getId());
			metadata.setNote(data.note());
			dbController.insertOrUpdate(metadata);
		}
		return true;
	}

	private void createSecurityLeg(StockTransaction transaction, StockSecurity security, Transaction data,
			ImportedType importedType) {
		StockTransactionSecurityLeg leg = new StockTransactionSecurityLeg();
		leg.setTransactionId(transaction.getId());
		leg.setLegNumber(1);
		leg.setSecurityId(security.getId());
		leg.setLegRole(importedType.positionChange() ? StockSecurityLegRole.POSITION : StockSecurityLegRole.REFERENCE);
		BigDecimal quantity = data.shares() != null ? data.shares().abs() : BigDecimal.ZERO;
		long quantityE9 = scaled(quantity, QUANTITY_SCALE, "Stückzahl");
		leg.setQuantityE9(importedType.positionChange() ? importedType.positionDirection() * quantityE9 : quantityE9);
		leg.setQuantityType(StockQuantityType.UNITS);
		BigDecimal unitPrice = calculateUnitPrice(data, importedType);
		if (unitPrice != null) {
			leg.setPriceE8(scaled(unitPrice, PRICE_SCALE, "Kurs"));
			leg.setPriceCurrency(data.grossCurrency() != null ? data.grossCurrency() : data.bookingCurrency());
			leg.setQuotationType(StockQuotationType.ABSOLUTE);
		}
		dbController.insertOrUpdate(leg);
	}

	private Booking createTransactionBooking(PortfolioSummary portfolio, Transaction data, ImportedType type,
			StockSecurity security) {
		BigDecimal amount = accountAmount(data, type);
		if (amount == null || amount.signum() == 0) {
			return null;
		}
		return createBooking(portfolio.settlementAccountId(), data.date(), amount,
				bookingPurpose(type, security.getName(), data.note()));
	}

	private void createCashLegs(StockTransaction transaction, PortfolioSummary portfolio, Transaction data,
			ImportedType type, Booking booking) {
		BigDecimal net = accountAmount(data, type);
		if (net == null || net.signum() == 0) {
			return;
		}
		BigDecimal fees = nonNegative(data.fees());
		BigDecimal taxes = nonNegative(data.taxes());
		List<CashValue> values = new ArrayList<>();
		if (type == ImportedType.BUY) {
			values.add(new CashValue(StockCashLegRole.TRADE_VALUE, net.add(fees).add(taxes)));
		} else if (type == ImportedType.SELL) {
			values.add(new CashValue(StockCashLegRole.TRADE_VALUE, net.add(fees).add(taxes)));
		} else if (type == ImportedType.DIVIDEND || type == ImportedType.INTEREST) {
			values.add(new CashValue(type == ImportedType.DIVIDEND ? StockCashLegRole.DIVIDEND : StockCashLegRole.INTEREST,
					net.add(fees).add(taxes)));
		} else {
			values.add(new CashValue(StockCashLegRole.OTHER, net));
		}
		if (fees.signum() != 0) {
			values.add(new CashValue(StockCashLegRole.FEE, fees.negate()));
		}
		if (taxes.signum() != 0) {
			values.add(new CashValue(StockCashLegRole.TAX, taxes.negate()));
		}

		int legNumber = 1;
		for (CashValue value : values) {
			if (value.amount().signum() == 0) {
				continue;
			}
			StockTransactionCashLeg leg = new StockTransactionCashLeg();
			leg.setTransactionId(transaction.getId());
			leg.setLegNumber(legNumber++);
			leg.setAccountId(portfolio.settlementAccountId());
			leg.setBookingId(booking != null ? booking.getId() : null);
			leg.setLegRole(value.role());
			leg.setAmountMinor(toMinor(value.amount(), data.bookingCurrency()));
			leg.setCurrency(data.bookingCurrency());
			leg.setValueAt(data.date());
			dbController.insertOrUpdate(leg);
		}
	}

	private boolean saveGeneralBooking(PortfolioSummary portfolio, Transaction data, ImportedType type) {
		return saveGeneralBooking(portfolio.settlementAccountId(), data, type);
	}

	private boolean saveGeneralBooking(int accountId, Transaction data, ImportedType type) {
		BigDecimal amount = accountAmount(data, type);
		if (amount == null || amount.signum() == 0) {
			return false;
		}
		String purpose = data.note() != null ? data.note() : type.displayName();
		boolean exists = dbController.getAllByParent(Booking.class, accountId).stream()
				.filter(booking -> booking.getSource() != null && booking.getSource().getGroup() == Source.IMPORT.getGroup())
				.anyMatch(booking -> Objects.equals(booking.getDate(), data.date().toLocalDate())
						&& sameAmount(booking.getAmount(), amount) && Objects.equals(booking.getPurpose(), purpose));
		if (exists) {
			return false;
		}
		createBooking(accountId, data.date(), amount, purpose);
		return true;
	}

	private Booking createBooking(int accountId, LocalDateTime date, BigDecimal amount, String purpose) {
		Booking booking = new Booking();
		booking.setAccountId(accountId);
		booking.setDateBooking(date.toLocalDate());
		booking.setDateValue(date.toLocalDate());
		booking.setAmount(amount);
		booking.setPurpose(purpose);
		booking.setBookingType(BookingType.fromAmount(amount));
		booking.setSource(Source.IMPORT_NEW);
		return dbController.insertOrUpdate(booking);
	}

	private void validateTransaction(Transaction data, ImportedType type, PortfolioSummary portfolio,
			StockSecurity security) {
		if (data.date() == null || data.bookingCurrency() == null) {
			throw new GBankingException("Datum oder Buchungswährung einer Transaktion fehlt");
		}
		validateBookingCurrency(portfolio, data);
		if (!type.positionChange() && data.value() == null) {
			throw new GBankingException("Für " + type.displayName() + " fehlt der Buchungswert");
		}
		if ((type == ImportedType.BUY || type == ImportedType.SELL) && data.value() == null) {
			throw new GBankingException("Für " + type.displayName() + " fehlt der Buchungswert");
		}
		if (type.positionChange() && (data.shares() == null || data.shares().signum() <= 0)) {
			throw new GBankingException("Für " + type.displayName() + " von '" + security.getName()
					+ "' muss eine positive Stückzahl angegeben sein");
		}
		if (data.grossCurrency() != null && data.grossCurrency() != data.bookingCurrency()
				&& (data.exchangeRate() == null || data.exchangeRate().signum() <= 0)) {
			throw new GBankingException("Für unterschiedliche Buchungs- und Bruttowährungen muss ein exakter Wechselkurs vorliegen");
		}
	}

	private void validateBookingCurrency(PortfolioSummary portfolio, Transaction data) {
		BankAccount account = dbController.getById(BankAccount.class, portfolio.settlementAccountId());
		if (data.bookingCurrency() == null || account == null || account.getBaseCurrency() != data.bookingCurrency()) {
			throw new GBankingException("Die Buchungswährung " + data.bookingCurrency()
					+ " passt nicht zur Währung des Verrechnungskontos");
		}
	}

	private static BigDecimal calculateUnitPrice(Transaction data, ImportedType type) {
		if (data.shares() == null || data.shares().signum() == 0) {
			return null;
		}
		BigDecimal gross = data.grossValue();
		if (gross == null && data.value() != null) {
			BigDecimal net = data.value().abs();
			BigDecimal expenses = nonNegative(data.fees()).add(nonNegative(data.taxes()));
			gross = type == ImportedType.BUY ? net.subtract(expenses) : net.add(expenses);
		}
		return gross != null && gross.signum() > 0
				? gross.abs().divide(data.shares().abs(), PRICE_SCALE, RoundingMode.HALF_UP) : null;
	}

	private static BigDecimal accountAmount(Transaction data, ImportedType type) {
		if (data.value() == null) {
			return null;
		}
		BigDecimal absolute = data.value().abs();
		return switch (type) {
		case BUY, REMOVAL, FEE, TAX -> absolute.negate();
		case SELL, DEPOSIT, DIVIDEND, INTEREST, TAX_REFUND -> absolute;
		default -> data.value();
		};
	}

	private static boolean hasCashValue(Transaction data, ImportedType type) {
		BigDecimal value = accountAmount(data, type);
		return value != null && value.signum() != 0;
	}

	private static String bookingPurpose(ImportedType type, String securityName, String note) {
		return note != null ? note : type.displayName() + " " + securityName;
	}

	private static int editableFieldMask(Transaction data) {
		EnumSet<StockTransactionEditField> fields = EnumSet.of(StockTransactionEditField.SETTLEMENT_DATE,
				StockTransactionEditField.ACCRUED_INTEREST);
		if (data.exchangeRate() == null) {
			fields.add(StockTransactionEditField.EXCHANGE_RATE);
		}
		if (data.fees() == null) {
			fields.add(StockTransactionEditField.FEES);
		}
		if (data.taxes() == null) {
			fields.add(StockTransactionEditField.TAXES);
		}
		if (data.note() == null) {
			fields.add(StockTransactionEditField.NOTE);
		}
		return StockTransactionEditField.maskOf(fields);
	}

	private static String transactionFingerprint(PortfolioSummary portfolio, Transaction data, ImportedType type,
			StockSecurity security) {
		String value = String.join("|", Integer.toString(portfolio.portfolioId()), type.name(), data.date().toString(),
				Integer.toString(security.getId()), normalizedDecimal(data.shares()), normalizedAbsolute(data.value()),
				data.bookingCurrency() != null ? data.bookingCurrency().name() : "");
		return hash(value.getBytes(StandardCharsets.UTF_8), 0);
	}

	private boolean transactionExists(int portfolioId, String fingerprint) {
		return dbController.getAllByParent(StockTransaction.class, portfolioId).stream()
				.anyMatch(transaction -> fingerprint.equalsIgnoreCase(transaction.getFingerprint()));
	}

	private StockDataSource requireSource() {
		return dbController.getAll(StockDataSource.class).stream()
				.filter(source -> SOURCE_CODE.equalsIgnoreCase(source.getSourceCode()))
				.filter(source -> source.getSourceType() == StockDataSourceType.FILE)
				.findFirst().orElseThrow(() -> new GBankingException("Die Portfolio-Performance-Datenquelle fehlt"));
	}

	private boolean isAlreadyImported(int sourceId, String contentHash) {
		return dbController.getAll(StockImportBatch.class).stream()
				.anyMatch(batch -> batch.getSourceId() == sourceId && batch.getImportStatus() == StockImportStatus.IMPORTED
						&& contentHash.equalsIgnoreCase(batch.getContentHash()));
	}

	private StockImportBatch createBatch(StockDataSource source, Path file, ExportType type, String contentHash) {
		StockImportBatch batch = new StockImportBatch();
		batch.setSourceId(source.getId());
		batch.setImporterKey(IMPORTER_KEY);
		batch.setFormatType(type.name());
		batch.setImporterVersion(IMPORTER_VERSION);
		Path simpleFileName = file.getFileName();
		batch.setFileName(simpleFileName != null ? simpleFileName.toString() : file.toString());
		batch.setContentHash(contentHash);
		batch.setImportStatus(StockImportStatus.RUNNING);
		batch.setStartedAt(LocalDateTime.now());
		return dbController.insertOrUpdate(batch);
	}

	private StockImportRecord createRecord(StockImportBatch batch, ExportType type, String fingerprint) {
		StockImportRecord record = new StockImportRecord();
		record.setImportBatchId(batch.getId());
		record.setRecordNumber(1);
		record.setRecordType(type.name());
		record.setFingerprint(fingerprint);
		record.setRecordStatus(StockImportRecordStatus.IMPORTED);
		return dbController.insertOrUpdate(record);
	}

	private static void requirePortfolio(PortfolioSummary portfolio) {
		if (portfolio == null || portfolio.portfolioId() <= 0 || portfolio.settlementAccountId() <= 0) {
			throw new GBankingException("Bitte wählen Sie zuerst ein Wertpapier-Depot aus");
		}
	}

	private static long scaled(BigDecimal value, int scale, String field) {
		try {
			return value.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact();
		} catch (ArithmeticException exception) {
			throw new GBankingException(field + " ist zu groß", exception);
		}
	}

	private static long toMinor(BigDecimal value, Currency currency) {
		return value.setScale(currency.getMinorUnitDigits(), RoundingMode.HALF_UP)
				.movePointRight(currency.getMinorUnitDigits()).longValueExact();
	}

	private static String normalizedDecimal(BigDecimal value) {
		return value != null ? value.stripTrailingZeros().toPlainString() : "";
	}

	private static String normalizedAbsolute(BigDecimal value) {
		return value != null ? normalizedDecimal(value.abs()) : "";
	}

	private static BigDecimal nonNegative(BigDecimal value) {
		return value != null ? value.abs() : BigDecimal.ZERO;
	}

	private static boolean sameAmount(BigDecimal left, BigDecimal right) {
		return left != null && right != null && left.compareTo(right) == 0;
	}

	private static String stripTickerSuffix(String value) {
		int separator = value != null ? value.indexOf('.') : -1;
		return separator > 0 ? value.substring(0, separator) : value;
	}

	private static String normalize(String value) {
		return nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
	}

	private static String nullToEmpty(String value) {
		return value != null ? value : "";
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "Unbekanntes Wertpapier";
	}

	private static String hash(byte[] content, int portfolioId) {
		return hash(content, portfolioId, false, null, null);
	}

	private static String hash(byte[] content, int portfolioId, boolean importAdditionalAccounts,
			XmlImportAssignments assignments, String discriminator) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(content);
			if (portfolioId > 0) {
				digest.update((byte) ':');
				digest.update(Integer.toString(portfolioId).getBytes(StandardCharsets.UTF_8));
			}
			if (importAdditionalAccounts) {
				digest.update(":ADDITIONAL_ACCOUNTS".getBytes(StandardCharsets.UTF_8));
			}
			if (assignments != null) {
				digest.update(assignments.fingerprint().getBytes(StandardCharsets.UTF_8));
			}
			if (discriminator != null) {
				digest.update(discriminator.getBytes(StandardCharsets.UTF_8));
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 ist nicht verfügbar", exception);
		}
	}

	public record ImportResult(int securities, int transactions, int bookings, int prices, int portfolios, int accounts,
			boolean alreadyImported) {
		public int totalImported() {
			return securities + transactions + bookings + prices + portfolios + accounts;
		}
	}

	public record XmlPortfolio(String externalId, String name, String settlementAccountExternalId,
			String settlementAccountName, Currency currency, int transactionCount, Integer suggestedTargetId) {
	}

	public record XmlAccount(String externalId, String name, Currency currency, int transactionCount,
			boolean additional, Integer suggestedTargetId) {
	}

	public record XmlImportPreview(List<XmlPortfolio> portfolios, List<XmlAccount> accounts) {
		public XmlImportPreview {
			portfolios = List.copyOf(portfolios);
			accounts = List.copyOf(accounts);
		}

		public List<XmlAccount> additionalAccounts() {
			return accounts.stream().filter(XmlAccount::additional).toList();
		}
	}

	public record XmlTargetOption(int id, String name, Currency currency, Integer settlementAccountId,
			AccountType accountType) {
	}

	public record XmlImportAssignments(Map<String, Integer> portfolioTargets, Map<String, Integer> accountTargets) {
		public XmlImportAssignments {
			portfolioTargets = Map.copyOf(portfolioTargets);
			accountTargets = Map.copyOf(accountTargets);
		}

		private String fingerprint() {
			return ":MAPPING:PORTFOLIOS=" + sortedMapping(portfolioTargets)
					+ ":ACCOUNTS=" + sortedMapping(accountTargets);
		}

		private static String sortedMapping(Map<String, Integer> mapping) {
			return mapping.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.map(entry -> entry.getKey() + '=' + entry.getValue())
					.collect(java.util.stream.Collectors.joining(","));
		}
	}

	private static final class Counters {
		private int securities;
		private int transactions;
		private int bookings;
		private int prices;
		private int portfolios;
		private int accounts;

		private ImportResult result(boolean alreadyImported) {
			return new ImportResult(securities, transactions, bookings, prices, portfolios, accounts, alreadyImported);
		}
	}

	private record ImportedPortfolio(Portfolio source, PortfolioSummary target) {
	}

	private record CashValue(StockCashLegRole role, BigDecimal amount) {
	}

	private record PriceSourceKey(int securityId, int sourceId, String providerSymbol) {
	}

	private record PriceDateKey(int priceSourceId, LocalDateTime quotedAt) {
	}

	private record PriceValueKey(int priceSourceId, LocalDateTime quotedAt, long priceE8) {
	}

	private final class PriceImportCache {
		private final Map<PriceSourceKey, StockSecurityPriceSource> priceSources = new HashMap<>();
		private final Map<PriceDateKey, StockSecurityPrice> latestPriceByDate = new HashMap<>();
		private final Map<PriceValueKey, StockSecurityPrice> pricesByValue = new HashMap<>();

		private PriceImportCache() {
			Map<Integer, StockSecurityPriceSource> sourcesById = new HashMap<>();
			for (StockSecurityPriceSource priceSource : dbController.getAll(StockSecurityPriceSource.class)) {
				priceSources.put(new PriceSourceKey(priceSource.getSecurityId(), priceSource.getSourceId(),
						priceSource.getProviderSymbol()), priceSource);
				sourcesById.put(priceSource.getId(), priceSource);
			}
			for (StockSecurityPrice price : dbController.getAll(StockSecurityPrice.class)) {
				if (!sourcesById.containsKey(price.getPriceSourceId()) || price.getPriceType() != StockPriceType.CLOSE) {
					continue;
				}
				PriceDateKey dateKey = new PriceDateKey(price.getPriceSourceId(), price.getQuotedAt());
				latestPriceByDate.merge(dateKey, price, (left, right) -> left.getId() > right.getId() ? left : right);
				pricesByValue.put(new PriceValueKey(price.getPriceSourceId(), price.getQuotedAt(), price.getPriceE8()), price);
			}
		}
	}

	private enum ImportedType {
		BUY(StockTransactionType.BUY, true, 1, "Kauf"),
		SELL(StockTransactionType.SELL, true, -1, "Verkauf"),
		DELIVERY_IN(StockTransactionType.DELIVERY_IN, true, 1, "Einlieferung"),
		DELIVERY_OUT(StockTransactionType.DELIVERY_OUT, true, -1, "Auslieferung"),
		DIVIDEND(StockTransactionType.DIVIDEND, false, 0, "Dividende"),
		INTEREST(StockTransactionType.INTEREST, false, 0, "Zinsen"),
		DEPOSIT(null, false, 0, "Einlage"),
		REMOVAL(null, false, 0, "Entnahme"),
		FEE(null, false, 0, "Gebühren"),
		TAX(null, false, 0, "Steuern"),
		TAX_REFUND(null, false, 0, "Steuererstattung");

		private final StockTransactionType stockType;
		private final boolean positionChange;
		private final int positionDirection;
		private final String displayName;

		ImportedType(StockTransactionType stockType, boolean positionChange, int positionDirection, String displayName) {
			this.stockType = stockType;
			this.positionChange = positionChange;
			this.positionDirection = positionDirection;
			this.displayName = displayName;
		}

		private static ImportedType from(String value) {
			String normalized = normalize(value).replace(" ", "_");
			return switch (normalized) {
			case "BUY", "KAUF" -> BUY;
			case "SELL", "VERKAUF" -> SELL;
			case "DELIVERY_INBOUND", "DELIVERY_IN", "EINLIEFERUNG" -> DELIVERY_IN;
			case "DELIVERY_OUTBOUND", "DELIVERY_OUT", "AUSLIEFERUNG" -> DELIVERY_OUT;
			case "DIVIDENDS", "DIVIDEND", "DIVIDENDE" -> DIVIDEND;
			case "INTEREST", "ZINSEN" -> INTEREST;
			case "DEPOSIT", "EINLAGE" -> DEPOSIT;
			case "REMOVAL", "ENTNAHME" -> REMOVAL;
			case "FEES", "FEE", "GEBÜHREN", "GEBUEHREN" -> FEE;
			case "TAXES", "TAX", "STEUERN" -> TAX;
			case "TAX_REFUND", "STEUERERSTATTUNG" -> TAX_REFUND;
			default -> throw new GBankingException("Nicht unterstützte Portfolio-Performance-Transaktionsart: " + value);
			};
		}

		private StockTransactionType stockType() {
			return stockType;
		}

		private boolean stockTransaction() {
			return stockType != null;
		}

		private boolean generalBookingOnly() {
			return stockType == null;
		}

		private boolean positionChange() {
			return positionChange;
		}

		private int positionDirection() {
			return positionDirection;
		}

		private String displayName() {
			return displayName;
		}
	}
}
