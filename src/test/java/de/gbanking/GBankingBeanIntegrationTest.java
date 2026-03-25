package de.gbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.structures.Konto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import de.gbanking.db.DBController;
import de.gbanking.db.StatementsConfig;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.AccountType;
import de.gbanking.db.dao.enu.MoneyTransferStatus;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.file.exp.FileExportXMLBean;
import de.gbanking.file.imp.FileImportBean;
import de.gbanking.gui.dto.MoneyTransferForm;
import de.gbanking.mapper.HbciMapper;

@Disabled
class GBankingBeanIntegrationTest {

	@InjectMocks
	private GBankingBean gBankingBean;

	@Mock
	private static DBController dbController;

	@Mock
	private GBankingLoggingHandler logHandler;

	@Mock
	private FileImportBean fileImportBean;

	@Mock
	private FileExportXMLBean fileExportBean;

	@Mock
	private HBCIPassport passport;

	@Mock
	private HBCIHandler handler;

	@Mock
	private HBCIJob<?> saldoJob;

	@Mock
	private HBCIJob<?> umsatzJob;

	@Mock
	private Konto konto;

	private HBCIHandler mockHandler;


	@BeforeAll
	static void initHBCI() {
		// Prevent NPE in HBCIUtilsInternal by initializing once for all tests
		HBCIUtils.init(null, new HBCICallbackConsole());
		HBCIUtils.setParam("client.product", "JUnitTest");
		HBCIUtils.setParam("client.version", "1.0");
		HBCIUtils.setParam("log.loglevel.default", "0");
	}

