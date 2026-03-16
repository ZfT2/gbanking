package de.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import de.gbanking.db.StatementsConfig.StatementType;
import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;

@ExtendWith(MockitoExtension.class)
class DbExecuterErrorHandlingTest extends DBControllerIntegrationBaseTest {

	@Mock
	private StatementsConfig sc;

	@Test
	void executeSimpleSelect_invalidSql_shouldFail() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		Boolean result = setupMock(acc01, Boolean.class, "invalid select");

		assertNull(result);
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeDate_shouldFail() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		Date result = setupMock(acc01, Date.class, "select id from bankAccount where id = ?");

		assertNull(result);
	}

	@Test
	void executeSimpleSelect_invalidResultFieldWithTypeBoolean_shouldFail() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		Boolean result = setupMock(acc01, Boolean.class, "select blahblah from bankAccount where id = ?");

		assertNull(result);
	}

	@Test
	void executeSimpleSelect_emptyResultFieldWithTypeBoolean_shouldReturnFalse() {
		BankAccess ba = TestData.createSampleBankAccess("44444444");
		db.insertOrUpdate(ba);
		BankAccount acc01 = TestData.createSampleAccount(ba.getId());
		db.insertOrUpdate(acc01);

		Boolean result = setupMock(acc01, Boolean.class, "select balance from bankAccount where id = ?");

		assertFalse(result);
	}

	private <T> T setupMock(BankAccount acc01, Class<T> resultType, String manipulatedSql) {
		try (MockedStatic<StatementsConfig> dummyStatement = Mockito.mockStatic(StatementsConfig.class)) {
			dummyStatement.when(() -> StatementsConfig.getSqlStatement(acc01.getClass(), StatementType.SELECT_ID)).thenReturn(manipulatedSql);

			T result = db.getSingleResultField(acc01, StatementType.SELECT_ID, resultType);

			dummyStatement.verify(() -> StatementsConfig.getSqlStatement(eq(acc01.getClass()), eq(StatementType.SELECT_ID)));

			return result;
		}
	}
}