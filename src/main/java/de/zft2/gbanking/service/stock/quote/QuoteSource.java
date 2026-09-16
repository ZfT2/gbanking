package de.zft2.gbanking.service.stock.quote;

import java.util.Objects;

public record QuoteSource(QuoteProvider provider, GenericQuoteFeed genericFeed) {

	public QuoteSource {
		Objects.requireNonNull(provider, "provider");
		if ((provider == QuoteProvider.GENERIC) != (genericFeed != null)) {
			throw new IllegalArgumentException("A generic quote source requires exactly one feed");
		}
	}

	public static QuoteSource of(QuoteProvider provider) {
		return new QuoteSource(provider, null);
	}

	public static QuoteSource of(GenericQuoteFeed feed) {
		return new QuoteSource(QuoteProvider.GENERIC, Objects.requireNonNull(feed, "feed"));
	}

	public String sourceCode() {
		return genericFeed != null ? genericFeed.sourceCode() : provider.getSourceCode();
	}

	@Override
	public String toString() {
		return genericFeed != null ? genericFeed.name() : provider.toString();
	}
}
