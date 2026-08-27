package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class DbDdlSetupTest {

	@Test
	void setupShouldRejectConnectionWithExistingTransaction() throws Exception {
		Connection connection = mock(Connection.class);
		when(connection.getAutoCommit()).thenReturn(false);

		SQLException exception = assertThrows(SQLException.class, () -> DbDdlSetup.setupDB(connection));

		assertEquals("Database setup requires an auto-commit connection", exception.getMessage());
		verify(connection).getAutoCommit();
	}
}
