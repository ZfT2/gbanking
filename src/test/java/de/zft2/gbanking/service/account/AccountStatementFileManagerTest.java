package de.zft2.gbanking.service.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kapott.hbci.GV_Result.GVRKontoauszug.Format;
import org.kapott.hbci.GV_Result.GVRKontoauszug.GVRKontoauszugEntry;

import de.zft2.gbanking.db.DbRuntimeContext;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.tenant.TenantFileEncryptionContext;
import de.zft2.gbanking.tenant.TenantPaths;
import de.zft2.gbanking.tenant.TenantProfile;
import de.zft2.gbanking.tenant.TenantSession;
import de.zft2.gbanking.tenant.TenantStore;

class AccountStatementFileManagerTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void deactivateTenantEncryption() {
		TenantFileEncryptionContext.deactivate();
	}

	@Test
	void defaultServiceShouldUseAccountStatementsNextToTenantDatabaseDirectory() {
		String previousDbDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Path databaseDirectory = tempDir.resolve("data").resolve("tenant").resolve("00000000-0000-0000-0000-000000000001")
				.resolve("db");
		try {
			DbRuntimeContext.setCurrentDbDirectory(databaseDirectory.toString());

			Path statementFile = new AccountStatementFileManager().resolve("statement.pdf");

			assertEquals(databaseDirectory.getParent().resolve("accountStatements").resolve("statement.pdf"), statementFile);
		} finally {
			DbRuntimeContext.setCurrentDbDirectory(previousDbDirectory);
		}
	}

	@Test
	void defaultServiceShouldUseSharedAccountStatementsForLocalWorkingDatabase() {
		String previousDbDirectory = DbRuntimeContext.getCurrentDbDirectory();
		Path tenantDirectory = tempDir.resolve("shared-data").resolve("tenant").resolve("00000000-0000-0000-0000-000000000001");
		TenantPaths tenantPaths = new TenantPaths(tenantDirectory, tempDir.resolve("work").resolve("tenant")
				.resolve(tenantDirectory.getFileName()).resolve("db"));
		try {
			DbRuntimeContext.setCurrentTenantPaths(tenantPaths);

			Path statementFile = new AccountStatementFileManager().resolve("statement.pdf");

			assertEquals(tenantDirectory.resolve("accountStatements").resolve("statement.pdf"), statementFile);
		} finally {
			DbRuntimeContext.setCurrentDbDirectory(previousDbDirectory);
		}
	}

	@Test
	void saveShouldUseRequestedFileNamePattern() throws Exception {
		AccountStatementFileManager fileService = new AccountStatementFileManager(tempDir.resolve("statements"));
		BankAccount account = createAccount();
		byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.ISO_8859_1);
		GVRKontoauszugEntry entry = createEntry(content);

		Path statementFile = fileService.save(account, entry, Set.of());

		assertTrue(Files.exists(statementFile));
		assertArrayEquals(content, Files.readAllBytes(statementFile));
		assertEquals("account-DE12345678901234567890_2026-0007_NameVomFileNameFeld.pdf", statementFile.getFileName().toString());
	}

	@Test
	void saveShouldAddCounterSuffixWhenFileNameAlreadyExists() throws Exception {
		AccountStatementFileManager fileService = new AccountStatementFileManager(tempDir.resolve("statements"));
		BankAccount account = createAccount();
		GVRKontoauszugEntry entry = createEntry("statement".getBytes(StandardCharsets.ISO_8859_1));
		Set<String> existingFileNames = new HashSet<>();

		Path first = fileService.save(account, entry, existingFileNames);
		existingFileNames.add(fileName(first));
		Path second = fileService.save(account, entry, existingFileNames);

		assertEquals("account-DE12345678901234567890_2026-0007_NameVomFileNameFeld.pdf", fileName(first));
		assertEquals("account-DE12345678901234567890_2026-0007_NameVomFileNameFeld_01.pdf", fileName(second));
		try (var files = Files.list(tempDir.resolve("statements"))) {
			assertEquals(2, files.count());
		}
	}

	private static String fileName(Path file) {
		return Objects.requireNonNull(file.getFileName(), "file name").toString();
	}

	@Test
	void saveShouldUseAccountNumberWhenIbanIsMissing() {
		AccountStatementFileManager fileService = new AccountStatementFileManager(tempDir.resolve("statements"));
		BankAccount account = createAccount();
		account.setIban(null);
		account.setNumber("1234567890");
		GVRKontoauszugEntry entry = createEntry("statement".getBytes(StandardCharsets.ISO_8859_1));
		entry.setIBAN(null);

		Path statementFile = fileService.save(account, entry, Set.of());

		assertEquals("account-1234567890_2026-0007_NameVomFileNameFeld.pdf", statementFile.getFileName().toString());
	}

	@Test
	void saveShouldTrimUnsafeCharactersAtFileNamePartBoundaries() {
		AccountStatementFileManager fileService = new AccountStatementFileManager(tempDir.resolve("statements"));
		BankAccount account = createAccount();
		account.setIban(" /DE123/ ");
		GVRKontoauszugEntry entry = createEntry("statement".getBytes(StandardCharsets.ISO_8859_1));
		entry.setFilename("###Statement###.pdf");

		Path statementFile = fileService.save(account, entry, Set.of());

		assertEquals("account-DE123_2026-0007_Statement.pdf", statementFile.getFileName().toString());
	}

	@Test
	void encryptedStatementShouldOnlyBeDecryptedForOpening() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("tenant", "secret".toCharArray());
		byte[] content = "%PDF-1.7 encrypted".getBytes(StandardCharsets.ISO_8859_1);
		try (TenantSession session = tenantStore.authenticateSession(tenant.id(), "secret".toCharArray()).orElseThrow()) {
			TenantFileEncryptionContext.activate(session);
			Path statementsDirectory = session.paths().accountStatementsDirectory();
			AccountStatementFileManager fileService = new AccountStatementFileManager(statementsDirectory, () -> true);

			Path encryptedFile = fileService.save(createAccount(), createEntry(content), Set.of());
			String logicalFileName = fileService.logicalFileName(encryptedFile);
			Path fileForOpening = fileService.prepareForOpening(logicalFileName);

			assertTrue(encryptedFile.getFileName().toString().endsWith(".pdf.enc"));
			assertFalse(Files.exists(statementsDirectory.resolve(logicalFileName)));
			assertArrayEquals(content, Files.readAllBytes(fileForOpening));
			assertFalse(fileForOpening.startsWith(statementsDirectory));
		}
	}

	@Test
	void changingEncryptionShouldConvertExistingStatementInBothDirections() throws Exception {
		TenantStore tenantStore = new TenantStore(tempDir.resolve("data"));
		TenantProfile tenant = tenantStore.createTenant("tenant", "secret".toCharArray());
		byte[] content = "statement".getBytes(StandardCharsets.ISO_8859_1);
		try (TenantSession session = tenantStore.authenticateSession(tenant.id(), "secret".toCharArray()).orElseThrow()) {
			TenantFileEncryptionContext.activate(session);
			AccountStatementFileManager fileService = new AccountStatementFileManager(session.paths().accountStatementsDirectory());
			Path plaintextFile = fileService.save(createAccount(), createEntry(content), Set.of());

			fileService.updateEncryption(true);
			Path encryptedFile = TenantFileEncryptionContext.encryptedFile(plaintextFile);
			assertFalse(Files.exists(plaintextFile));
			assertTrue(Files.isRegularFile(encryptedFile));

			fileService.updateEncryption(false);
			assertFalse(Files.exists(encryptedFile));
			assertArrayEquals(content, Files.readAllBytes(plaintextFile));
		}
	}

	private BankAccount createAccount() {
		BankAccount account = new BankAccount();
		account.setId(42);
		account.setAccountName("Girokonto");
		account.setIban("DE12345678901234567890");
		account.setBic("TESTDEFFXXX");
		return account;
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
		entry.setIBAN("DE12345678901234567890");
		entry.setBIC("TESTDEFFXXX");
		entry.setFilename("NameVomFileNameFeld.pdf");
		return entry;
	}

	private Date toDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}
}
