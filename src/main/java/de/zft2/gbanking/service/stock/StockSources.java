package de.zft2.gbanking.service.stock;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.zft2.gbanking.db.DbExecutor;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;

/**
 * Central helper for the lookup and the creation of stock data sources and price sources.
 *
 * <p>
 * The stock services import transactions and prices from different sources. All of them have to
 * look up the source first and to create it if it does not exist yet. The lookup criteria differ
 * between the services, so they are passed explicitly instead of being hard coded here.
 * </p>
 */
public final class StockSources {

	private StockSources() {
	}

	/**
	 * Searches the data source with the given code, ignoring the case of the code.
	 *
	 * @param sources    the known data sources
	 * @param sourceCode the code of the searched data source
	 * @return the found data source, if any
	 */
	public static Optional<StockDataSource> findDataSource(List<StockDataSource> sources, String sourceCode) {
		return sources.stream().filter(source -> sourceCode.equalsIgnoreCase(source.getSourceCode())).findFirst();
	}

	/**
	 * Returns the data source with the given code and creates it if it does not exist yet.
	 *
	 * @param dbExecutor     the executor used to read and to write the data sources
	 * @param sourceCode     the code of the data source
	 * @param sourceName     the display name of the data source
	 * @param sourceType     the type of the data source
	 * @param defaultPriority the priority of the data source
	 * @return the existing or the newly created data source
	 */
	public static StockDataSource getOrCreateDataSource(DbExecutor dbExecutor, String sourceCode, String sourceName,
			StockDataSourceType sourceType, int defaultPriority) {
		return findDataSource(dbExecutor.getAll(StockDataSource.class), sourceCode)
				.orElseGet(() -> createDataSource(dbExecutor, sourceCode, sourceName, sourceType, defaultPriority));
	}

	/**
	 * Creates a new data source.
	 *
	 * @param dbExecutor      the executor used to write the data source
	 * @param sourceCode      the code of the data source
	 * @param sourceName      the display name of the data source
	 * @param sourceType      the type of the data source
	 * @param defaultPriority the priority of the data source
	 * @return the newly created data source
	 */
	public static StockDataSource createDataSource(DbExecutor dbExecutor, String sourceCode, String sourceName,
			StockDataSourceType sourceType, int defaultPriority) {
		StockDataSource source = new StockDataSource();
		source.setSourceCode(sourceCode);
		source.setSourceName(sourceName);
		source.setSourceType(sourceType);
		source.setDefaultPriority(defaultPriority);
		return dbExecutor.insertOrUpdate(source);
	}

	/**
	 * Searches the price source of a security for the given data source, provider symbol and market
	 * identifier code.
	 *
	 * @param candidates          the known price sources of the security
	 * @param sourceId            the id of the data source
	 * @param providerSymbol      the expected provider symbol, {@code null} expects no symbol
	 * @param marketIdentifierCode the expected market identifier code, {@code null} expects no code
	 * @return the found price source, if any
	 */
	public static Optional<StockSecurityPriceSource> findPriceSource(List<StockSecurityPriceSource> candidates,
			int sourceId, String providerSymbol, String marketIdentifierCode) {
		return candidates.stream()
				.filter(candidate -> candidate.getSourceId() == sourceId)
				.filter(candidate -> matches(candidate.getProviderSymbol(), providerSymbol))
				.filter(candidate -> Objects.equals(candidate.getMarketIdentifierCode(), marketIdentifierCode))
				.findFirst();
	}

	/**
	 * Returns the price source of a security and creates it if it does not exist yet.
	 *
	 * @param dbExecutor           the executor used to read and to write the price sources
	 * @param securityId           the id of the security
	 * @param sourceId             the id of the data source
	 * @param providerSymbol       the provider symbol of the price source, may be {@code null}
	 * @param marketIdentifierCode the market identifier code of the price source, may be {@code null}
	 * @param priority             the priority of the price source
	 * @return the existing or the newly created price source
	 */
	public static StockSecurityPriceSource getOrCreatePriceSource(DbExecutor dbExecutor, int securityId, int sourceId,
			String providerSymbol, String marketIdentifierCode, int priority) {
		List<StockSecurityPriceSource> candidates = dbExecutor.getAllByParent(StockSecurityPriceSource.class, securityId);
		return findPriceSource(candidates, sourceId, providerSymbol, marketIdentifierCode)
				.orElseGet(() -> createPriceSource(dbExecutor, securityId, sourceId, providerSymbol, marketIdentifierCode,
						priority));
	}

	/**
	 * Creates a new price source for a security.
	 *
	 * @param dbExecutor           the executor used to write the price source
	 * @param securityId           the id of the security
	 * @param sourceId             the id of the data source
	 * @param providerSymbol       the provider symbol of the price source, may be {@code null}
	 * @param marketIdentifierCode the market identifier code of the price source, may be {@code null}
	 * @param priority             the priority of the price source
	 * @return the newly created price source
	 */
	public static StockSecurityPriceSource createPriceSource(DbExecutor dbExecutor, int securityId, int sourceId,
			String providerSymbol, String marketIdentifierCode, int priority) {
		StockSecurityPriceSource priceSource = new StockSecurityPriceSource();
		priceSource.setSecurityId(securityId);
		priceSource.setSourceId(sourceId);
		priceSource.setProviderSymbol(providerSymbol);
		priceSource.setMarketIdentifierCode(marketIdentifierCode);
		priceSource.setPriority(priority);
		return dbExecutor.insertOrUpdate(priceSource);
	}

	/**
	 * Compares a stored text with the expected one, ignoring the case of the text.
	 *
	 * @param value    the stored text
	 * @param expected the expected text
	 * @return {@code true} if both texts are equal
	 */
	private static boolean matches(String value, String expected) {
		return expected == null ? value == null : expected.equalsIgnoreCase(value);
	}
}
