package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.service.account.AccountStatementRequestPlanner.StatementOverviewEntry;
import de.zft2.gbanking.service.account.AccountStatementRequestPlanner.StatementRequest;

class AccountStatementRequestPlannerTest {

	@Test
	void createRedownloadRequestsShouldStartWithStoredAcknowledgedStatementsAndDeduplicateFallbackMonths() {
		List<BankAccountStatement> storedStatements = List.of(createStatement(2026, 5, true), createStatement(2026, 4, false), createStatement(2025, 12, true),
				createStatement(0, 6, true));

		List<StatementRequest> requests = AccountStatementRequestPlanner.createRedownloadRequests(storedStatements, YearMonth.of(2026, Month.JUNE));

		assertEquals(new StatementRequest(2026, 5, true), requests.get(0));
		assertEquals(new StatementRequest(2025, 12, true), requests.get(1));
		assertEquals(new StatementRequest(null, 6, true), requests.get(2));
		assertEquals(new StatementRequest(2026, 6, false), requests.get(3));
		assertEquals(new StatementRequest(2026, 4, false), requests.get(4));
		assertEquals(1, countRequests(requests, 2026, 5));
		assertEquals(1, countRequests(requests, 2025, 12));
	}

	@Test
	void readOverviewEntriesShouldParseLowlevelResultData() {
		Properties resultData = new Properties();
		resultData.setProperty("content.number", "5");
		resultData.setProperty("content.year", "2026");
		resultData.setProperty("content.retrievable", "J");
		resultData.setProperty("content.acknowledgement", "1");
		resultData.setProperty("content.date", "20260530");
		resultData.setProperty("content.time", "101500");
		resultData.setProperty("content.creationtype", "PDF");
		resultData.setProperty("content.documentid", "DOC-5");

		List<StatementOverviewEntry> entries = AccountStatementRequestPlanner.readOverviewEntries(resultData);

		assertEquals(1, entries.size());
		assertEquals(new StatementOverviewEntry(2026, 5, true, "1", LocalDate.of(2026, Month.MAY, 30), "101500", "PDF", "DOC-5"),
				entries.get(0));
	}

	@Test
	void createOverviewDownloadRequestsShouldSkipKnownAndNonRetrievableEntries() {
		List<StatementOverviewEntry> overviewEntries = List.of(
				new StatementOverviewEntry(2026, 5, true, "1", null, null, null, null),
				new StatementOverviewEntry(2026, 4, false, "1", null, null, null, null),
				new StatementOverviewEntry(2026, 3, true, "1", null, null, null, null),
				new StatementOverviewEntry(null, 2, true, "1", null, null, null, null));

		List<StatementRequest> requests = AccountStatementRequestPlanner.createOverviewDownloadRequests(overviewEntries, Set.of("2026/5"));

		assertEquals(List.of(new StatementRequest(2026, 3, true), new StatementRequest(null, 2, true)), requests);
	}

	@Test
	void readOverviewEntriesShouldSortIndexedEntriesAndSkipInvalidNumbers() {
		Properties resultData = new Properties();
		resultData.setProperty("content_10.number", "10");
		resultData.setProperty("content.number", "1");
		resultData.setProperty("content_2.number", "2");
		resultData.setProperty("content_1.number", "invalid");

		List<StatementOverviewEntry> entries = AccountStatementRequestPlanner.readOverviewEntries(resultData);

		assertEquals(List.of(1, 2, 10), entries.stream().map(StatementOverviewEntry::number).toList());
	}

	@Test
	void createInitialRetrievalYearsShouldIncludeCurrentAndFourPreviousYears() {
		assertEquals(List.of(2026, 2025, 2024, 2023, 2022), AccountStatementRequestPlanner.createInitialRetrievalYears(2026));
	}

	private long countRequests(List<StatementRequest> requests, int year, int number) {
		return requests.stream().filter(request -> request.year() != null && request.year() == year && request.number() == number).count();
	}

	private BankAccountStatement createStatement(int year, int number, boolean acknowledged) {
		BankAccountStatement statement = new BankAccountStatement();
		statement.setYear(year);
		statement.setNumber(number);
		statement.setAcknowledged(acknowledged);
		return statement;
	}
}
