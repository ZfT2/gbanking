package de.zft2.gbanking.service.stock.quote;

import de.zft2.gbanking.BaseMessages;

public enum QuoteProvider {

	ALPHA_VANTAGE("UI_QUOTE_PROVIDER_ALPHA_VANTAGE", "ALPHA_VANTAGE"),
	EODHD("UI_QUOTE_PROVIDER_EODHD", "EODHD"),
	ALPACA("UI_QUOTE_PROVIDER_ALPACA", "ALPACA"),
	GENERIC("UI_QUOTE_PROVIDER_GENERIC", "GENERIC_QUOTE_FEED");

	private final String displayNameKey;
	private final String sourceCode;

	QuoteProvider(String displayNameKey, String sourceCode) {
		this.displayNameKey = displayNameKey;
		this.sourceCode = sourceCode;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	@Override
	public String toString() {
		return BaseMessages.getTextStatic(displayNameKey);
	}
}
