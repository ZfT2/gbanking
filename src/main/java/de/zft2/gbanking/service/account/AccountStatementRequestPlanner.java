package de.zft2.gbanking.service.account;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.zft2.gbanking.db.dao.BankAccountStatement;

final class AccountStatementRequestPlanner {

	private static final Logger log = LogManager.getLogger(AccountStatementRequestPlanner.class);

	private static final Set<String> OVERVIEW_RESULT_FIELDS = Set.of("number", "acknowledgement", "retrievable", "year", "date", "time",
			"creationtype", "documentid");
	private static final int FIRST_RETRIEVAL_LOOKBACK_YEARS = 4;
	private static final int REDOWNLOAD_LOOKBACK_MONTHS = FIRST_RETRIEVAL_LOOKBACK_YEARS * 12;

	private AccountStatementRequestPlanner() {
	}

	static List<StatementRequest> createRedownloadRequests(List<BankAccountStatement> storedStatements, YearMonth startMonth) {
		List<StatementRequest> statementRequests = new ArrayList<>();
		Set<String> requestedStatementIds = new HashSet<>();
		addStoredRedownloadRequests(statementRequests, requestedStatementIds, storedStatements);
		addFallbackRedownloadRequests(statementRequests, requestedStatementIds, startMonth);
		return statementRequests;
	}

	private static void addStoredRedownloadRequests(List<StatementRequest> statementRequests, Set<String> requestedStatementIds,
			List<BankAccountStatement> storedStatements) {
		List<BankAccountStatement> acknowledgedStatements = new ArrayList<>();
		if (storedStatements != null) {
			for (BankAccountStatement statement : storedStatements) {
				if (isRedownloadableStoredStatement(statement)) {
					acknowledgedStatements.add(statement);
				}
			}
		}

		acknowledgedStatements.sort((left, right) -> {
			int yearCompare = Integer.compare(right.getYear(), left.getYear());
			return yearCompare != 0 ? yearCompare : Integer.compare(right.getNumber(), left.getNumber());
		});

		for (BankAccountStatement statement : acknowledgedStatements) {
			addRequest(statementRequests, requestedStatementIds,
					new StatementRequest(toRequestYear(statement.getYear()), statement.getNumber(), true));
		}
	}

	private static boolean isRedownloadableStoredStatement(BankAccountStatement statement) {
		return statement != null && statement.isAcknowledged() && statement.getNumber() > 0;
	}

	private static void addFallbackRedownloadRequests(List<StatementRequest> statementRequests, Set<String> requestedStatementIds,
			YearMonth startMonth) {
		YearMonth statementMonth = startMonth != null ? startMonth : YearMonth.now(ZoneId.systemDefault()).minusMonths(1);
		for (int monthOffset = 0; monthOffset < REDOWNLOAD_LOOKBACK_MONTHS; monthOffset++) {
			addRequest(statementRequests, requestedStatementIds,
					new StatementRequest(statementMonth.getYear(), statementMonth.getMonthValue(), false));
			statementMonth = statementMonth.minusMonths(1);
		}
	}

	static List<StatementRequest> createOverviewDownloadRequests(List<StatementOverviewEntry> overviewEntries, Set<String> knownStatementIds) {
		List<StatementOverviewEntry> downloadableEntries = new ArrayList<>();
		if (overviewEntries != null) {
			for (StatementOverviewEntry overviewEntry : overviewEntries) {
				if (overviewEntry != null && overviewEntry.retrievable() && overviewEntry.number() > 0) {
					downloadableEntries.add(overviewEntry);
				}
			}
		}

		downloadableEntries.sort((left, right) -> {
			int yearCompare = Integer.compare(yearValue(right.year()), yearValue(left.year()));
			return yearCompare != 0 ? yearCompare : Integer.compare(right.number(), left.number());
		});

		Set<String> requestedStatementIds = new HashSet<>();
		if (knownStatementIds != null) {
			requestedStatementIds.addAll(knownStatementIds);
		}

		List<StatementRequest> statementRequests = new ArrayList<>();
		for (StatementOverviewEntry overviewEntry : downloadableEntries) {
			addRequest(statementRequests, requestedStatementIds,
					new StatementRequest(overviewEntry.year(), overviewEntry.number(), true));
		}
		return statementRequests;
	}

	private static void addRequest(List<StatementRequest> statementRequests, Set<String> requestedStatementIds, StatementRequest statementRequest) {
		if (requestedStatementIds.add(statementId(statementRequest.year(), statementRequest.number()))) {
			statementRequests.add(statementRequest);
		}
	}

