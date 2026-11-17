package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.util.TextValues.nullToEmpty;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import de.zft2.gbanking.db.DbExecutor;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockNumericValueType;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockImportRecord;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.service.stock.PortfolioPerformanceData.Price;

final class PortfolioPerformancePriceImporter {

	private final DbExecutor dbExecutor;
	private final Map<PriceSourceKey, StockSecurityPriceSource> priceSources = new HashMap<>();
	private final Map<PriceDateKey, StockSecurityPrice> latestPriceByDate = new HashMap<>();
	private final Map<PriceValueKey, StockSecurityPrice> pricesByValue = new HashMap<>();

	PortfolioPerformancePriceImporter(DbExecutor dbExecutor) {
		this.dbExecutor = dbExecutor;
		Map<Integer, StockSecurityPriceSource> sourcesById = new HashMap<>();
		for (StockSecurityPriceSource priceSource : dbExecutor.getAll(StockSecurityPriceSource.class)) {
			priceSources.put(new PriceSourceKey(priceSource.getSecurityId(), priceSource.getSourceId(),
					priceSource.getProviderSymbol()), priceSource);
			sourcesById.put(priceSource.getId(), priceSource);
		}
		for (StockSecurityPrice price : dbExecutor.getAll(StockSecurityPrice.class)) {
			if (!sourcesById.containsKey(price.getPriceSourceId()) || price.getPriceType() != StockPriceType.CLOSE) {
				continue;
			}
			PriceDateKey dateKey = new PriceDateKey(price.getPriceSourceId(), price.getQuotedAt());
			latestPriceByDate.merge(dateKey, price, (left, right) -> left.getId() > right.getId() ? left : right);
			pricesByValue.put(new PriceValueKey(price.getPriceSourceId(), price.getQuotedAt(), price.getPriceE8()), price);
		}
	}

	boolean savePrice(StockSecurity security, Price priceData, StockDataSource source,
			StockImportRecord importRecord, String providerSymbol) {
		Currency currency = security.getDefaultQuoteCurrency();
		if (currency == null) {
			throw new GBankingException("Für das Wertpapier '" + security.getName() + "' fehlt die Kurswährung");
		}
		StockSecurityPriceSource priceSource = getOrCreatePriceSource(security, source);
		long priceE8 = scaledPrice(priceData);
		LocalDateTime quotedAt = priceData.date().atStartOfDay();
		PriceValueKey valueKey = new PriceValueKey(priceSource.getId(), quotedAt, priceE8);
		if (pricesByValue.containsKey(valueKey)) {
			return false;
		}
		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setImportRecordId(importRecord.getId());
		price.setQuotedAt(quotedAt);
		price.setPriceE8(priceE8);
		price.setQuoteCurrency(currency);
		price.setQuotationType(StockQuotationType.ABSOLUTE);
		price.setPriceType(StockPriceType.CLOSE);
		price.setExternalReference(priceData.date() + ":" + nullToEmpty(providerSymbol));
		PriceDateKey dateKey = new PriceDateKey(priceSource.getId(), quotedAt);
		StockSecurityPrice previous = latestPriceByDate.get(dateKey);
		if (previous != null) {
			price.setSupersedesPriceId(previous.getId());
		}
		price = dbExecutor.insertOrUpdate(price);
		pricesByValue.put(valueKey, price);
		latestPriceByDate.put(dateKey, price);
		return true;
	}

	private StockSecurityPriceSource getOrCreatePriceSource(StockSecurity security, StockDataSource source) {
		PriceSourceKey key = new PriceSourceKey(security.getId(), source.getId(), null);
		return priceSources.computeIfAbsent(key, ignored -> StockSources.createPriceSource(dbExecutor,
				security.getId(), source.getId(), null, null, source.getDefaultPriority()));
	}

	private static long scaledPrice(Price price) {
		try {
			return StockNumericValues.toScaledLong(price.value(), StockNumericValueType.PRICE.getScaleDigits(), RoundingMode.HALF_UP);
		} catch (ArithmeticException exception) {
			throw new GBankingException("Kurs ist zu groß", exception);
		}
	}

	private record PriceSourceKey(int securityId, int sourceId, String providerSymbol) {
	}

	private record PriceDateKey(int priceSourceId, LocalDateTime quotedAt) {
	}

	private record PriceValueKey(int priceSourceId, LocalDateTime quotedAt, long priceE8) {
	}
}
