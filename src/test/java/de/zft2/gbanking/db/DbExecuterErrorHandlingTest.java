package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.BankAccess;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Recipient;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.testdata.TestDataFactory;

class DbExecuterErrorHandlingTest extends DBControllerIntegrationBaseTest {

	@Test
	void executeSimpleSelect_invalidSql_shouldThrowDatabaseException() {
		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, Boolean.class, "invalid select");
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeDate_shouldThrowDatabaseException() {
		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, LocalDate.class, "select accountName from bankAccount where id = ?");
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeBoolean_shouldThrowDatabaseException() {
		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		assertSimpleSelectFails(acc01, Boolean.class, "select blahblah from bankAccount where id = ?");
	}

	@Test
	void executeSimpleSelect_emptyResultFieldWithTypeBoolean_shouldReturnFalse() {
		BankAccess ba = TestDataFactory.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestDataFactory.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class)) {
			statements.when(() -> StatementsConfig.getSqlStatement(acc01.getClass(), StatementType.SELECT_ID))
					.thenReturn("select balance from bankAccount where id = ?");

			assertFalse(db.getSingleResultField(acc01, StatementType.SELECT_ID, Boolean.class));
		}
	}

	@Test
	void insertOrUpdate_sqlError_shouldThrowAndLeaveConnectionWritable() {
		BankAccess invalidBankAccess = TestDataFactory.createSampleBankAccess("44444444");
		invalidBankAccess.setBankName(null);

		assertThrows(GBankingException.class, () -> db.insertOrUpdate(invalidBankAccess));

		BankAccess validBankAccess = db.insertOrUpdate(TestDataFactory.createSampleBankAccess("44444444"));
		assertTrue(validBankAccess.getId() > 0);
	}

	@Test
	void insertOrUpdate_invalidSql_shouldThrowDatabaseException() {
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("44444444");

		try (MockedStatic<StatementsConfig> statements = Mockito.mockStatic(StatementsConfig.class, Mockito.CALLS_REAL_METHODS)) {
			statements.when(() -> StatementsConfig.getSqlStatement(BankAccess.class, StatementType.INSERT)).thenReturn("invalid insert");

			assertThrows(GBankingException.class, () -> db.insertOrUpdate(bankAccess));
		}
	}

	@Test
	void genericReadMethods_invalidSql_shouldThrowDatabaseException() {
		BankAccess bankAccess = TestDataFactory.createSampleBankAccess("44444444");

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
	void mapRead_sqlError_shouldThrowDatabaseException() {
		JdbcOperations jdbc = DbConnectionHandler.getSession().jdbc();
		JdbcOperations.QueryObserver previousObserver = failQueries(jdbc);
		try {
			assertThrows(GBankingException.class, () -> db.getAccountsIdsByAccountName());
		} finally {
			jdbc.replaceQueryObserver(previousObserver);
		}
	}

	@Test
	void specializedReadMethods_sqlError_shouldThrowDatabaseException() {
		JdbcOperations jdbc = DbConnectionHandler.getSession().jdbc();
		JdbcOperations.QueryObserver previousObserver = failQueries(jdbc);
		try {
			assertThrows(GBankingException.class, () -> db.findPreferredRecipientByIban("DE123"));
			assertThrows(GBankingException.class, () -> db.getSplitBookings(1));
			assertThrows(GBankingException.class, () -> db.getBankAccessByBlz("44444444"));
			assertDebugPrintFailures();

			Booking booking = new Booking();
			booking.setRecipient(new Recipient());
			booking.setAmount(BigDecimal.ONE);
			booking.setDateBooking(LocalDate.now());
			assertThrows(GBankingException.class, () -> db.findCrossBooking(booking));
		} finally {
			jdbc.replaceQueryObserver(previousObserver);
		}
	}

	private void assertDebugPrintFailures() {
		Level previousLevel = LogManager.getLogger(DBController.class).getLevel();
		Configurator.setLevel(DBController.class, Level.DEBUG);
		try {
			assertThrows(GBankingException.class, () -> db.printAccountsInDB());
			assertThrows(GBankingException.class, () -> db.printBookingsInDB());
		} finally {
			Configurator.setLevel(DBController.class, previousLevel);
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

	private static JdbcOperations.QueryObserver failQueries(JdbcOperations jdbc) {
		return jdbc.replaceQueryObserver(sql -> {
			throw new SQLException("simulated read failure");
		});
	}
}
