package de.zft2.gbanking.service.stock;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.stock.StockPortfolio;
import de.zft2.gbanking.db.dao.stock.StockPortfolioSettlementAccount;
import de.zft2.gbanking.service.AbstractDbService;

/** Requires the caller's transaction to resolve deferred portfolio/assignment foreign keys. */
final class StockPortfolioWriter extends AbstractDbService {

	StockPortfolio createPortfolio(int depotAccountId, int settlementAccountId,
			LocalDate openedAt, LocalDate closedAt, LocalDate validFrom) {
		StockPortfolio portfolio = new StockPortfolio();
		portfolio.setAccountId(depotAccountId);
		portfolio.setCurrentSettlementRelationId(0);
		portfolio.setOpenedAt(openedAt);
		portfolio.setClosedAt(closedAt);
		portfolio = dbController.insertOrUpdate(portfolio);
		insertSettlementAccount(portfolio, settlementAccountId, validFrom);
		return portfolio;
	}

	void changeSettlementAccount(StockPortfolio portfolio, StockPortfolioSettlementAccount currentRelation,
			int settlementAccountId, LocalDate validFrom) {
		if (validFrom.equals(currentRelation.getValidFrom())) {
			currentRelation.setAccountId(settlementAccountId);
			dbController.insertOrUpdate(currentRelation);
			return;
		}
		portfolio.setCurrentSettlementRelationId(0);
		dbController.insertOrUpdate(portfolio);
		currentRelation.setValidTo(validFrom);
		dbController.insertOrUpdate(currentRelation);
		insertSettlementAccount(portfolio, settlementAccountId, validFrom);
	}

	private void insertSettlementAccount(StockPortfolio portfolio, int settlementAccountId, LocalDate validFrom) {
		StockPortfolioSettlementAccount relation = new StockPortfolioSettlementAccount();
		relation.setPortfolioId(portfolio.getId());
		relation.setAccountId(settlementAccountId);
		relation.setValidFrom(validFrom);
		relation = dbController.insertOrUpdate(relation);
		portfolio.setCurrentSettlementRelationId(relation.getId());
		dbController.insertOrUpdate(portfolio);
	}
}
