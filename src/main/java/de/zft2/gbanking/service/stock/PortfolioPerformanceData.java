package de.zft2.gbanking.service.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.zft2.gbanking.db.dao.enu.Currency;

final class PortfolioPerformanceData {

	private PortfolioPerformanceData() {
	}

	record Security(String externalId, String name, Currency currency, String isin, String wkn, String ticker,
			String issuer, List<Price> prices) {
	}

	record Price(LocalDate date, BigDecimal value) {
	}

	record Transaction(String externalId, LocalDateTime date, String type, BigDecimal value,
			Currency bookingCurrency, BigDecimal grossValue, Currency grossCurrency, BigDecimal exchangeRate,
			BigDecimal fees, BigDecimal taxes, BigDecimal shares, String securityExternalId,
			String isin, String wkn, String ticker, String securityName, String note) {
	}

	record Account(String externalId, String name, Currency currency, boolean retired,
			List<Transaction> transactions) {
	}

	record Portfolio(String externalId, String name, boolean retired, String settlementAccountExternalId,
			List<Transaction> transactions) {
	}

	record Document(String version, List<Security> securities, List<Portfolio> portfolios, List<Account> accounts) {
		List<Account> additionalAccounts() {
			Set<String> linkedAccountIds = portfolios.stream().map(Portfolio::settlementAccountExternalId)
					.filter(id -> id != null).collect(Collectors.toSet());
			return accounts.stream().filter(account -> !linkedAccountIds.contains(account.externalId())).toList();
		}
	}
}
