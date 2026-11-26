package de.zft2.gbanking.service.stock;

import java.util.Collection;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;

public final class StockPriceHistory {

	public static final BinaryOperator<StockSecurityPrice> HIGHEST_ID =
			(first, second) -> first.getId() > second.getId() ? first : second;

	private StockPriceHistory() {
	}

	/** Resolves only the supplied history; tombstones must also suppress their predecessors. */
	public static Stream<StockSecurityPrice> effectivePrices(Collection<StockSecurityPrice> prices) {
		Set<Integer> supersededIds = prices.stream().map(StockSecurityPrice::getSupersedesPriceId)
				.filter(id -> id != null).collect(Collectors.toSet());
		return prices.stream().filter(price -> !price.isDeleted() && !supersededIds.contains(price.getId()));
	}
}
