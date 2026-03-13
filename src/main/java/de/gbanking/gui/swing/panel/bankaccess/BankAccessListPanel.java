package de.gbanking.gui.swing.panel.bankaccess;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.BankAccess;
import de.gbanking.db.dao.BankAccount;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.model.BankAccessTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.BankAccessOverviewPanel;

public class BankAccessListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1821214499292194496L;

	private static Logger log = LogManager.getLogger(BankAccessListPanel.class);

	private JTable accessListTable;
	private BankAccessTableModel modelBankAccess;

	private JPanel parentPanel;

	public BankAccessListPanel(JPanel parentPanel) {
		this.parentPanel = parentPanel;
		createBankAccessListPanel();
	}

	public void createBankAccessListPanel() {

		Border mainPanelBorder = BorderFactory.createTitledBorder("Bankzugänge");
		setBorder(mainPanelBorder);
		setLayout(new BorderLayout());

		List<BankAccess> bankAccessList = dbController.getAll(BankAccess.class);

		String[] titlesBankAccess = new String[] { "ausgewählt", "ID", "Bank", "UserId", "PIN Verfahren", "FinTS URL.", "aktiv", "Stand" };
		/*final BankAccessTableModel*/ modelBankAccess = new BankAccessTableModel(titlesBankAccess, bankAccessList);

		accessListTable = new GBankingTable(modelBankAccess);
		accessListTable.setModel(modelBankAccess);

		accessListTable.getColumnModel().getColumn(0).setMaxWidth(75);
		accessListTable.getColumnModel().getColumn(1).setMinWidth(100);
		accessListTable.getColumnModel().getColumn(2).setMaxWidth(75);
		accessListTable.getColumnModel().getColumn(3).setMaxWidth(75);

		accessListTable.getColumnModel().getColumn(5).setMaxWidth(75);

		ListSelectionModel cellSelectionModel = accessListTable.getSelectionModel();
		cellSelectionModel.addListSelectionListener(getAccessTableSelectionListener((BankAccessTableModel) accessListTable.getModel()));


		JScrollPane scrollPaneAccesses = new JScrollPane(accessListTable);
		scrollPaneAccesses.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPaneAccesses, BorderLayout.CENTER);

	}

	private ListSelectionListener getAccessTableSelectionListener(BankAccessTableModel model) {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int column = 1; // Access-Id
				int row = accessListTable.getSelectedRow();
				int accessId = (int) accessListTable.getModel().getValueAt(row, column);

				log.info(messages.getFormattedMessage("LOG_BANK_ACCESS_SELECTED", accessId));
				BankAccess selectedAccess = model.getSelectedBankAccess(row);

				List<BankAccount> bankAccessAccountList = dbController.getAllByParent(BankAccount.class, selectedAccess.getId());
				selectedAccess.setAccounts(bankAccessAccountList);

				BankAccessOverviewPanel parent = ((BankAccessOverviewPanel) parentPanel);

				parent.getBankAccessDetailPanel().updatePanelFieldValues(selectedAccess);
				parent.getBankAccessDetailPanel().repaint();

			}
		};
	}
	
	public void refreshModelBankAccess() {
		modelBankAccess.setResultlist(dbController.getAll(BankAccess.class));
		modelBankAccess.fireTableDataChanged();
		this.revalidate();
		this.repaint();
	}

}
