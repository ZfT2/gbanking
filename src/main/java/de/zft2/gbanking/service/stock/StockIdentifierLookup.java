package de.zft2.gbanking.service.stock;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import de.zft2.gbanking.db.dao.enu.StockIdentifierType;
import de.zft2.gbanking.db.dao.stock.StockSecurityIdentifier;

/** Indexes loaded identifiers with explicit eligibility and selection rules. */
public final class StockIdentifierLookup {

	public enum Selection {
		FIRST_IN_ORDER, LAST_IN_ORDER, LOWEST_ID;

		private StockSecurityIdentifier choose(StockSecurityIdentifier first, StockSecurityIdentifier next) {
			return switch (this) {
			case FIRST_IN_ORDER -> first;
			case LAST_IN_ORDER -> next;
			case LOWEST_ID -> first.getId() <= next.getId() ? first : next;
			};
		}
	}

	private final Map<Integer, Map<StockIdentifierType, StockSecurityIdentifier>> identifiers = new HashMap<>();

	private StockIdentifierLookup(Collection<StockSecurityIdentifier> rows, Predicate<StockSecurityIdentifier> eligible,
			Selection selection) {
		Objects.requireNonNull(selection, "selection");
		for (StockSecurityIdentifier identifier : rows) {
			if (eligible.test(identifier)) {
				identifiers.computeIfAbsent(identifier.getSecurityId(), ignored -> new EnumMap<>(StockIdentifierType.class))
						.merge(identifier.getIdentifierType(), identifier, (first, next) -> selection.choose(first, next));
			}
		}
	}

	public static StockIdentifierLookup all(Collection<StockSecurityIdentifier> rows, Selection selection) {
		return new StockIdentifierLookup(rows, identifier -> true, selection);
	}

	public static StockIdentifierLookup openEnded(Collection<StockSecurityIdentifier> rows, Selection selection) {
		return new StockIdentifierLookup(rows, identifier -> identifier.getValidTo() == null, selection);
	}

	public static StockIdentifierLookup startedAndOpenEnded(Collection<StockSecurityIdentifier> rows, LocalDate asOf,
			Selection selection) {
		Objects.requireNonNull(asOf, "asOf");
		return new StockIdentifierLookup(rows, identifier -> identifier.getValidTo() == null
				&& (identifier.getValidFrom() == null || !identifier.getValidFrom().isAfter(asOf)), selection);
	}

	public Identifiers get(int securityId, String missingValue) {
		Map<StockIdentifierType, StockSecurityIdentifier> selected = identifiers.getOrDefault(securityId, Map.of());
		StockSecurityIdentifier ticker = selected.get(StockIdentifierType.TICKER);
		return new Identifiers(value(selected.get(StockIdentifierType.ISIN), missingValue),
				value(selected.get(StockIdentifierType.WKN), missingValue), value(ticker, missingValue),
				ticker != null ? Objects.toString(ticker.getMarketIdentifierCode(), missingValue) : missingValue);
	}

	private static String value(StockSecurityIdentifier identifier, String missingValue) {
		return identifier != null ? Objects.toString(identifier.getIdentifierValue(), missingValue) : missingValue;
	}

	public record Identifiers(String isin, String wkn, String ticker, String marketIdentifierCode) {
		public static final Identifiers EMPTY = new Identifiers("", "", "", "");
	}
}
