package de.zft2.gbanking.service.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVPrinter;

import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockNumericValueType;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionMetadata;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.gui.enu.ExportType;
import de.zft2.gbanking.service.AbstractDbService;
import de.zft2.gbanking.service.stock.StockPortfolioService.PortfolioSummary;

public class PortfolioPerformanceExportService extends AbstractDbService {

	private static final int QUANTITY_SCALE = StockNumericValueType.QUANTITY.getScaleDigits();
	private static final int PRICE_SCALE = StockNumericValueType.PRICE.getScaleDigits();
	private static final String[] TRANSACTION_HEADERS = { "Datum", "Typ", "Wert", "Buchungswährung",
			"Bruttobetrag", "Währung Bruttobetrag", "Wechselkurs", "Gebühren", "Steuern", "Stück", "ISIN",
			"WKN", "Ticker-Symbol", "Wertpapiername", "Notiz" };
	private static final String[] SECURITY_HEADERS = { "ISIN", "WKN", "Ticker-Symbol", "Wertpapiername", "Währung",
			"Notiz", "Gesamtkostenquote (TER)", "Fondsgröße", "Anbieter", "Kaufgebühr (prozentual)",
			"Verwaltungsgebühr (prozentual)" };

	public void exportFile(Path file, ExportType exportType, PortfolioSummary portfolio) throws IOException {
		requirePortfolio(portfolio);
		switch (exportType) {
		case STOCK_PP_TRANSACTIONS_CSV -> exportPortfolioTransactions(file, portfolio);
		case STOCK_PP_ACCOUNT_TRANSACTIONS_CSV -> exportAccountTransactions(file, portfolio);
		case STOCK_PP_SECURITIES_CSV -> exportSecurities(file, portfolio);
		case STOCK_PP_PRICES_CSV -> exportPrices(file, portfolio);
		default -> throw new GBankingException("Unbekanntes Portfolio-Performance-Exportformat: " + exportType);
		}
	}

	private void exportPortfolioTransactions(Path file, PortfolioSummary portfolio) throws IOException {
		ExportContext context = context(portfolio);
		try (CSVPrinter printer = PortfolioPerformanceCsvSupport.printer(file, TRANSACTION_HEADERS)) {
			for (StockTransaction transaction : context.transactions()) {
				if (!isPortfolioTransaction(transaction.getTransactionType())) {
					continue;
				}
				StockTransactionSecurityLeg securityLeg = context.securityLeg(transaction.getId());
				if (securityLeg == null) {
					continue;
				}
				StockSecurity security = context.securities().get(securityLeg.getSecurityId());
				SecurityIdentifiers identifiers = context.identifiers().getOrDefault(securityLeg.getSecurityId(),
						SecurityIdentifiers.EMPTY);
				CashSummary cash = context.cash(transaction.getId());
				BigDecimal portfolioValue = portfolioValue(transaction.getTransactionType(), cash.netAmount());
				printer.printRecord(PortfolioPerformanceCsvSupport.formatDateTime(transaction.getTradeAt()),
						typeName(transaction.getTransactionType()), PortfolioPerformanceCsvSupport.formatMoney(portfolioValue),
						currencyCode(cash.currency()), "", "", "", PortfolioPerformanceCsvSupport.formatMoney(cash.fees()),
						PortfolioPerformanceCsvSupport.formatMoney(cash.taxes()),
						PortfolioPerformanceCsvSupport.formatDecimal(quantity(securityLeg)), identifiers.isin(), identifiers.wkn(),
						identifiers.ticker(), security != null ? security.getName() : "", context.note(transaction.getId()));
			}
		}
	}

