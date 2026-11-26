package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityPrice;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;

class StockPriceHistoryTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void effectivePricesShouldResolveUnsortedChainsAcrossSourcesAndDates(boolean deleted) {
		StockSecurityPrice original = price(1, null);
		StockSecurityPrice correction = price(2, 1);
		correction.setPriceSourceId(2);
		correction.setQuotedAt(correction.getQuotedAt().plusDays(1));
		StockSecurityPrice successor = price(3, 2);
		successor.setDeleted(deleted);
		StockSecurityPrice otherOriginal = price(4, null);
		StockSecurityPrice otherCorrection = price(5, 4);
		StockSecurityPrice independent = price(6, null);
		List<StockSecurityPrice> history = List.of(successor, independent, original, correction,
				otherCorrection, otherOriginal);

		assertEquals(deleted ? List.of(independent, otherCorrection) : List.of(successor, independent, otherCorrection),
				StockPriceHistory.effectivePrices(history).toList());
		assertEquals(List.of(), StockPriceHistory.effectivePrices(List.of()).toList());
	}

	private static StockSecurityPrice price(int id, Integer supersedesId) {
		StockSecurityPrice price = createSecurityPrice(1, LocalDate.of(2024, Month.JANUARY, 15).atStartOfDay(),
				1_000_000_000L);
		price.setId(id);
		price.setSupersedesPriceId(supersedesId);
		return price;
	}
}
