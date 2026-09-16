package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountState;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.db.dao.enu.Currency;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService;
import de.zft2.gbanking.service.stock.StockPortfolioAdministrationService.PortfolioSaveRequest;
import de.zft2.gbanking.testdata.TestDataFactory;

class StockPortfolioAdministrationServiceTest extends DBControllerIntegrationBaseTest {

	@Test
	void shouldCreateAndEditOfflinePortfolioWithSettlementHistory() {
		BankAccount firstSettlement = createAccount("Verrechnung A", AccountType.CURRENT_ACCOUNT);
		BankAccount secondSettlement = createAccount("Verrechnung B", AccountType.DEPOT_ACCOUNT);
		StockPortfolioAdministrationService service = new StockPortfolioAdministrationService();
		LocalDate openedAt = LocalDate.of(2026, 1, 2);

		var created = service.save(request(null, "Offline-Depot", firstSettlement.getId(), openedAt, openedAt));

		BankAccount depotAccount = db.getById(BankAccount.class, created.accountId());
		assertEquals(AccountType.DEPOT, depotAccount.getAccountType());
		assertEquals(Source.MANUELL, depotAccount.getSource());
		assertTrue(depotAccount.isOfflineAccount());
		assertEquals(firstSettlement.getId(), created.settlementAccountId());

		LocalDate changeDate = LocalDate.of(2026, 9, 16);
		var edited = service.save(request(created.portfolioId(), "Depot geändert", secondSettlement.getId(),
				openedAt, changeDate));

		assertEquals("Depot geändert", edited.name());
		assertEquals(secondSettlement.getId(), edited.settlementAccountId());
		StockPortfolio portfolio = db.getById(StockPortfolio.class, edited.portfolioId());
		var relations = db.getAllByParent(StockPortfolioSettlementAccount.class, edited.portfolioId());
		assertEquals(2, relations.size());
		assertEquals(changeDate, relations.stream().filter(relation -> relation.getValidTo() != null)
				.findFirst().orElseThrow().getValidTo());
		assertEquals(secondSettlement.getId(), db.getById(StockPortfolioSettlementAccount.class,
				portfolio.getCurrentSettlementRelationId()).getAccountId());
	}

	private PortfolioSaveRequest request(Integer portfolioId, String name, int settlementAccountId,
			LocalDate openedAt, LocalDate validFrom) {
		return new PortfolioSaveRequest(portfolioId, name, "Max Mustermann", null, null, "4711", null,
				"Testbank", null, null, Currency.EUR, AccountState.ACTIVE, true, openedAt, null,
				settlementAccountId, validFrom);
	}

	private BankAccount createAccount(String name, AccountType accountType) {
		BankAccount account = TestDataFactory.createSampleAccount(null);
		account.setAccountName(name);
		account.setAccountType(accountType);
		account.setSource(Source.MANUELL);
		account.setAccountState(AccountState.ACTIVE);
		account.setBaseCurrency(Currency.EUR);
		account.setNumber(name);
		return db.insertOrUpdate(account);
	}
}
