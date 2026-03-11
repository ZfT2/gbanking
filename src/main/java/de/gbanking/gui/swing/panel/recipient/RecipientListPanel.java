package de.gbanking.gui.swing.panel.recipient;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.model.RecipientTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferDetailListTabPanel;
import de.gbanking.gui.swing.panel.moneytransfer.MoneyTransferInputBasePanel;
import de.gbanking.gui.swing.panel.overview.RecipientOverviewPanel;

public class RecipientListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3908212005397723334L;

	private static Logger log = LogManager.getLogger(RecipientListPanel.class);

	private transient TableRowSorter<RecipientTableModel> sorter;
	private JTextField filterText;

	private JTable tableRecipients;

	private RecipientTableModel recipientTableModel;

	private JPanel parentPanel;
	
	private PageContext pageContext;

	public RecipientListPanel(JPanel parentPanel) {
		this.parentPanel = parentPanel;
		pageContext = parentPanel instanceof MoneyTransferDetailListTabPanel ? PageContext.ACCOUNTS_MONEYTRANSFERS : PageContext.RECIPIENTS;
		createInnerRecipientListPanel();
	}

	private void createInnerRecipientListPanel() {

		log.debug("createInnerMoneyTransfersPanel()");

		TitledBorder recipientsPanelBorder = BorderFactory.createTitledBorder("Adressbuch");
		setBorder(recipientsPanelBorder);

		List<Recipient> recipientList = dbController.getAllFull(Recipient.class);

		String[] titlesRecipient = null;
		if (pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS) {
			titlesRecipient = new String[] { "*", "ID", "Name", "IBAN", "Bank" };
		} else {
			titlesRecipient = new String[] { "*", "ID", "Name", "IBAN", "Konto-Nr.", "BIC", "BLZ", "Bank", "Stand" };
		}
		recipientTableModel = new RecipientTableModel(titlesRecipient, recipientList, pageContext);

		tableRecipients = new GBankingTable(recipientTableModel);

		/* "*", "ID", "Name", "IBAN", "Bank" */
		tableRecipients.getColumnModel().getColumn(1).setMinWidth(75);
		tableRecipients.getColumnModel().getColumn(2).setWidth(150);

		ListSelectionModel cellSelectionModel = tableRecipients.getSelectionModel();

		cellSelectionModel.addListSelectionListener(getRecipientTableSelectionListener());

		sorter = new TableRowSorter<>(recipientTableModel);
		tableRecipients.setRowSorter(sorter);

		JScrollPane scrollPaneRecipiens = new JScrollPane(tableRecipients);
		scrollPaneRecipiens.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		setLayout(new BorderLayout());
		add(scrollPaneRecipiens, BorderLayout.CENTER);

		JPanel filterPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbcFilter = new GridBagConstraints();
		gbcFilter.insets = new Insets(2, 2, 2, 2);
		gbcFilter.weightx = 0.05;
		gbcFilter.gridx = 0;
		
		JLabel labelFilter = new JLabel("Suche:", SwingConstants.TRAILING);
		filterPanel.add(labelFilter, gbcFilter);
		filterText = new JTextField();
		filterText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				recipientFilter();
			}

			public void insertUpdate(DocumentEvent e) {
				recipientFilter();
			}

			public void removeUpdate(DocumentEvent e) {
				recipientFilter();
			}
		});
		labelFilter.setLabelFor(filterText);
		gbcFilter.weightx = 0.95;
		gbcFilter.gridx = 1;
		gbcFilter.fill = GridBagConstraints.HORIZONTAL;
		filterPanel.add(filterText, gbcFilter);
		add(filterPanel, BorderLayout.PAGE_END);

	}

	/**
	 * Update the row filter regular expression from the expression in the text box.
	 */
	private void recipientFilter() {
		RowFilter<RecipientTableModel, Object> rf = null;
		try {
			String text = filterText.getText();
			List<RowFilter<Object, Object>> rfs = new ArrayList<>(2);
			rfs.add(RowFilter.regexFilter("(?i)" + text));
			rf = RowFilter.andFilter(rfs);
		} catch (java.util.regex.PatternSyntaxException e) {
			return;
		}
		sorter.setRowFilter(rf);
	}

	private ListSelectionListener getRecipientTableSelectionListener() {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int column = 1; // Recipient-Id
				int row = tableRecipients.getSelectedRow();
				if (tableRecipients.getRowSorter() != null) {
					row = tableRecipients.getRowSorter().convertRowIndexToModel(row);
				}
				int recipientId = (int) tableRecipients.getModel().getValueAt(row, column);

				log.info(messages.getFormattedMessage("LOG_INFO_RECIPIENT_SELECTED", recipientId));

				final Recipient selectedRecipient = recipientTableModel.getSelectedRecipient(row);

				if (pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS) {
					MoneyTransferDetailListTabPanel parent = ((MoneyTransferDetailListTabPanel) parentPanel);
					MoneyTransferInputBasePanel moneyTransferInputPanel = parent.getMoneyTransferInputPanel();
					moneyTransferInputPanel.updatePanelFieldValues(selectedRecipient);
					moneyTransferInputPanel.revalidate();
					moneyTransferInputPanel.repaint();
				} else {
					RecipientOverviewPanel parent = ((RecipientOverviewPanel) parentPanel);
					RecipientDetailPanel recipientDetailPanel = parent.getRecipientDetailPanel();
					recipientDetailPanel.updatePanelFieldValues(selectedRecipient);
					recipientDetailPanel.revalidate();
					recipientDetailPanel.repaint();
				}
			}
		};
	}

}
