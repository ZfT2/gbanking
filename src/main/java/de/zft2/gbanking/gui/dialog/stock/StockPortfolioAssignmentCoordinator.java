package de.zft2.gbanking.gui.dialog.stock;

import java.util.List;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.enu.AccountType;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.dialog.stock.StockPortfolioAssignmentDialog.AccountAssignment;
import de.zft2.gbanking.service.ServiceRegistry;
import de.zft2.gbanking.service.stock.StockPortfolioFinTsService;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

public final class StockPortfolioAssignmentCoordinator implements BaseMessages {

	private final StockPortfolioFinTsService service;
	private final List<BankAccount> portfolioAccounts;
	private final List<BankAccount> settlementAccounts;
	private List<AccountAssignment> assignments = List.of();

	public StockPortfolioAssignmentCoordinator(List<BankAccount> bankAccessAccounts) {
		this(ServiceRegistry.getService(StockPortfolioFinTsService.class), bankAccessAccounts);
	}

	StockPortfolioAssignmentCoordinator(StockPortfolioFinTsService service, List<BankAccount> bankAccessAccounts) {
		this.service = service;
		List<BankAccount> accounts = bankAccessAccounts != null ? bankAccessAccounts : List.of();
		portfolioAccounts = accounts.stream()
				.filter(account -> account.getAccountType() == AccountType.DEPOT)
				.filter(account -> account.getId() <= 0 || service.findPortfolioForAccount(account.getId()) == null)
				.toList();
		settlementAccounts = accounts.stream()
				.filter(StockPortfolioAssignmentCoordinator::isSettlementAccount)
				.toList();
	}

	public boolean chooseAssignments(Window owner) {
		if (portfolioAccounts.isEmpty()) {
			return true;
		}
		if (settlementAccounts.isEmpty()) {
			DialogWindowSupport.showAlert(owner, AlertType.WARNING, getText("ALERT_STOCK_ASSIGNMENT_NO_SETTLEMENT_ACCOUNT"));
			return false;
		}
		List<AccountAssignment> selectedAssignments = new StockPortfolioAssignmentDialog(
				owner, portfolioAccounts, settlementAccounts).showAndWait();
		if (selectedAssignments == null) {
			return false;
		}
		assignments = selectedAssignments;
		return true;
	}

	public void persistAssignments() {
		for (AccountAssignment assignment : assignments) {
			BankAccount portfolioAccount = portfolioAccounts.get(assignment.portfolioIndex());
			BankAccount settlementAccount = settlementAccounts.get(assignment.settlementIndex());
			service.ensurePortfolio(portfolioAccount.getId(), settlementAccount.getId());
		}
	}

	private static boolean isSettlementAccount(BankAccount account) {
		return account.getAccountType() == AccountType.CURRENT_ACCOUNT
				|| account.getAccountType() == AccountType.DEPOT_ACCOUNT;
	}
}
