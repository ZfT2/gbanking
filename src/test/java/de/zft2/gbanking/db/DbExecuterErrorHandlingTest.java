package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.GBankingException;

class DbExecuterErrorHandlingTest extends DBControllerIntegrationBaseTest {

	@Test
	void executeSimpleSelect_invalidSql_shouldThrowDatabaseException() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, Boolean.class, "invalid select");
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeDate_shouldThrowDatabaseException() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, LocalDate.class, "select accountName from bankAccount where id = ?");
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeBoolean_shouldThrowDatabaseException() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, Boolean.class, "select blahblah from bankAccount where id = ?");
	}

	@Test
	void executeSimpleSelect_emptyResultFieldWithTypeBoolean_shouldReturnFalse() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class)) {
			statements.when(() -> StatementsConfig.getSqlStatement(acc01.getClass(), StatementType.SELECT_ID))
					.thenReturn("select balance from bankAccount where id = ?");

			assertFalse(db.getSingleResultField(acc01, StatementType.SELECT_ID, Boolean.class));
		}
	}

	@Test
	void insertOrUpdate_sqlError_shouldThrowAndLeaveConnectionWritable() {
		BankAccess invalidBankAccess = TestData.createSampleBankAccess("44444444");
		invalidBankAccess.setBankName(null);

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(invalidBankAccess));

		BankAccess validBankAccess = db.insertOrUpdate(TestData.createSampleBankAccess("44444444"));
		assertTrue(validBankAccess.getId() > 0);
	}

	@Test
	void insertOrUpdate_invalidSql_shouldThrowDatabaseException() {
		BankAccess bankAccess = TestData.createSampleBankAccess("44444444");

		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class, Mockito.CALLS_REAL_METHODS)) {
			statements.when(() -> StatementsConfig.getSqlStatement(BankAccess.class, StatementType.INSERT)).thenReturn("invalid insert");

			assertThrows(GBankingException.class, () -> db.insertOrUpdate(bankAccess));
		}
	}

	@Test
	void genericReadMethods_invalidSql_shouldThrowDatabaseException() {
		BankAccess bankAccess = TestData.createSampleBankAccess("44444444");

		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class, Mockito.CALLS_REAL_METHODS)) {
			statements.when(() -> StatementsConfig.getSqlStatement(BankAccess.class, StatementType.SELECT_FIND)).thenReturn("invalid find");
			assertThrows(GBankingException.class, () -> db.find(BankAccess.class, bankAccess));

			statements.when(() -> StatementsConfig.getSelectByIdSqlStatement(BankAccess.class)).thenReturn("invalid select by id");
			assertThrows(GBankingException.class, () -> db.getById(BankAccess.class, 1));

			statements.when(() -> StatementsConfig.getSqlStatement(BankAccess.class, StatementType.SELECT_ALL)).thenReturn("invalid select all");
			assertThrows(GBankingException.class, () -> db.getAll(BankAccess.class));
		}
	}

	@Test
	void mapRead_invalidSql_shouldThrowDatabaseException() {
		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class, Mockito.CALLS_REAL_METHODS)) {
			statements.when(() -> StatementsConfig.getTableViewName(BankAccount.class)).thenReturn("missingBankAccountTable");

			assertThrows(GBankingException.class, () -> db.getAccountsIdsByAccountName());
		}
	}

	@Test
	void specializedReadMethods_sqlError_shouldThrowDatabaseException() throws SQLException {
		Connection activeConnection = DbConnectionHandler.connection;
		Connection failingConnection = mock(Connection.class);
		SQLException failure = new SQLException("simulated read failure");
		when(failingConnection.prepareStatement(anyString())).thenThrow(failure);
		when(failingConnection.createStatement()).thenThrow(failure);
		DbConnectionHandler.connection = failingConnection;

		try {
			assertThrows(GBankingException.class, () -> db.findPreferredRecipientByIban("DE123"));
			assertThrows(GBankingException.class, () -> db.getSplitBookings(1));
			assertThrows(GBankingException.class, () -> db.getBankAccessByBlz("44444444"));
			assertThrows(GBankingException.class, () -> db.printAccountsInDB());
			assertThrows(GBankingException.class, () -> db.printBookingsInDB());

			Booking booking = new Booking();
			booking.setRecipient(new Recipient());
			assertThrows(GBankingException.class, () -> db.findCrossBooking(booking));
		} finally {
			DbConnectionHandler.connection = activeConnection;
		}
	}

	@Test
	void missingRows_shouldKeepNullAndEmptyResultContract() {
		assertNull(db.getById(BankAccess.class, Integer.MAX_VALUE));
		assertNull(db.getBankAccessByBlz("00000000"));
		assertTrue(db.getAll(BankAccess.class).isEmpty());
	}

	private <T> void assertSimpleSelectFails(BankAccount acc01, Class<T> resultType, String manipulatedSql) {
		try (MockedStatic<StatementsConfig> dummyStatement = Mockito.mockStatic(StatementsConfig.class)) {
			dummyStatement.when(() -> StatementsConfig.getSqlStatement(acc01.getClass(), StatementType.SELECT_ID)).thenReturn(manipulatedSql);

			assertThrows(GBankingException.class, () -> db.getSingleResultField(acc01, StatementType.SELECT_ID, resultType));

			dummyStatement.verify(() -> StatementsConfig.getSqlStatement(eq(acc01.getClass()), eq(StatementType.SELECT_ID)));
		}
	}
}
