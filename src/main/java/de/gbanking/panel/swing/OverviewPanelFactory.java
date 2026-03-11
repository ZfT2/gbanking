package de.gbanking.panel.swing;

import de.gbanking.exception.GBankingException;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.AllTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.BankAccessOverviewPanel;
import de.gbanking.gui.swing.panel.overview.CategoryOverviewPanel;
import de.gbanking.gui.swing.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.swing.panel.overview.OverviewBasePanel;
import de.gbanking.gui.swing.panel.overview.RecipientOverviewPanel;

public class OverviewPanelFactory {

	private OverviewPanelFactory() {
	}

	public static OverviewBasePanel getInstance(String actionCommand) {
		
		PageContext pageContext = PageContext.valueOf(actionCommand);

		switch (pageContext) {
		case ACCOUNTS_TRANSACTIONS:
			return new AccountsTransactionsOverviewPanel();
		case ACCOUNTS_MONEYTRANSFERS:
			return new MoneyTransferOverviewPanel();
		case BANKACCESS:
			return new BankAccessOverviewPanel();
		case CATEGORIES:
			return new CategoryOverviewPanel();
		case RECIPIENTS:
			return new RecipientOverviewPanel();
		case ALL_ACCOUNTS:
			return new AllAccountsOverviewPanel();
		case ALL_TRANSACTIONS:
			return new AllTransactionsOverviewPanel();
		default:
			throw new GBankingException("Unknown OverviewPanel Key:", pageContext.name());
		}
	}

}
