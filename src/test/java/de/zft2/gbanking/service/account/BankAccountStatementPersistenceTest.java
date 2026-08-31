package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.testdata.TestDataFactory;

class BankAccountStatementPersistenceTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void closeDatabaseConnection() {
		DBController.resetConnection();
	}

	@Test
	void insertOrUpdateShouldPersistAccountStatementViaDaoRegistry() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccountStatement statement = createStatement(account);

		BankAccountStatement savedStatement = dbController.insertOrUpdate(statement);
		List<BankAccountStatement> statements = dbController.getAllByParentFull(BankAccountStatement.class, account.getId());

		assertTrue(savedStatement.getId() > 0);
		assertEquals(1, statements.size());
		assertEquals("account-DE123_2026-0005_bank.pdf", statements.get(0).getFileName());
		assertEquals(LocalDate.of(2026, Month.MAY, 31), statements.get(0).getStatementDate());
		assertArrayEquals("receipt".getBytes(StandardCharsets.ISO_8859_1), statements.get(0).getReceipt());
	}

	@Test
	void insertOrUpdateShouldMarkStatementAcknowledgedViaDaoRegistry() {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		BankAccountStatement statement = dbController.insertOrUpdate(createStatement(account));

		statement.setAcknowledged(true);
		statement.setAcknowledgedAt(LocalDateTime.of(2026, Month.JULY, 9, 12, 30));
		dbController.insertOrUpdate(statement);
		BankAccountStatement reloadedStatement = dbController.getById(BankAccountStatement.class, statement.getId());

		assertNotNull(reloadedStatement);
		assertTrue(reloadedStatement.isAcknowledged());
		assertEquals(LocalDateTime.of(2026, Month.JULY, 9, 12, 30), reloadedStatement.getAcknowledgedAt());
	}

	private BankAccountStatement createStatement(BankAccount account) {
		BankAccountStatement statement = new BankAccountStatement();
		statement.setAccountId(account.getId());
		statement.setAccountName("Girokonto");
		statement.setFileName("account-DE123_2026-0005_bank.pdf");
		statement.setFormat("PDF");
		statement.setRetrievedAt(LocalDateTime.of(2026, Month.JULY, 9, 11, 30));
		statement.setStatementDate(LocalDate.of(2026, Month.MAY, 31));
		statement.setStartDate(LocalDate.of(2026, Month.MAY, 1));
		statement.setEndDate(LocalDate.of(2026, Month.MAY, 31));
		statement.setYear(2026);
		statement.setNumber(5);
		statement.setSize(1234L);
		statement.setIban("DE123");
		statement.setBic("TESTDEFFXXX");
		statement.setSourceJob("KontoauszugPdf");
		statement.setReceiptAvailable(true);
		statement.setReceipt("receipt".getBytes(StandardCharsets.ISO_8859_1));
		statement.setAcknowledged(false);
		statement.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return statement;
	}
}
