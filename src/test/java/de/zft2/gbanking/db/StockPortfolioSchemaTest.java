package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;

import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockSecurityState;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockStatementStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;

class StockPortfolioSchemaTest {

	private static final String CREATED_AT = "2026-09-14T12:00:00";

	@Test
	void portfolioShouldRequireDepotAndExactlyOneCurrentSettlementRelation() throws Exception {
		try (Connection connection = openDatabase(); Statement statement = connection.createStatement()) {
			insertBankAccount(statement, 1, 16, "Depot");
			insertBankAccount(statement, 2, 12, "Verrechnungskonto");
			insertBankAccount(statement, 3, 1, "Girokonto");
			insertBankAccount(statement, 4, 2, "Tagesgeldkonto");
			insertPortfolio(connection, 1, 1, 1, 2);

			assertThrows(SQLException.class, () -> statement.executeUpdate("""
					INSERT INTO stockPortfolioSettlementAccount
					    (id, portfolio_id, account_id, validFrom, validTo, createdAt, updatedAt)
					VALUES (2, 1, 3, '2026-01-01', NULL, '2026-01-01', '2026-01-01')
					"""));
			assertThrows(SQLException.class, () -> statement.executeUpdate("""
					INSERT INTO stockPortfolioSettlementAccount
					    (id, portfolio_id, account_id, validFrom, validTo, createdAt, updatedAt)
					VALUES (2, 1, 4, '2027-01-01', '2028-01-01', '2027-01-01', '2027-01-01')
					"""));

			changeSettlementAccount(connection, 1, 1, 2, 3);
			try (var resultSet = statement.executeQuery("""
					SELECT account_id, validFrom, validTo
					FROM stockPortfolioSettlementAccount
					WHERE portfolio_id = 1
					ORDER BY validFrom
					""")) {
				resultSet.next();
				assertEquals(2, resultSet.getInt("account_id"));
				assertEquals("2026-06-01", resultSet.getString("validTo"));
				resultSet.next();
				assertEquals(3, resultSet.getInt("account_id"));
				assertEquals("2026-06-01", resultSet.getString("validFrom"));
				assertNull(resultSet.getString("validTo"));
			}
		}
	}

	@Test
	void settledSecurityLegsShouldDerivePositionAndAllowNegativeHoldings() throws Exception {
		try (Connection connection = openDatabase(); Statement statement = connection.createStatement()) {
			insertBankAccount(statement, 1, 16, "Depot");
			insertBankAccount(statement, 2, 1, "Girokonto");
			insertPortfolio(connection, 1, 1, 1, 2);
			insertSecurity(statement);

			insertPendingTransaction(statement, 1, StockTransactionType.BUY, "2026-01-02");
			insertSecurityLeg(statement, 1, 1, 2_000_000_000L, 7_500_000_000L);
			insertCashLeg(statement, 1, 1, StockCashLegRole.TRADE_VALUE, -15_000L);
			insertCashLeg(statement, 1, 2, StockCashLegRole.ACCRUED_INTEREST, -123L);
			settleTransaction(statement, 1, "2026-01-02");

			insertPendingTransaction(statement, 2, StockTransactionType.SELL, "2026-02-02");
			insertSecurityLeg(statement, 2, 1, -3_000_000_000L, 8_000_000_000L);
			settleTransaction(statement, 2, "2026-02-02");

			try (var resultSet = statement.executeQuery("""
					SELECT quantityE9
					FROM stockPortfolioPosition
					WHERE portfolio_id = 1 AND security_id = 1 AND quantityType = %d
					""".formatted(StockQuantityType.UNITS.getDbStateId()))) {
				resultSet.next();
				assertEquals(-1_000_000_000L, resultSet.getLong("quantityE9"));
			}

			assertThrows(SQLException.class,
					() -> statement.executeUpdate("UPDATE stockTransactionSecurityLeg SET quantityE9 = 1 WHERE id = 1"));
			statement.executeUpdate("""
					INSERT INTO stockTransactionMetadata (transaction_id, note, tags, updatedAt)
					VALUES (1, 'Beleg geprueft', 'steuer', '2026-09-14')
					""");
			statement.executeUpdate("""
					UPDATE stockTransactionMetadata
					SET note = 'Beleg und Steuer geprueft', updatedAt = '2026-09-15'
					WHERE transaction_id = 1
					""");
		}
	}

