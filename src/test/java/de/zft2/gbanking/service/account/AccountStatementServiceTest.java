package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kapott.hbci.GV_Result.GVRKontoauszug.Format;
import org.kapott.hbci.GV_Result.GVRKontoauszug.GVRKontoauszugEntry;

import de.zft2.gbanking.db.DBController;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.BankAccountStatement;
import de.zft2.gbanking.testdata.TestDataFactory;

class AccountStatementServiceTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setupDatabaseContext() {
		DBController.resetConnection();
		DBController.getInstance(tempDir.resolve("db").toString());
	}

	@AfterEach
	void closeDatabaseConnection() {
		DBController.resetConnection();
	}

	@Test
	void saveStatementsShouldSkipDuplicateEntriesInRetrievalSession() throws Exception {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		AccountStatementService service = new AccountStatementService(tempDir);
		GVRKontoauszugEntry entry = createEntry("statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry, entry), "KontoauszugPdf", new HashSet<>());

		assertEquals(1, savedStatements.size());
		assertEquals(1, dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).size());
		assertEquals(1, regularFileCount(tempDir));
	}

	@Test
	void saveStatementsShouldRestoreMissingKnownStatementWithOriginalFileName() throws Exception {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		AccountStatementService service = new AccountStatementService(tempDir);
		BankAccountStatement storedStatement = dbController.insertOrUpdate(createStoredStatement(account, true));
		GVRKontoauszugEntry entry = createEntry("restored statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry), "KontoauszugPdf", new HashSet<>());
		List<BankAccountStatement> persistedStatements = dbController.getAllByParentFull(BankAccountStatement.class, account.getId());

		assertEquals(1, savedStatements.size());
		assertEquals(storedStatement.getId(), savedStatements.get(0).getId());
		assertEquals(1, persistedStatements.size());
		assertEquals("account-DE123_2026-0007_statement.pdf", persistedStatements.get(0).getFileName());
		assertTrue(persistedStatements.get(0).isAcknowledged());
		assertTrue(Files.exists(tempDir.resolve("account-DE123_2026-0007_statement.pdf")));
		assertEquals(1, regularFileCount(tempDir));
	}

	@Test
	void saveStatementsShouldSkipKnownStatementWhenFileAlreadyExists() throws Exception {
		DBController dbController = DBController.getInstance(tempDir.resolve("db").toString());
		BankAccount account = dbController.insertOrUpdate(TestDataFactory.createSampleAccount(null));
		AccountStatementService service = new AccountStatementService(tempDir);
		BankAccountStatement storedStatement = dbController.insertOrUpdate(createStoredStatement(account, true));
		Files.write(tempDir.resolve(storedStatement.getFileName()), "existing".getBytes(StandardCharsets.ISO_8859_1));
		GVRKontoauszugEntry entry = createEntry("duplicate statement".getBytes(StandardCharsets.ISO_8859_1));

		List<BankAccountStatement> savedStatements = service.saveStatements(account, List.of(entry), "KontoauszugPdf", new HashSet<>());

		assertTrue(savedStatements.isEmpty());
		assertEquals(1, dbController.getAllByParentFull(BankAccountStatement.class, account.getId()).size());
		assertEquals(1, regularFileCount(tempDir));
	}

	private BankAccountStatement createStoredStatement(BankAccount account, boolean acknowledged) {
		BankAccountStatement statement = new BankAccountStatement();
		statement.setAccountId(account.getId());
		statement.setAccountName("Girokonto");
		statement.setFileName("account-DE123_2026-0007_statement.pdf");
		statement.setFormat("PDF");
		statement.setRetrievedAt(LocalDateTime.of(2026, Month.JULY, 9, 11, 30));
		statement.setStatementDate(LocalDate.of(2026, Month.JUNE, 30));
		statement.setStartDate(LocalDate.of(2026, Month.JUNE, 1));
		statement.setEndDate(LocalDate.of(2026, Month.JUNE, 30));
		statement.setYear(2026);
		statement.setNumber(7);
		statement.setSize(1234L);
		statement.setIban("DE123");
		statement.setBic("TESTDEFFXXX");
		statement.setSourceJob("KontoauszugPdf");
		statement.setReceiptAvailable(true);
		statement.setReceipt("receipt".getBytes(StandardCharsets.ISO_8859_1));
		statement.setAcknowledged(acknowledged);
		statement.setAcknowledgedAt(acknowledged ? LocalDateTime.of(2026, Month.JULY, 9, 12, 30) : null);
		statement.setUpdatedAt(LocalDate.now(ZoneId.systemDefault()));
		return statement;
	}

	private GVRKontoauszugEntry createEntry(byte[] content) {
		GVRKontoauszugEntry entry = new GVRKontoauszugEntry();
		entry.setFormat(Format.PDF);
		entry.setData(content);
		entry.setDate(toDate(LocalDate.of(2026, Month.JUNE, 30)));
		entry.setStartDate(toDate(LocalDate.of(2026, Month.JUNE, 1)));
		entry.setEndDate(toDate(LocalDate.of(2026, Month.JUNE, 30)));
		entry.setYear(2026);
		entry.setNumber(7);
		entry.setReceipt("receipt".getBytes(StandardCharsets.ISO_8859_1));
		entry.setIBAN("DE123");
		entry.setBIC("TESTDEFFXXX");
		entry.setFilename("statement.pdf");
		return entry;
	}

	private Date toDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private long regularFileCount(Path directory) throws java.io.IOException {
		try (var files = Files.list(directory)) {
			return files.filter(path -> Files.isRegularFile(path)).count();
		}
	}
}
