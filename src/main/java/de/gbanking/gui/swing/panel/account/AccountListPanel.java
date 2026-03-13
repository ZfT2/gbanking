package de.gbanking.gui.swing.panel.account;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.model.AccountTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferListPanel;
import de.gbanking.gui.swing.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.AllAccountsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.MoneyTransferOverviewPanel;
import de.gbanking.gui.swing.panel.overview.OverviewBasePanel;
import de.gbanking.gui.swing.panel.transaction.TransactionListPanel;

public class AccountListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = 793083517803136077L;

	private static Logger log = LogManager.getLogger(AccountListPanel.class);

	private GBankingTable accountListTable;

	private OverviewBasePanel parentPanel;

	private AccountTableModel modelAccount;

	public AccountListPanel(OverviewBasePanel parentPanel) {
		this.parentPanel = parentPanel;
		createInnerAccountListPanel();
	}

	private void createInnerAccountListPanel() {

		List<BankAccount> accountList = dbController.getAll(BankAccount.class);
		String[] titlesAccount = null;
		switch (parentPanel.getPageContext()) {
		case ALL_ACCOUNTS:
			titlesAccount = new String[] { "*", "ID", "Konto Name", "IBAN", "Bank", "Konto-Typ", "Saldo", "Stand" };
			modelAccount = new AccountTableModel(titlesAccount, accountList);
			accountListTable = new GBankingTable(modelAccount);
			accountListTable.getColumnModel().getColumn(1).setMinWidth(50);
			accountListTable.getColumnModel().getColumn(2).setMinWidth(200);
			accountListTable.getColumnModel().getColumn(2).setMaxWidth(200);
			accountListTable.getColumnModel().getColumn(3).setMinWidth(300);
			accountListTable.getColumnModel().getColumn(3).setMaxWidth(300);
			accountListTable.getColumnModel().getColumn(4).setMaxWidth(150);
			accountListTable.getColumnModel().getColumn(5).setMaxWidth(75);
			accountListTable.getColumnModel().getColumn(6).setMaxWidth(60);
			break;
		case ACCOUNTS_TRANSACTIONS, ACCOUNTS_MONEYTRANSFERS, CATEGORIES:
			setMinimumSize(new Dimension(335, (int) getMinimumSize().getHeight()));
			titlesAccount = new String[] { "*", "ID", "Konto Name", "Stand" };
			modelAccount = new AccountTableModel(titlesAccount, accountList);
			accountListTable = new GBankingTable(modelAccount);
			accountListTable.getColumnModel().getColumn(1).setMinWidth(250);
			accountListTable.getColumnModel().getColumn(2).setMaxWidth(60);
			break;
		default:
			log.error("Unknown Page Context: {}", parentPanel.getPageContext());
		}

		ListSelectionModel cellSelectionModel = accountListTable.getSelectionModel();
		AccountTableModel model = (AccountTableModel) accountListTable.getModel();
		cellSelectionModel.addListSelectionListener(getAccountTableSelectionListener(model));

		setLayout(new BorderLayout());
		Border accountListPanelBorder = BorderFactory.createTitledBorder("Konto");
		setBorder(accountListPanelBorder);

		accountListTable.setPreferredScrollableViewportSize(accountListTable.getPreferredSize());

		JScrollPane scrollPaneAccounts = new JScrollPane(accountListTable);
		scrollPaneAccounts.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPaneAccounts);
	}

	private ListSelectionListener getAccountTableSelectionListener(AccountTableModel model) {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int row = accountListTable.getSelectedRow();
				int accountId = accountListTable.getSelectedRowId();

				log.info(messages.getFormattedMessage("LOG_ACCOUNT_SELECTED", accountId));

				if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
					List<Booking> bookingList = dbController.getAllByParent(Booking.class, accountId);
					final BankAccount selectedAccount = model.getSelectedAccount(row);

					AccountsTransactionsOverviewPanel parent = ((AccountsTransactionsOverviewPanel) parentPanel);
					TransactionListPanel transactionListPanel = parent.getTransactionListPanel();

					transactionListPanel.updatePanelBorder("Umsätze - " + selectedAccount.getAccountName());
					transactionListPanel.updateModelBooking(bookingList);
					parent.repaint();

					parent.getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
					parent.getAccountDetailPanel().repaint();

				} else if (parentPanel.getPageContext() == PageContext.ALL_ACCOUNTS) {
					final BankAccount selectedAccount = model.getSelectedAccount(row);

					AllAccountsOverviewPanel parent = ((AllAccountsOverviewPanel) parentPanel);

					parent.getAccountDetailPanel().updatePanelFieldValues(selectedAccount);
					parent.getAccountDetailPanel().repaint();

				} else if (parentPanel.getPageContext() == PageContext.ACCOUNTS_MONEYTRANSFERS) {
					List<MoneyTransfer> moneytransferList = dbController.getAllByParent(MoneyTransfer.class, accountId);
					final BankAccount selectedAccount = model.getSelectedAccount(row);

					MoneyTransferOverviewPanel parent = ((MoneyTransferOverviewPanel) parentPanel);
					parent.setSelectedAccount(((AccountTableModel) accountListTable.getModel()).getSelectedAccount(row));
					MoneyTransferListPanel moneyTransferListPanel = parent.getMoneyTransferListPanel();

					moneyTransferListPanel.updatePanelBorder(OrderType.TRANSFER.getPlural() + " " + selectedAccount.getAccountName());
					moneyTransferListPanel.updateModelMoneytransfer(moneytransferList);
					parent.repaint();

					parent.getMoneyTransferInputPanel().updatePanelFieldValues(selectedAccount);
					parent.getMoneyTransferInputPanel().repaint();

				} else {
					log.error("Unknown Page Context: {}", parentPanel.getPageContext());
				}
			}
		};
	}

	public AccountTableModel getModelAccount() {
		return modelAccount;
	}

	public void refreshModelAccount() {
		modelAccount.setResultlist(dbController.getAll(BankAccount.class));
		modelAccount.fireTableDataChanged();
	}

}