	@Test
	void reportedStatementPositionShouldNotChangeDerivedPosition() throws Exception {
		try (Connection connection = openDatabase(); Statement statement = connection.createStatement()) {
			insertBankAccount(statement, 1, 16, "Depot");
			insertBankAccount(statement, 2, 1, "Girokonto");
			insertPortfolio(connection, 1, 1, 1, 2);
			insertSecurity(statement);
			insertPendingTransaction(statement, 1, StockTransactionType.BUY, "2026-01-02");
			insertSecurityLeg(statement, 1, 1, 2_000_000_000L, 7_500_000_000L);
			settleTransaction(statement, 1, "2026-01-02");

			statement.executeUpdate("""
					INSERT INTO stockPortfolioStatement
					    (id, portfolio_id, source_id, statementAt, statementStatus, externalReference, createdAt)
					VALUES (1, 1, 2, '2026-03-31', %d, 'HKKAZ-2026-03-31', '2026-03-31')
					""".formatted(StockStatementStatus.DRAFT.getDbStateId()));
			statement.executeUpdate("""
					INSERT INTO stockPortfolioStatementPosition
					    (statement_id, security_id, quantityE9, quantityType, createdAt)
					VALUES (1, 1, 99000000000, %d, '2026-03-31')
					""".formatted(StockQuantityType.UNITS.getDbStateId()));
			statement.executeUpdate("UPDATE stockPortfolioStatement SET statementStatus = %d WHERE id = 1"
					.formatted(StockStatementStatus.FINAL.getDbStateId()));

			try (var resultSet = statement.executeQuery("SELECT quantityE9 FROM stockPortfolioPosition")) {
				resultSet.next();
				assertEquals(2_000_000_000L, resultSet.getLong("quantityE9"));
			}
			assertThrows(SQLException.class, () -> statement.executeUpdate("""
					INSERT INTO stockTransaction
					    (portfolio_id, source_id, transactionType, transactionStatus, tradeAt, settledAt, createdAt, updatedAt)
					VALUES (1, 1, %d, %d, '2026-03-31', '2026-03-31', '2026-03-31', '2026-03-31')
					""".formatted(StockTransactionType.RECONCILIATION_ADJUSTMENT.getDbStateId(),
							StockTransactionStatus.SETTLED.getDbStateId())));
		}
	}