	static List<StatementOverviewEntry> readOverviewEntries(Properties resultData) {
		if (resultData == null || resultData.isEmpty()) {
			return List.of();
		}

		List<StatementOverviewEntry> entries = new ArrayList<>();
		for (String prefix : overviewEntryPrefixes(resultData)) {
			int number = parseInt(resultData.getProperty(prefix + ".number"), 0);
			if (number > 0) {
				entries.add(new StatementOverviewEntry(
						parseInteger(resultData.getProperty(prefix + ".year")),
						number,
						parseBoolean(resultData.getProperty(prefix + ".retrievable")),
						trimToNull(resultData.getProperty(prefix + ".acknowledgement")),
						parseDate(resultData.getProperty(prefix + ".date")),
						trimToNull(resultData.getProperty(prefix + ".time")),
						trimToNull(resultData.getProperty(prefix + ".creationtype")),
						trimToNull(resultData.getProperty(prefix + ".documentid"))));
			}
		}
		return entries;
	}

	private static List<String> overviewEntryPrefixes(Properties resultData) {
		Set<String> prefixes = new HashSet<>();
		for (String key : resultData.stringPropertyNames()) {
			int separator = key.lastIndexOf('.');
			if (separator > 0 && separator < key.length() - 1
					&& OVERVIEW_RESULT_FIELDS.contains(key.substring(separator + 1))) {
				prefixes.add(key.substring(0, separator));
			}
		}

		List<String> sortedPrefixes = new ArrayList<>(prefixes);
		sortedPrefixes.sort((left, right) -> Integer.compare(overviewPrefixIndex(left), overviewPrefixIndex(right)));
		return sortedPrefixes;
	}

	private static int overviewPrefixIndex(String prefix) {
		if ("content".equals(prefix)) {
			return 0;
		}
		return prefix != null && prefix.startsWith("content_")
				? parseInt(prefix.substring("content_".length()), Integer.MAX_VALUE)
				: Integer.MAX_VALUE;
	}

	static Set<String> createKnownStatementIds(List<BankAccountStatement> storedStatements, List<AccountStatement> currentStatements) {
		Set<String> knownStatementIds = new HashSet<>();
		if (storedStatements != null) {
			for (BankAccountStatement statement : storedStatements) {
				if (statement != null && statement.getNumber() > 0) {
					knownStatementIds.add(statementId(toRequestYear(statement.getYear()), statement.getNumber()));
				}
			}
		}
		if (currentStatements != null) {
			for (AccountStatement statement : currentStatements) {
				if (statement != null && statement.number() > 0) {
					knownStatementIds.add(statementId(toRequestYear(statement.year()), statement.number()));
				}
			}
		}
		return knownStatementIds;
	}

	static boolean hasStoredStatementNumbers(List<BankAccountStatement> storedStatements) {
		if (storedStatements != null) {
			for (BankAccountStatement statement : storedStatements) {
				if (statement != null && statement.getNumber() > 0) {
					return true;
				}
			}
		}
		return false;
	}

	static List<Integer> createInitialRetrievalYears(int currentYear) {
		List<Integer> years = new ArrayList<>();
		for (int year = currentYear; year >= currentYear - FIRST_RETRIEVAL_LOOKBACK_YEARS; year--) {
			years.add(year);
		}
		return years;
	}

	private static String statementId(Integer year, int number) {
		return (year != null ? year.toString() : "") + "/" + number;
	}

	private static Integer toRequestYear(int year) {
		return year > 0 ? Integer.valueOf(year) : null;
	}

	private static int yearValue(Integer year) {
		return year != null ? year.intValue() : 0;
	}

	private static LocalDate parseDate(String value) {
		String normalizedValue = trimToNull(value);
		if (normalizedValue == null) {
			return null;
		}
		try {
			if (normalizedValue.length() == 8) {
				return LocalDate.of(
						Integer.parseInt(normalizedValue.substring(0, 4)),
						Integer.parseInt(normalizedValue.substring(4, 6)),
						Integer.parseInt(normalizedValue.substring(6, 8)));
			}
			return LocalDate.parse(normalizedValue);
		} catch (RuntimeException e) {
			log.warn("Could not parse HKKAU account statement overview date {}.", normalizedValue, e);
			return null;
		}
	}

	private static boolean parseBoolean(String value) {
		String normalizedValue = trimToNull(value);
		return normalizedValue != null
				&& ("J".equalsIgnoreCase(normalizedValue) || "Y".equalsIgnoreCase(normalizedValue) || "1".equals(normalizedValue)
						|| Boolean.parseBoolean(normalizedValue));
	}

	private static Integer parseInteger(String value) {
		String normalizedValue = trimToNull(value);
		if (normalizedValue == null) {
			return null;
		}
		try {
			return Integer.valueOf(normalizedValue);
		} catch (NumberFormatException e) {
			log.warn("Could not parse HKKAU account statement overview integer {}.", normalizedValue, e);
			return null;
		}
	}

	private static int parseInt(String value, int defaultValue) {
		Integer parsedValue = parseInteger(value);
		return parsedValue != null ? parsedValue.intValue() : defaultValue;
	}

	record StatementOverviewEntry(Integer year, int number, boolean retrievable, String acknowledgementCode, LocalDate creationDate,
			String creationTime, String creationType, String documentId) {
	}

	record StatementRequest(Integer year, int number, boolean exactStatement) {
	}
}
