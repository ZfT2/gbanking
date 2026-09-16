package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.enu.StockPriceBasis;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.stock.StockPortfolioPosition;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.AbstractDbService;

public class StockSecurityAdministrationService extends AbstractDbService {

	public List<SecurityDetails> getSecurities() {
		return getSecurities(false);
	}

	public List<SecurityDetails> getSecurities(boolean onlyWithHoldings) {
		Map<Integer, IdentifierValues> identifiers = identifiersBySecurity();
		Set<Integer> securitiesWithHoldings = onlyWithHoldings ? securitiesWithHoldings() : Set.of();
		return dbController.getAll(StockSecurity.class).stream()
				.filter(security -> !onlyWithHoldings || securitiesWithHoldings.contains(security.getId()))
				.map(security -> toDetails(security, identifiers.getOrDefault(security.getId(), IdentifierValues.EMPTY)))
				.sorted(Comparator.comparing(SecurityDetails::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private Set<Integer> securitiesWithHoldings() {
		return dbController.getAll(StockPortfolioPosition.class).stream()
				.filter(position -> position.getQuantityE9() != 0)
				.map(StockPortfolioPosition::getSecurityId)
				.collect(Collectors.toSet());
	}

	public SecurityDetails save(SecuritySaveRequest request) {
		validate(request);
		int securityId = dbController.executeInTransaction(() -> saveInTransaction(request));
		return getSecurities().stream().filter(security -> security.securityId() == securityId).findFirst()
				.orElseThrow(() -> new GBankingException("Das gespeicherte Wertpapier wurde nicht gefunden"));
	}

	private int saveInTransaction(SecuritySaveRequest request) {
		StockSecurity security = request.securityId() != null
				? dbController.getById(StockSecurity.class, request.securityId()) : new StockSecurity();
		if (security == null) {
			throw new GBankingException("Das Wertpapier wurde nicht gefunden");
		}
		applyValues(security, request);
		dbController.insertOrUpdate(security);
		synchronizeIdentifier(security.getId(), StockIdentifierType.ISIN, request.isin(), null);
		synchronizeIdentifier(security.getId(), StockIdentifierType.WKN, request.wkn(), null);
		synchronizeIdentifier(security.getId(), StockIdentifierType.TICKER, request.ticker(), request.marketIdentifierCode());
		return security.getId();
	}

	private static void applyValues(StockSecurity security, SecuritySaveRequest request) {
		security.setName(trimToNull(request.name()));
		security.setSecurityType(request.securityType());
		security.setIssuer(trimToNull(request.issuer()));
		security.setDomicileCountry(upper(request.domicileCountry()));
		security.setMaturityDate(request.maturityDate());
		security.setDefaultQuantityType(request.quantityType());
		security.setNominalCurrency(request.nominalCurrency());
		security.setDefaultQuoteCurrency(request.quoteCurrency());
		security.setDefaultQuotationType(request.quotationType());
		security.setDefaultPriceBasis(request.quotationType() == StockQuotationType.PERCENT_OF_NOMINAL
				? request.priceBasis() : null);
		security.setSecurityState(request.securityState());
	}

	private void synchronizeIdentifier(int securityId, StockIdentifierType type, String requestedValue,
			String requestedMarketIdentifierCode) {
		String value = upper(requestedValue);
		String marketIdentifierCode = upper(requestedMarketIdentifierCode);
		List<StockSecurityIdentifier> currentIdentifiers = dbController
				.getAllByParent(StockSecurityIdentifier.class, securityId).stream()
				.filter(identifier -> identifier.getIdentifierType() == type && identifier.getValidTo() == null)
				.sorted(Comparator.comparingInt(StockSecurityIdentifier::getId)).toList();
		StockSecurityIdentifier current = currentIdentifiers.isEmpty() ? null : currentIdentifiers.get(0);
		if (value == null) {
			if (current != null) {
				dbController.delete(current, null);
			}
			return;
		}

		validateUniqueIdentifier(securityId, type, value, marketIdentifierCode);
		StockSecurityIdentifier identifier = current != null ? current : new StockSecurityIdentifier();
		identifier.setSecurityId(securityId);
		identifier.setIdentifierType(type);
		identifier.setIdentifierValue(value);
		identifier.setMarketIdentifierCode(type == StockIdentifierType.TICKER ? marketIdentifierCode : null);
		dbController.insertOrUpdate(identifier);
	}

	private void validateUniqueIdentifier(int securityId, StockIdentifierType type, String value,
			String marketIdentifierCode) {
		boolean duplicate = dbController.getAll(StockSecurityIdentifier.class).stream()
				.filter(identifier -> identifier.getSecurityId() != securityId && identifier.getValidTo() == null)
				.filter(identifier -> identifier.getIdentifierType() == type)
				.filter(identifier -> value.equalsIgnoreCase(identifier.getIdentifierValue()))
				.anyMatch(identifier -> type != StockIdentifierType.TICKER
						|| equalIgnoreCase(marketIdentifierCode, identifier.getMarketIdentifierCode()));
		if (duplicate) {
			throw new GBankingException(type + " " + value + " ist bereits einem anderen Wertpapier zugeordnet");
		}
	}

	private void validate(SecuritySaveRequest request) {
		if (request == null || trimToNull(request.name()) == null) {
			throw new GBankingException("Die Wertpapierbezeichnung muss angegeben werden");
		}
		if (request.securityType() == null || request.quantityType() == null
				|| request.quotationType() == null || request.securityState() == null) {
			throw new GBankingException("Typ, Bestandsart, Notierungsart und Status müssen angegeben werden");
		}
		if (request.quoteCurrency() == null) {
			throw new GBankingException("Die Kurswährung muss angegeben werden");
		}
		validateLength(request.domicileCountry(), 2, "Das Domizilland muss als zweistelliger ISO-Code angegeben werden");
		validateLength(request.isin(), 12, "Die ISIN muss zwölf Zeichen enthalten");
		validateLength(request.wkn(), 6, "Die WKN muss sechs Zeichen enthalten");
		validateLength(request.marketIdentifierCode(), 4, "Der Börsenplatz muss als vierstelliger MIC angegeben werden");
		if (trimToNull(request.marketIdentifierCode()) != null && trimToNull(request.ticker()) == null) {
			throw new GBankingException("Ein Börsenplatz kann nur zusammen mit einem Ticker angegeben werden");
		}
		if (request.quotationType() == StockQuotationType.PERCENT_OF_NOMINAL
				&& (request.quantityType() != StockQuantityType.NOMINAL
						|| request.nominalCurrency() == null || request.priceBasis() == null)) {
			throw new GBankingException("Prozentnotierte Wertpapiere benötigen Nominale, Nominalwährung und Preisbasis");
		}
	}

	private static void validateLength(String value, int expectedLength, String message) {
		String normalized = trimToNull(value);
		if (normalized != null && normalized.length() != expectedLength) {
			throw new GBankingException(message);
		}
	}

	private Map<Integer, IdentifierValues> identifiersBySecurity() {
		LocalDate today = LocalDate.now();
		Map<Integer, EnumMap<StockIdentifierType, StockSecurityIdentifier>> identifiers = dbController
				.getAll(StockSecurityIdentifier.class).stream()
				.filter(identifier -> identifier.getValidTo() == null)
				.filter(identifier -> identifier.getValidFrom() == null || !identifier.getValidFrom().isAfter(today))
				.sorted(Comparator.comparingInt(StockSecurityIdentifier::getId))
				.collect(Collectors.groupingBy(StockSecurityIdentifier::getSecurityId,
						Collectors.toMap(StockSecurityIdentifier::getIdentifierType, Function.identity(),
								(first, ignored) -> first, () -> new EnumMap<>(StockIdentifierType.class))));
		return identifiers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
				entry -> toIdentifierValues(entry.getValue())));
	}

	private static IdentifierValues toIdentifierValues(Map<StockIdentifierType, StockSecurityIdentifier> identifiers) {
		StockSecurityIdentifier ticker = identifiers.get(StockIdentifierType.TICKER);
		return new IdentifierValues(identifierValue(identifiers, StockIdentifierType.ISIN),
				identifierValue(identifiers, StockIdentifierType.WKN),
				ticker != null ? value(ticker.getIdentifierValue()) : "",
				ticker != null ? value(ticker.getMarketIdentifierCode()) : "");
	}

	private static String identifierValue(Map<StockIdentifierType, StockSecurityIdentifier> identifiers,
			StockIdentifierType type) {
		StockSecurityIdentifier identifier = identifiers.get(type);
		return identifier != null ? value(identifier.getIdentifierValue()) : "";
	}

	private static SecurityDetails toDetails(StockSecurity security, IdentifierValues identifiers) {
		return new SecurityDetails(security.getId(), value(security.getName()), value(security.getIssuer()),
				value(security.getDomicileCountry()), security.getMaturityDate(), security.getSecurityType(),
				security.getDefaultQuantityType(), security.getNominalCurrency(), security.getDefaultQuoteCurrency(),
				security.getDefaultQuotationType(), security.getDefaultPriceBasis(), security.getSecurityState(),
				identifiers.isin(), identifiers.wkn(), identifiers.ticker(), identifiers.marketIdentifierCode(),
				security.getCreatedAt(), security.getModifiedAt());
	}

	private static String upper(String value) {
		String normalized = trimToNull(value);
		return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
	}

	private static boolean equalIgnoreCase(String first, String second) {
		String left = trimToNull(first);
		String right = trimToNull(second);
		return left == null ? right == null : right != null && left.equalsIgnoreCase(right);
	}

	private static String value(String value) {
		return value != null ? value : "";
	}

	public record SecurityDetails(int securityId, String name, String issuer, String domicileCountry,
			LocalDate maturityDate, StockSecurityType securityType, StockQuantityType quantityType,
			Currency nominalCurrency, Currency quoteCurrency, StockQuotationType quotationType,
			StockPriceBasis priceBasis, StockSecurityState securityState, String isin, String wkn,
			String ticker, String marketIdentifierCode, LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	public record SecuritySaveRequest(Integer securityId, String name, String issuer, String domicileCountry,
			LocalDate maturityDate, StockSecurityType securityType, StockQuantityType quantityType,
			Currency nominalCurrency, Currency quoteCurrency, StockQuotationType quotationType,
			StockPriceBasis priceBasis, StockSecurityState securityState, String isin, String wkn,
			String ticker, String marketIdentifierCode) {
	}

	private record IdentifierValues(String isin, String wkn, String ticker, String marketIdentifierCode) {
		private static final IdentifierValues EMPTY = new IdentifierValues("", "", "", "");
	}
}
