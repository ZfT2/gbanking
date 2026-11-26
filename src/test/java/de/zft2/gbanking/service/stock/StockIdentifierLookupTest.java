package de.zft2.gbanking.service.stock;

import static de.zft2.gbanking.testdata.TestDataFactory.createSecurityIdentifier;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;
import de.zft2.gbanking.service.stock.StockIdentifierLookup.Identifiers;
import de.zft2.gbanking.service.stock.StockIdentifierLookup.Selection;

class StockIdentifierLookupTest {

	@ParameterizedTest
	@EnumSource(Selection.class)
	void selectionShouldDistinguishInputOrderFromIdAndKeepTickerAndMarketTogether(Selection selection) {
		StockSecurityIdentifier first = ticker(30, " First.DE ", "XETR");
		StockSecurityIdentifier lowest = ticker(10, "Second", "XNYS");
		StockSecurityIdentifier last = ticker(20, "Last", "XLON");
		var other = createSecurityIdentifier(2, StockIdentifierType.ISIN, "DE0000000001", null, null);
		StockIdentifierLookup lookup = StockIdentifierLookup.all(List.of(first, lowest, last, other), selection);
		StockSecurityIdentifier expected = switch (selection) {
		case FIRST_IN_ORDER -> first;
		case LAST_IN_ORDER -> last;
		case LOWEST_ID -> lowest;
		};

		assertEquals(new Identifiers(null, null, expected.getIdentifierValue(), expected.getMarketIdentifierCode()),
				lookup.get(1, null));
		assertEquals(new Identifiers("DE0000000001", "", "", ""), lookup.get(2, ""));
		assertEquals(Identifiers.EMPTY, lookup.get(99, ""));
	}

	@Test
	void validityRulesShouldKeepFutureEndsExcludedAndIncludeTheStartDate() {
		LocalDate asOf = LocalDate.of(2024, Month.JANUARY, 15);
		List<StockSecurityIdentifier> rows = List.of(
				createSecurityIdentifier(1, StockIdentifierType.ISIN, "Expired", asOf.minusDays(2), asOf.minusDays(1)),
				createSecurityIdentifier(1, StockIdentifierType.ISIN, "Future end", asOf.minusDays(1), asOf.plusDays(1)),
				createSecurityIdentifier(1, StockIdentifierType.ISIN, "Future start", asOf.plusDays(1), null),
				createSecurityIdentifier(1, StockIdentifierType.ISIN, "Today", asOf, null),
				createSecurityIdentifier(1, StockIdentifierType.ISIN, "Undated", null, null));

		assertEquals("Expired", StockIdentifierLookup.all(rows, Selection.FIRST_IN_ORDER).get(1, null).isin());
		assertEquals("Future start", StockIdentifierLookup.openEnded(rows, Selection.FIRST_IN_ORDER).get(1, null).isin());
		assertEquals("Today", StockIdentifierLookup.startedAndOpenEnded(rows, asOf, Selection.FIRST_IN_ORDER).get(1, null).isin());
		assertEquals("Undated", StockIdentifierLookup.startedAndOpenEnded(rows, asOf, Selection.LAST_IN_ORDER).get(1, null).isin());
		assertEquals(new Identifiers(null, null, null, null),
				StockIdentifierLookup.all(List.of(), Selection.FIRST_IN_ORDER).get(1, null));
	}

	private static StockSecurityIdentifier ticker(int id, String symbol, String market) {
		StockSecurityIdentifier identifier = createSecurityIdentifier(1, StockIdentifierType.TICKER, symbol, null, null);
		identifier.setId(id);
		identifier.setMarketIdentifierCode(market);
		return identifier;
	}
}