	private static Connection openDatabase() throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:", config.toProperties());
		DbDdlSetup.setupDB(connection);
		return connection;
	}

	private static void insertBankAccount(Statement statement, int id, int accountType, String accountName) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO bankAccount
				    (id, accountName, baseCurrency, accountType, accountSource, isSEPAAccount, isOfflineAccount,
				     accountState, createdAt, updatedAt)
				VALUES (%d, '%s', 1, %d, 5, 0, 1, 1, '%s', '%s')
				""".formatted(id, accountName, accountType, CREATED_AT, CREATED_AT));
	}

	private static void insertPortfolio(Connection connection, int portfolioId, int accountId, int relationId,
			int settlementAccountId) throws SQLException {
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO stockPortfolio
					    (id, account_id, currentSettlementRelation_id, openedAt, createdAt, updatedAt)
					VALUES (%d, %d, %d, '2026-01-01', '%s', '%s')
					""".formatted(portfolioId, accountId, relationId, CREATED_AT, CREATED_AT));
			statement.executeUpdate("""
					INSERT INTO stockPortfolioSettlementAccount
					    (id, portfolio_id, account_id, validFrom, validTo, createdAt, updatedAt)
					VALUES (%d, %d, %d, '2026-01-01', NULL, '%s', '%s')
					""".formatted(relationId, portfolioId, settlementAccountId, CREATED_AT, CREATED_AT));
			connection.commit();
		} catch (SQLException exception) {
			connection.rollback();
			throw exception;
		} finally {
			connection.setAutoCommit(true);
		}
	}

	private static void changeSettlementAccount(Connection connection, int portfolioId, int oldRelationId,
			int newRelationId, int newAccountId) throws SQLException {
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("UPDATE stockPortfolio SET currentSettlementRelation_id = %d WHERE id = %d"
					.formatted(newRelationId, portfolioId));
			statement.executeUpdate("UPDATE stockPortfolioSettlementAccount SET validTo = '2026-06-01' WHERE id = %d"
					.formatted(oldRelationId));
			statement.executeUpdate("""
					INSERT INTO stockPortfolioSettlementAccount
					    (id, portfolio_id, account_id, validFrom, validTo, createdAt, updatedAt)
					VALUES (%d, %d, %d, '2026-06-01', NULL, '%s', '%s')
					""".formatted(newRelationId, portfolioId, newAccountId, CREATED_AT, CREATED_AT));
			connection.commit();
		} catch (SQLException exception) {
			connection.rollback();
			throw exception;
		} finally {
			connection.setAutoCommit(true);
		}
	}

	private static void insertSecurity(Statement statement) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO stockSecurity
				    (id, securityType, name, defaultQuantityType, defaultQuoteCurrency,
				     defaultQuotationType, securityState, createdAt, updatedAt)
				VALUES (1, %d, 'Test AG', %d, %d, %d, %d, '%s', '%s')
				""".formatted(StockSecurityType.STOCK.getDbStateId(), StockQuantityType.UNITS.getDbStateId(),
						Currency.EUR.getDbStateId(), StockQuotationType.ABSOLUTE.getDbStateId(),
						StockSecurityState.ACTIVE.getDbStateId(), CREATED_AT, CREATED_AT));
	}

	private static void insertPendingTransaction(Statement statement, int id, StockTransactionType transactionType,
			String date) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO stockTransaction
				    (id, portfolio_id, source_id, transactionType, transactionStatus, tradeAt, createdAt, updatedAt)
				VALUES (%d, 1, 1, %d, %d, '%s', '%s', '%s')
				""".formatted(id, transactionType.getDbStateId(), StockTransactionStatus.PENDING.getDbStateId(),
						date, date, date));
	}

	private static void settleTransaction(Statement statement, int transactionId, String settledAt) throws SQLException {
		statement.executeUpdate("""
				UPDATE stockTransaction
				SET transactionStatus = %d, settledAt = '%s', updatedAt = '%s'
				WHERE id = %d
				""".formatted(StockTransactionStatus.SETTLED.getDbStateId(), settledAt, settledAt, transactionId));
	}

	private static void insertSecurityLeg(Statement statement, int transactionId, int legNumber, long quantityE9,
			long priceE8) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO stockTransactionSecurityLeg
				    (transaction_id, legNumber, security_id, legRole, quantityE9, quantityType, priceE8,
				     priceCurrency, quotationType, createdAt)
				VALUES (%d, %d, 1, %d, %d, %d, %d, %d, %d, '%s')
				""".formatted(transactionId, legNumber, StockSecurityLegRole.POSITION.getDbStateId(), quantityE9,
						StockQuantityType.UNITS.getDbStateId(), priceE8, Currency.EUR.getDbStateId(),
						StockQuotationType.ABSOLUTE.getDbStateId(), CREATED_AT));
	}

	private static void insertCashLeg(Statement statement, int transactionId, int legNumber,
			StockCashLegRole legRole, long amountMinor) throws SQLException {
		statement.executeUpdate("""
				INSERT INTO stockTransactionCashLeg
				    (transaction_id, legNumber, account_id, legRole, amountMinor, currency, valueAt, createdAt)
				VALUES (%d, %d, 2, %d, %d, %d, '2026-01-02', '2026-01-02')
				""".formatted(transactionId, legNumber, legRole.getDbStateId(), amountMinor,
						Currency.EUR.getDbStateId()));
	}
}