	@BeforeEach
	void setup() {
		// Spy real bean to allow selective mocking
		gBankingBean = spy(new GBankingBean());

		mockHandler = mock(HBCIHandler.class);

		dbController = mock(DBController.class);

		// Mock handler creation to prevent real HBCI communication
		try {
			doReturn(mockHandler).when(gBankingBean).createHBCIHandler(String.valueOf(anyInt()), any(HBCIPassport.class));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// ----------------- DB-Methoden -----------------

	@Test
	void testDeleteBankAccessFromDB() {
		BankAccess bankAccess = new BankAccess();
		BankAccount account = new BankAccount();
		account.setSource(Source.MANUELL);
		bankAccess.setAccounts(List.of(account));

		when(dbController.executeSimpleUpdate(bankAccess.getAccounts(), StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, null) >= 0).thenReturn(true);
		when(dbController.delete(bankAccess, null)).thenReturn(true);

		boolean result = gBankingBean.deleteBankAccessFromDB(bankAccess);

		assertTrue(result);
		verify(dbController).executeSimpleUpdate(bankAccess.getAccounts(), StatementsConfig.StatementType.UPDATE_ACCOUNT_SOURCE, null);
		verify(dbController).delete(bankAccess, null);
	}

	@Test
	void testSaveBankAccessAccountsToDB() {
		BankAccess bankAccess = new BankAccess();
		BankAccount account = new BankAccount();
		account.setId(1);
		account.setAccountType(AccountType.CURRENT_ACCOUNT);
		bankAccess.setAccounts(List.of(account));

		when(dbController.insertOrUpdate(account)).thenReturn(account);
		when(dbController.insertBusinessCases(account)).thenReturn(true);

		boolean result = gBankingBean.saveBankAccessAccountsToDB(bankAccess);

		assertTrue(result);
		verify(dbController).insertOrUpdate(account);
		verify(dbController).insertBusinessCases(account);
	}

	@Test
	void testSaveMoneyTransferToDB_NewRecipient() {
		BankAccount account = new BankAccount();
		account.setId(1);
		MoneyTransferForm moneyTransfer = new MoneyTransferForm(account, "John Doe", "DE123", "BICCODE", "TestBank", BigDecimal.valueOf(100), "Test");
		moneyTransfer.setBankAccount(account);

		when(dbController.find(Recipient.class, any())).thenReturn(null);
		when(dbController.insertOrUpdate(any(Recipient.class))).thenAnswer(i -> i.getArguments()[0]);
		when(dbController.insertOrUpdate(any(MoneyTransfer.class))).thenAnswer(i -> i.getArguments()[0]);

		MoneyTransfer result = gBankingBean.saveMoneyTransferToDB(moneyTransfer);

		assertNotNull(result);
		assertEquals(MoneyTransferStatus.NEW, result.getMoneytransferStatus());
	}

	// ----------------- File-Methoden -----------------

	// ------------------------------------------------------------------------
	// TEST 1: importFile() - success case
	// ------------------------------------------------------------------------
	@Test
	void testImportFile_Success() throws Exception {
		try (MockedConstruction<FileImportBean> mocked = mockConstruction(FileImportBean.class,
				(mock, context) -> doNothing().when(mock).importFileToDatatbase(anyString()))) {

			boolean result = fileImportBean.importFile("dummy.xml");

			assertTrue(result, "Expected importFile() to return true on successful import");
			verify(mocked.constructed().get(0)).importFileToDatatbase("dummy.xml");
		}
	}

	// ------------------------------------------------------------------------
	// TEST 2: importFile() - failure (throws IOException)
	// ------------------------------------------------------------------------
	@Test
	void testImportFile_Failure() throws Exception {
		try (MockedConstruction<FileImportBean> mocked = mockConstruction(FileImportBean.class,
				(mock, context) -> doThrow(new IOException("File not found")).when(mock).importFileToDatatbase(anyString()))) {

			boolean result = fileImportBean.importFile("nonexistent.xml");

			assertFalse(result, "Expected importFile() to return false when exception occurs");
			verify(mocked.constructed().get(0)).importFileToDatatbase("nonexistent.xml");
		}
	}

	// ------------------------------------------------------------------------
	// TEST 3: exportFile() - success case
	// ------------------------------------------------------------------------
	@Test
	void testExportFile_Success() throws Exception {
		List<BankAccount> dummyAccounts = List.of(new BankAccount());
		String exportFile = "export.xml";

		try (MockedConstruction<FileExportXMLBean> mocked = mockConstruction(FileExportXMLBean.class,
				(mock, context) -> when(mock.exportFileFromDatatbase(anyList(), anyString())).thenReturn(true))) {

			boolean result = fileExportBean.exportFileFromDatatbase(dummyAccounts, exportFile); // gBankingBean.exportFile(dummyAccounts, exportFile,
																								// FileType.XML);

			assertTrue(result, "Expected exportFile() to return true when export succeeds");
			verify(mocked.constructed().get(0)).exportFileFromDatatbase(dummyAccounts, exportFile);
		}
	}

	// ------------------------------------------------------------------------
	// TEST 4: exportFile() - failure (throws TransformerException)
	// ------------------------------------------------------------------------
	@Test
	void testExportFile_Failure() throws Exception {
		List<BankAccount> dummyAccounts = List.of(new BankAccount());
		String exportFile = "export_error.xml";

		try (MockedConstruction<FileExportXMLBean> mocked = mockConstruction(FileExportXMLBean.class,
				(mock, context) -> doThrow(new TransformerException("Export failed")).when(mock).exportFileFromDatatbase(anyList(), anyString()))) {

			boolean result = fileExportBean.exportFileFromDatatbase(dummyAccounts, exportFile); // gBankingBean.exportFile(dummyAccounts, exportFile,
																								// FileType.XML);

			assertFalse(result, "Expected exportFile() to return false when TransformerException occurs");
			verify(mocked.constructed().get(0)).exportFileFromDatatbase(dummyAccounts, exportFile);
		}
	}

	// ----------------- HBCI-Methoden -----------------

	@Test
	void testAddNewBankAccess_Success() throws Exception {
		// -------------------------
		// Arrange: BankAccess input
		// -------------------------
		BankAccess bankAccess = new BankAccess();
		// initial values can be empty — bean will fill blz/upd/bpd/accounts
		// but set something if your logic expects it
		bankAccess.setBlz(null);
		bankAccess.setPin("1234".toCharArray());

		// -------------------------
		// Arrange: fake passport
		// -------------------------
		HBCIPassport mockPassport = mock(HBCIPassport.class);

		// Use doReturn on spy for initBankConnection (spy allows overriding)
		doReturn(mockPassport).when(gBankingBean).initBankConnection(any(BankAccess.class));

		// -------------------------
		// Arrange: real Konto (POJO)
		// -------------------------
		Konto konto = new Konto();
		konto.country = "DE";
		konto.blz = "10020030";
		konto.number = "987654321";
		konto.iban = "DE44500105175407324931";
		konto.bic = "BANKDEFFXXX";
		konto.name = "John Doe";
		konto.allowedGVs = List.of("GV1", "GV2");

		when(mockPassport.getAccounts()).thenReturn(new Konto[] { konto });
		when(mockPassport.getBLZ()).thenReturn("10020030");

		// IMPORTANT: UPD / BPD are Properties in your BankAccess class
		Properties updProps = new Properties();
		updProps.setProperty("updKey", "updValue");
		when(mockPassport.getUPD()).thenReturn(updProps);

		Properties bpdProps = new Properties();
		bpdProps.setProperty("bpdKey", "bpdValue");
		when(mockPassport.getBPD()).thenReturn(bpdProps);

		when(mockPassport.getInstName()).thenReturn("Mock Bank Inc.");

		// -------------------------
		// Arrange: mock construction of HBCIHandler (intercept constructor)
		// -------------------------
		// When GBankingBean executes: try (HBCIHandler handle = new HBCIHandler(...,
		// passport)) { ... }
		// we want to return a mock handler instance whose execute() we can stub.
		var execStatusMock = mock(org.kapott.hbci.status.HBCIExecStatus.class);
		when(execStatusMock.isOK()).thenReturn(true);

		try (MockedConstruction<HBCIHandler> mocked = mockConstruction(HBCIHandler.class, (mock, context) -> {
			// stub methods that addNewBankAccess might call on handler
			when(mock.execute()).thenReturn(execStatusMock);
			// if code calls mock.getPassport() or others, stub as needed:
			when(mock.getPassport()).thenReturn(mockPassport);
		})) {

			// -------------------------
			// Arrange: static HbciMapper
			// -------------------------
			BankAccount mappedAccount = new BankAccount();
			// set fields that will be asserted later (adapt names to your BankAccount
			// implementation)
			mappedAccount.setNumber(konto.number);
			mappedAccount.setBic(konto.bic);
			mappedAccount.setIban(konto.iban);

			try (MockedStatic<HbciMapper> hbciMapper = mockStatic(HbciMapper.class)) {
				hbciMapper.when(() -> HbciMapper.mapKontoToBankAccount(eq("Mock Bank Inc."), any(Konto.class))).thenReturn(mappedAccount);

				// -------------------------
				// Act: call method under test
				// -------------------------
				boolean result = gBankingBean.addNewBankAccess(bankAccess);

				// -------------------------
				// Assert: behavior and side-effects
				// -------------------------
				assertTrue(result, "addNewBankAccess should return true when HBCI execute reports OK");

				// bankAccess should now have BLZ, UPD, BPD and accounts set from
				// passport/mapper
				assertEquals("10020030", bankAccess.getBlz(), "bankAccess.blz must be set from passport.getBLZ()");
				assertSame(updProps, bankAccess.getUpd(), "bankAccess.upd must be the Properties from passport.getUPD()");
				assertSame(bpdProps, bankAccess.getBpd(), "bankAccess.bpd must be the Properties from passport.getBPD()");

				assertNotNull(bankAccess.getAccounts(), "accounts list must not be null");
				assertEquals(1, bankAccess.getAccounts().size(), "should map exactly one account");
				BankAccount first = bankAccess.getAccounts().get(0);
				assertEquals(konto.number, first.getNumber(), "mapped account number must match");
				assertEquals(konto.bic, first.getBic(), "mapped BIC must match");
				assertEquals(konto.iban, first.getIban(), "mapped IBAN must match");
			}
		}
	}

	@Test
	void testRetrieveAccountTransactions_Success() throws Exception {
		// -------------------------
		// Arrange: BankAccount + BankAccess
		// -------------------------
		BankAccount bankAccount = new BankAccount();
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");

		BankAccess bankAccess = new BankAccess();
		bankAccess.setBlz("10020030");
		doReturn(bankAccess).when(gBankingBean).initBankAccess(eq(bankAccount), any());

		// -------------------------
		// Arrange: mock passport
		// -------------------------
		HBCIPassport mockPassport = mock(HBCIPassport.class);
		Konto konto = new Konto();
		konto.iban = bankAccount.getIban();
		konto.number = bankAccount.getNumber();
		konto.allowedGVs = List.of("GV1", "GV2");

		when(mockPassport.getAccounts()).thenReturn(new Konto[] { konto });
		doReturn(mockPassport).when(gBankingBean).initBankConnection(bankAccess);

		// -------------------------
		// Mock HBCIHandler
		// -------------------------
		var execStatusMock = mock(org.kapott.hbci.status.HBCIExecStatus.class);
		when(execStatusMock.isOK()).thenReturn(true);

		try (MockedConstruction<HBCIHandler> mockedHandler = mockConstruction(HBCIHandler.class,
				(mock, context) -> when(mock.execute()).thenReturn(execStatusMock))) {

			// -------------------------
			// Act
			// -------------------------
			boolean result = gBankingBean.retrieveAccountTransactions(bankAccount, "1234".toCharArray());

			// -------------------------
			// Assert
			// -------------------------
			assertTrue(result);
		}
	}

	@Test
	void testExecuteTransfer_Success() throws Exception {
		// -------------------------
		// Arrange: BankAccount + MoneyTransfer
		// -------------------------
		BankAccount bankAccount = new BankAccount();
		bankAccount.setIban("DE44500105175407324931");
		bankAccount.setNumber("987654321");

		Recipient recipient = new Recipient();
		recipient.setIban("DE99887766554433221100");
		recipient.setBic("BANKDEFFXXX");
		recipient.setName("Jane Doe");

		MoneyTransfer mt = new MoneyTransfer();
		mt.setRecipient(recipient);
		mt.setAmount(new BigDecimal("100.50"));

		BankAccess bankAccess = new BankAccess();
		doReturn(bankAccess).when(gBankingBean).initBankAccess(eq(bankAccount), any());

		// -------------------------
		// Arrange: mock passport
		// -------------------------
		HBCIPassport mockPassport = mock(HBCIPassport.class);
		Konto sender = new Konto();
		sender.iban = bankAccount.getIban();
		sender.number = bankAccount.getNumber();
		when(mockPassport.getAccounts()).thenReturn(new Konto[] { sender });
		doReturn(mockPassport).when(gBankingBean).initBankConnection(eq(bankAccess));

		// -------------------------
		// Mock HBCIHandler
		// -------------------------
		var execStatusMock = mock(org.kapott.hbci.status.HBCIExecStatus.class);
		when(execStatusMock.isOK()).thenReturn(true);

		try (MockedConstruction<HBCIHandler> mockedHandler = mockConstruction(HBCIHandler.class,
				(mock, context) -> when(mock.execute()).thenReturn(execStatusMock))) {

			// -------------------------
			// Act
			// -------------------------
			boolean result = gBankingBean.executeTransfer(mt, bankAccount, null);

			// -------------------------
			// Assert
			// -------------------------
			assertTrue(result);
			assertEquals(MoneyTransferStatus.SENT, mt.getMoneytransferStatus());
		}
	}

}