	private void exportAccountTransactions(Path file, PortfolioSummary portfolio) throws IOException {
		ExportContext context = context(portfolio);
		Map<Integer, StockTransaction> transactionsByBooking = context.transactionsByBooking();
		List<Booking> bookings = dbController.getAllByParent(Booking.class, portfolio.settlementAccountId()).stream()
				.sorted(Comparator.comparing(Booking::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparingInt(Booking::getId)).toList();
		try (CSVPrinter printer = PortfolioPerformanceCsvSupport.printer(file, TRANSACTION_HEADERS)) {
			for (Booking booking : bookings) {
				StockTransaction transaction = transactionsByBooking.get(booking.getId());
				StockTransactionSecurityLeg securityLeg = transaction != null ? context.securityLeg(transaction.getId()) : null;
				StockSecurity security = securityLeg != null ? context.securities().get(securityLeg.getSecurityId()) : null;
				SecurityIdentifiers identifiers = securityLeg != null
						? context.identifiers().getOrDefault(securityLeg.getSecurityId(), SecurityIdentifiers.EMPTY)
						: SecurityIdentifiers.EMPTY;
				CashSummary cash = transaction != null ? context.cash(transaction.getId()) : CashSummary.EMPTY;
				String type = transaction != null ? typeName(transaction.getTransactionType()) : bookingTypeName(booking);
				printer.printRecord(PortfolioPerformanceCsvSupport.formatDateTime(booking.getDate().atStartOfDay()), type,
						PortfolioPerformanceCsvSupport.formatMoney(booking.getAmount()),
						currencyCode(portfolio.settlementCurrency()), "", "", "",
						PortfolioPerformanceCsvSupport.formatMoney(cash.fees()),
						PortfolioPerformanceCsvSupport.formatMoney(cash.taxes()),
						securityLeg != null ? PortfolioPerformanceCsvSupport.formatDecimal(quantity(securityLeg)) : "",
						identifiers.isin(), identifiers.wkn(), identifiers.ticker(), security != null ? security.getName() : "",
						booking.getPurpose());
			}
		}
	}

	private void exportSecurities(Path file, PortfolioSummary portfolio) throws IOException {
		ExportContext context = context(portfolio);
		try (CSVPrinter printer = PortfolioPerformanceCsvSupport.printer(file, SECURITY_HEADERS)) {
			for (StockSecurity security : context.usedSecurities()) {
				SecurityIdentifiers identifiers = context.identifiers().getOrDefault(security.getId(), SecurityIdentifiers.EMPTY);
				printer.printRecord(identifiers.isin(), identifiers.wkn(), identifiers.ticker(), security.getName(),
						currencyCode(security.getDefaultQuoteCurrency()), "", "", "", nullToEmpty(security.getIssuer()), "", "");
			}
		}
	}

	private void exportPrices(Path file, PortfolioSummary portfolio) throws IOException {
		ExportContext context = context(portfolio);
		List<StockSecurity> securities = context.usedSecurities();
		Map<Integer, String> headers = uniquePriceHeaders(securities, context.identifiers());
		List<String> csvHeaders = new ArrayList<>();
		csvHeaders.add("Datum");
		securities.forEach(security -> csvHeaders.add(headers.get(security.getId())));
		Map<LocalDate, Map<Integer, StockSecurityPrice>> prices = pricesByDate(securities);
		try (CSVPrinter printer = PortfolioPerformanceCsvSupport.printer(file, csvHeaders.toArray(String[]::new))) {
			for (Map.Entry<LocalDate, Map<Integer, StockSecurityPrice>> dateEntry : prices.entrySet()) {
				List<String> row = new ArrayList<>();
				row.add(PortfolioPerformanceCsvSupport.formatDate(dateEntry.getKey()));
				for (StockSecurity security : securities) {
					StockSecurityPrice price = dateEntry.getValue().get(security.getId());
					row.add(price != null ? PortfolioPerformanceCsvSupport.formatDecimal(scaled(price.getPriceE8(), PRICE_SCALE)) : "");
				}
				printer.printRecord(row);
			}
		}
	}

	private ExportContext context(PortfolioSummary portfolio) {
		List<StockTransaction> transactions = dbController.getAllByParent(StockTransaction.class, portfolio.portfolioId()).stream()
				.filter(transaction -> transaction.getTransactionStatus() == StockTransactionStatus.SETTLED)
				.sorted(Comparator.comparing(StockTransaction::getTradeAt).thenComparingInt(StockTransaction::getId)).toList();
		Map<Integer, StockSecurity> securities = dbController.getAll(StockSecurity.class).stream()
				.collect(Collectors.toMap(StockSecurity::getId, Function.identity()));
		Map<Integer, SecurityIdentifiers> identifiers = identifiers();
		Map<Integer, List<StockTransactionSecurityLeg>> securityLegs = new HashMap<>();
		Map<Integer, List<StockTransactionCashLeg>> cashLegs = new HashMap<>();
		for (StockTransaction transaction : transactions) {
			securityLegs.put(transaction.getId(), dbController.getAllByParent(StockTransactionSecurityLeg.class, transaction.getId()));
			cashLegs.put(transaction.getId(), dbController.getAllByParent(StockTransactionCashLeg.class, transaction.getId()));
		}
		Map<Integer, String> notes = dbController.getAll(StockTransactionMetadata.class).stream()
				.collect(Collectors.toMap(StockTransactionMetadata::getTransactionId,
						metadata -> nullToEmpty(metadata.getNote()), (left, right) -> left));
		return new ExportContext(transactions, securities, identifiers, securityLegs, cashLegs, notes);
	}

	private Map<Integer, SecurityIdentifiers> identifiers() {
		Map<Integer, SecurityIdentifiersBuilder> builders = new HashMap<>();
		for (StockSecurityIdentifier identifier : dbController.getAll(StockSecurityIdentifier.class)) {
			if (identifier.getValidTo() != null) {
				continue;
			}
			SecurityIdentifiersBuilder builder = builders.computeIfAbsent(identifier.getSecurityId(), ignored -> new SecurityIdentifiersBuilder());
			if (identifier.getIdentifierType() == StockIdentifierType.ISIN) {
				builder.isin = identifier.getIdentifierValue();
			} else if (identifier.getIdentifierType() == StockIdentifierType.WKN) {
				builder.wkn = identifier.getIdentifierValue();
			} else if (identifier.getIdentifierType() == StockIdentifierType.TICKER) {
				builder.ticker = identifier.getIdentifierValue();
			}
		}
		return builders.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
				entry -> entry.getValue().build()));
	}

	private Map<LocalDate, Map<Integer, StockSecurityPrice>> pricesByDate(List<StockSecurity> securities) {
		Set<Integer> securityIds = securities.stream().map(StockSecurity::getId).collect(Collectors.toSet());
		Map<Integer, Integer> priceSourceToSecurity = dbController.getAll(StockSecurityPriceSource.class).stream()
				.filter(source -> securityIds.contains(source.getSecurityId()))
				.collect(Collectors.toMap(StockSecurityPriceSource::getId, StockSecurityPriceSource::getSecurityId));
		List<StockSecurityPrice> prices = dbController.getAll(StockSecurityPrice.class);
		Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		Map<LocalDate, Map<Integer, StockSecurityPrice>> result = new TreeMap<>();
		for (StockSecurityPrice price : prices) {
			Integer securityId = priceSourceToSecurity.get(price.getPriceSourceId());
			if (securityId == null || price.isDeleted() || supersededIds.contains(price.getId())) {
				continue;
			}
			Map<Integer, StockSecurityPrice> daily = result.computeIfAbsent(price.getQuotedAt().toLocalDate(), ignored -> new HashMap<>());
			daily.merge(securityId, price, (left, right) -> left.getId() > right.getId() ? left : right);
		}
		return result;
	}

	private static Map<Integer, String> uniquePriceHeaders(List<StockSecurity> securities,
			Map<Integer, SecurityIdentifiers> identifiers) {
		Map<Integer, String> result = new LinkedHashMap<>();
		Set<String> used = new LinkedHashSet<>();
		for (StockSecurity security : securities) {
			SecurityIdentifiers values = identifiers.getOrDefault(security.getId(), SecurityIdentifiers.EMPTY);
			String base = firstNonBlank(values.isin(), stripTickerSuffix(values.ticker()), values.wkn(), security.getName());
			String unique = base;
			if (!used.add(unique)) {
				unique = base + "_" + security.getId();
				used.add(unique);
			}
			result.put(security.getId(), unique);
		}
		return result;
	}

	private static boolean isPortfolioTransaction(StockTransactionType type) {
		return type == StockTransactionType.BUY || type == StockTransactionType.SELL
				|| type == StockTransactionType.DELIVERY_IN || type == StockTransactionType.DELIVERY_OUT
				|| type == StockTransactionType.TRANSFER_IN || type == StockTransactionType.TRANSFER_OUT;
	}

	private static String typeName(StockTransactionType type) {
		return switch (type) {
		case BUY -> "Kauf";
		case SELL -> "Verkauf";
		case DELIVERY_IN, TRANSFER_IN -> "Einlieferung";
		case DELIVERY_OUT, TRANSFER_OUT -> "Auslieferung";
		case DIVIDEND -> "Dividende";
		case INTEREST -> "Zinsen";
		case FEE -> "Gebühren";
		case TAX -> "Steuern";
		default -> type.name();
		};
	}

	private static String bookingTypeName(Booking booking) {
		if (booking.getBookingType() == BookingType.INTEREST) {
			return "Zinsen";
		}
		return booking.getAmount() != null && booking.getAmount().signum() < 0 ? "Entnahme" : "Einlage";
	}

	private static BigDecimal portfolioValue(StockTransactionType type, BigDecimal netAmount) {
		if (netAmount == null) {
			return null;
		}
		return type == StockTransactionType.SELL || type == StockTransactionType.DELIVERY_OUT
				|| type == StockTransactionType.TRANSFER_OUT ? netAmount.abs().negate() : netAmount.abs();
	}

	private static BigDecimal quantity(StockTransactionSecurityLeg leg) {
		return scaled(Math.abs(leg.getQuantityE9()), QUANTITY_SCALE);
	}

	private static BigDecimal scaled(long value, int scale) {
		return BigDecimal.valueOf(value, scale).stripTrailingZeros();
	}

	private static String currencyCode(Currency currency) {
		return currency != null ? currency.name() : "";
	}

	private static String stripTickerSuffix(String value) {
		int separator = value != null ? value.indexOf('.') : -1;
		return separator > 0 ? value.substring(0, separator) : value;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "Wertpapier";
	}

	private static String nullToEmpty(String value) {
		return value != null ? value : "";
	}

	private static void requirePortfolio(PortfolioSummary portfolio) {
		if (portfolio == null || portfolio.portfolioId() <= 0 || portfolio.settlementAccountId() <= 0) {
			throw new GBankingException("Bitte wählen Sie zuerst ein Wertpapier-Depot aus");
		}
	}

	private record SecurityIdentifiers(String isin, String wkn, String ticker) {
		private static final SecurityIdentifiers EMPTY = new SecurityIdentifiers("", "", "");
	}

	private static final class SecurityIdentifiersBuilder {
		private String isin = "";
		private String wkn = "";
		private String ticker = "";

		private SecurityIdentifiers build() {
			return new SecurityIdentifiers(isin, wkn, ticker);
		}
	}

	private record CashSummary(BigDecimal netAmount, BigDecimal fees, BigDecimal taxes, Currency currency) {
		private static final CashSummary EMPTY = new CashSummary(null, null, null, null);
	}

	private record ExportContext(List<StockTransaction> transactions, Map<Integer, StockSecurity> securities,
			Map<Integer, SecurityIdentifiers> identifiers, Map<Integer, List<StockTransactionSecurityLeg>> securityLegs,
			Map<Integer, List<StockTransactionCashLeg>> cashLegs, Map<Integer, String> notes) {

		private StockTransactionSecurityLeg securityLeg(int transactionId) {
			return securityLegs.getOrDefault(transactionId, List.of()).stream().findFirst().orElse(null);
		}

		private CashSummary cash(int transactionId) {
			List<StockTransactionCashLeg> legs = cashLegs.getOrDefault(transactionId, List.of());
			if (legs.isEmpty()) {
				return CashSummary.EMPTY;
			}
			Currency currency = legs.get(0).getCurrency();
			BigDecimal net = legs.stream().map(leg -> fromMinor(leg.getAmountMinor(), leg.getCurrency()))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal fees = sumRole(legs, StockCashLegRole.FEE);
			BigDecimal taxes = sumRole(legs, StockCashLegRole.TAX);
			return new CashSummary(net, fees, taxes, currency);
		}

		private String note(int transactionId) {
			return notes.getOrDefault(transactionId, "");
		}

		private List<StockSecurity> usedSecurities() {
			return securityLegs.values().stream().flatMap(List::stream).map(StockTransactionSecurityLeg::getSecurityId)
					.distinct().map(securities::get).filter(Objects::nonNull)
					.sorted(Comparator.comparing(StockSecurity::getName, String.CASE_INSENSITIVE_ORDER)).toList();
		}

		private Map<Integer, StockTransaction> transactionsByBooking() {
			Map<Integer, StockTransaction> transactionsById = transactions.stream()
					.collect(Collectors.toMap(StockTransaction::getId, Function.identity()));
			Map<Integer, StockTransaction> result = new HashMap<>();
			for (Map.Entry<Integer, List<StockTransactionCashLeg>> entry : cashLegs.entrySet()) {
				StockTransaction transaction = transactionsById.get(entry.getKey());
				if (transaction != null) {
					entry.getValue().stream().map(StockTransactionCashLeg::getBookingId).filter(Objects::nonNull)
							.forEach(bookingId -> result.putIfAbsent(bookingId, transaction));
				}
			}
			return result;
		}

		private static BigDecimal sumRole(List<StockTransactionCashLeg> legs, StockCashLegRole role) {
			BigDecimal value = legs.stream().filter(leg -> leg.getLegRole() == role)
					.map(leg -> fromMinor(leg.getAmountMinor(), leg.getCurrency()).abs())
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			return value.signum() == 0 ? null : value;
		}

		private static BigDecimal fromMinor(long value, Currency currency) {
			return BigDecimal.valueOf(value, currency.getMinorUnitDigits());
		}
	}
}
