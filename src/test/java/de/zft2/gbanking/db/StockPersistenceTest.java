package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.StockCashLegRole;
import de.zft2.gbanking.db.dao.enu.StockDataSourceType;
import de.zft2.gbanking.db.dao.enu.StockPriceType;
import de.zft2.gbanking.db.dao.enu.StockQuantityType;
import de.zft2.gbanking.db.dao.enu.StockQuotationType;
import de.zft2.gbanking.db.dao.enu.StockSecurityLegRole;
import de.zft2.gbanking.db.dao.enu.StockSecurityType;
import de.zft2.gbanking.db.dao.enu.StockTransactionStatus;
import de.zft2.gbanking.db.dao.enu.StockTransactionType;
import de.zft2.gbanking.db.dao.stock.StockDataSource;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioPosition;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.db.dao.stock.StockSecurity;
import de.zft2.gbanking.db.dao.stock.StockSecurityPrice;
import de.zft2.gbanking.db.dao.stock.StockSecurityPriceSource;
import de.zft2.gbanking.db.dao.stock.StockTransaction;
import de.zft2.gbanking.db.dao.stock.StockTransactionCashLeg;
import de.zft2.gbanking.db.dao.stock.StockTransactionMetadata;
import de.zft2.gbanking.db.dao.stock.StockTransactionSecurityLeg;
import de.zft2.gbanking.exception.GBankingException;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockPersistenceTest extends DBControllerIntegrationBaseTest {

	@Test
	void shouldPersistTypedPortfolioTransactionAndPriceData() {
		StockDataSource source = createManualSource();
		BankAccount depotAccount = createAccount(AccountType.DEPOT);
		BankAccount settlementAccount = createAccount(AccountType.CURRENT_ACCOUNT);
		StockPortfolio portfolio = createPortfolio(depotAccount, settlementAccount);
		StockSecurity security = createSecurity();

		StockSecurityPriceSource priceSource = new StockSecurityPriceSource();
		priceSource.setSecurityId(security.getId());
		priceSource.setSourceId(source.getId());
		priceSource.setPriority(10);
		db.insertOrUpdate(priceSource);

		StockSecurityPrice price = new StockSecurityPrice();
		price.setPriceSourceId(priceSource.getId());
		price.setQuotedAt(LocalDateTime.of(2026, 9, 14, 17, 30));
		price.setPriceE8(12_345_678_900L);
		price.setQuoteCurrency(Currency.EUR);
		price.setQuotationType(StockQuotationType.ABSOLUTE);
		price.setPriceType(StockPriceType.CLOSE);
		db.insertOrUpdate(price);

		StockTransaction transaction = new StockTransaction();
		transaction.setPortfolioId(portfolio.getId());
		transaction.setSourceId(source.getId());
		transaction.setTransactionType(StockTransactionType.BUY);
		transaction.setTransactionStatus(StockTransactionStatus.PENDING);
		transaction.setTradeAt(LocalDateTime.of(2026, 9, 12, 10, 0));
		db.insertOrUpdate(transaction);

		StockTransactionSecurityLeg securityLeg = new StockTransactionSecurityLeg();
		securityLeg.setTransactionId(transaction.getId());
		securityLeg.setLegNumber(1);
		securityLeg.setSecurityId(security.getId());
		securityLeg.setLegRole(StockSecurityLegRole.POSITION);
		securityLeg.setQuantityE9(2_500_000_000L);
		securityLeg.setQuantityType(StockQuantityType.UNITS);
		db.insertOrUpdate(securityLeg);

		StockTransactionCashLeg accruedInterest = new StockTransactionCashLeg();
		accruedInterest.setTransactionId(transaction.getId());
		accruedInterest.setLegNumber(1);
		accruedInterest.setAccountId(settlementAccount.getId());
		accruedInterest.setLegRole(StockCashLegRole.ACCRUED_INTEREST);
		accruedInterest.setAmountMinor(-125L);
		accruedInterest.setCurrency(Currency.EUR);
		accruedInterest.setValueAt(LocalDateTime.of(2026, 9, 14, 0, 0));
		db.insertOrUpdate(accruedInterest);

		transaction.setTransactionStatus(StockTransactionStatus.SETTLED);
		transaction.setSettledAt(LocalDateTime.of(2026, 9, 14, 0, 0));
		db.insertOrUpdate(transaction);

		StockTransactionMetadata transactionMetadata = new StockTransactionMetadata();
		transactionMetadata.setTransactionId(transaction.getId());
		transactionMetadata.setNote("Stueckzinsen enthalten");
		db.insertOrUpdate(transactionMetadata);

		StockSecurity storedSecurity = db.getById(StockSecurity.class, security.getId());
		assertEquals(StockSecurityType.STOCK, storedSecurity.getSecurityType());
		assertEquals(Currency.EUR, storedSecurity.getDefaultQuoteCurrency());
		assertNotNull(storedSecurity.getModifiedAt());
		assertEquals(StockPriceType.CLOSE,
				db.getAllByParent(StockSecurityPrice.class, priceSource.getId()).get(0).getPriceType());
		assertEquals(StockCashLegRole.ACCRUED_INTEREST,
				db.getAllByParent(StockTransactionCashLeg.class, transaction.getId()).get(0).getLegRole());
		assertEquals("Stueckzinsen enthalten",
				db.getById(StockTransactionMetadata.class, transaction.getId()).getNote());

		List<StockPortfolioPosition> positions = db.getAllByParent(StockPortfolioPosition.class, portfolio.getId());
		assertEquals(1, positions.size());
		assertEquals(2_500_000_000L, positions.get(0).getQuantityE9());
		assertThrows(GBankingException.class, () -> db.insertOrUpdate(price));
	}

	private StockDataSource createManualSource() {
		StockDataSource source = new StockDataSource();
		source.setSourceCode("TEST_MANUAL");
		source.setSourceName("Manuelle Testquelle");
		source.setSourceType(StockDataSourceType.MANUAL);
		return db.insertOrUpdate(source);
	}

	private BankAccount createAccount(AccountType accountType) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountType(accountType);
		return db.insertOrUpdate(account);
	}

	private StockPortfolio createPortfolio(BankAccount depotAccount, BankAccount settlementAccount) {
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setId(100);
		portfolio.setAccountId(depotAccount.getId());
		portfolio.setCurrentSettlementRelationId(200);
		portfolio.setOpenedAt(LocalDate.of(2026, 1, 1));

		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setId(200);
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(settlementAccount.getId());
		relation.setValidFrom(portfolio.getOpenedAt());

		return db.executeInTransaction(() -> {
			db.insertOrUpdate(portfolio);
			db.insertOrUpdate(relation);
			return portfolio;
		});
	}

	private StockSecurity createSecurity() {
		StockSecurity security = new StockSecurity();
		security.setSecurityType(StockSecurityType.STOCK);
		security.setName("Test AG");
		security.setDefaultQuantityType(StockQuantityType.UNITS);
		security.setDefaultQuoteCurrency(Currency.EUR);
		security.setDefaultQuotationType(StockQuotationType.ABSOLUTE);
		return db.insertOrUpdate(security);
	}
}
