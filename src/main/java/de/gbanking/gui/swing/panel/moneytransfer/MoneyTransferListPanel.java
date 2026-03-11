package de.gbanking.gui.swing.panel.moneytransfer;

import java.awt.BorderLayout;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.MoneyTransfer;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.OrderType;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.model.MoneytransferTableModel;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.util.TypeConverter;

public class MoneyTransferListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2818778756966001173L;

	private static Logger log = LogManager.getLogger(MoneyTransferListPanel.class);

	private static TableRowSorter<MoneytransferTableModel> sorter;
	private static JTextField filterText;

	private TitledBorder accountMoneyTransfersPanelBorder;

	private MoneytransferTableModel modelMoneytransfer;
	
	private JPanel parentPanel;

	public MoneyTransferListPanel(OrderType orderType, JPanel parent) {
		this.parentPanel = parent;
		createInnerMoneyTransfersListPanel(orderType);
	}

	private void createInnerMoneyTransfersListPanel(OrderType orderType) {

		log.debug("createInnerMoneyTransfersPanel()");

		accountMoneyTransfersPanelBorder = BorderFactory.createTitledBorder(orderType.getPlural());
		setBorder(accountMoneyTransfersPanelBorder);
		setLayout(new BorderLayout());

		List<MoneyTransfer> moneyTransferList = dbController.getAllByParent(MoneyTransfer.class, 1);

		String[] titlesTransfer = new String[] { "*", "ID", "Datum", "Empfänger", "Verwendungszweck", "Betrag", "IBAN", "Bank" };
		modelMoneytransfer = new MoneytransferTableModel(titlesTransfer, moneyTransferList);

		JTable tableMoneyTransfers = new GBankingTable(modelMoneytransfer) {
			private static final long serialVersionUID = -3191205968703041341L;
			DefaultTableCellRenderer renderPurpose = new DefaultTableCellRenderer();

			{ // initializer block
				renderPurpose.setHorizontalAlignment(SwingConstants.LEFT);
				renderPurpose.setVerticalAlignment(SwingConstants.TOP);
			}

			DateRenderer renderDate = new DateRenderer();

			@Override
			public TableCellRenderer getCellRenderer(int arg0, int arg1) {
				if (arg1 == 3) {
					return renderPurpose;
				} else if (arg1 == 1) {
					return renderDate;
				} else {
					return super.getCellRenderer(arg0, arg1);
				}
			}
		};

		updateModelMoneytransfer(moneyTransferList);

		// "*", "ID", "Datum", "Empfänger", "Verwendungszweck", "Betrag", "IBAN", "Bank"

		tableMoneyTransfers.getColumnModel().getColumn(1).setMaxWidth(60);
		tableMoneyTransfers.getColumnModel().getColumn(2).setMaxWidth(200);

		tableMoneyTransfers.getColumnModel().getColumn(3).setMinWidth(250);

		tableMoneyTransfers.getColumnModel().getColumn(4).setMaxWidth(60);
		tableMoneyTransfers.getColumnModel().getColumn(5).setMaxWidth(150);
		tableMoneyTransfers.getColumnModel().getColumn(6).setMaxWidth(200);
		
		ListSelectionModel cellSelectionModel = tableMoneyTransfers.getSelectionModel();
		cellSelectionModel.addListSelectionListener(getMoneytransferTableSelectionListener(tableMoneyTransfers));

		sorter = new TableRowSorter<>(modelMoneytransfer);
		tableMoneyTransfers.setRowSorter(sorter);

		JScrollPane scrollPaneOrders = new JScrollPane(tableMoneyTransfers);
		scrollPaneOrders.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(/* tablePanel */ scrollPaneOrders, BorderLayout.CENTER /* gbc */);

		// Create a separate form for filterText and statusText
		JLabel labelFilter = new JLabel("Suche:", SwingConstants.LEFT);

		JPanel filterPanel = new JPanel(new BorderLayout());
		filterPanel.add(labelFilter, BorderLayout.LINE_START);

		filterText = new JTextField();
		// Whenever filterText changes, invoke newFilter.
		filterText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				moneytransferFilter();
			}

			public void insertUpdate(DocumentEvent e) {
				moneytransferFilter();
			}

			public void removeUpdate(DocumentEvent e) {
				moneytransferFilter();
			}
		});
		labelFilter.setLabelFor(filterText);

		filterPanel.add(filterText, BorderLayout.CENTER);

		add(filterPanel, BorderLayout.PAGE_END);
	}

	/**
	 * Update the row filter regular expression from the expression in the text box.
	 */
	private static void moneytransferFilter() {
		RowFilter<MoneytransferTableModel, Object> rf = null;
		// If current expression doesn't parse, don't update.
		try {
			String text = filterText.getText();
			// text = text.replace(".", "\\.");
			List<RowFilter<Object, Object>> rfs = new ArrayList<RowFilter<Object, Object>>(2);
			// rfs.add(RowFilter.regexFilter("(?i)" + text /* + "$"*/, 1,2));
			rfs.add(RowFilter.regexFilter("(?i)" + text /* + "$" */, 1, 2, 3, 4, 5, 6));
			// rfs.add(RowFilter.dateFilter(ComparisonType.EQUAL,
			// TypeConverter.toCalendar(filterText.getText()).getTime(), 1,2));
			rf = RowFilter.andFilter(rfs);

			// rf = RowFilter.regexFilter(filterText.getText(), 1,2,3,4,5,6);
		} catch (java.util.regex.PatternSyntaxException e) {
			return;
		}
		sorter.setRowFilter(rf);
	}

	private static class DateRenderer extends DefaultTableCellRenderer {
		/**
		 * 
		 */
		private static final long serialVersionUID = 8504625134533219749L;
		DateFormat formatter;

		public DateRenderer() {
			super();
		}

		@Override
		public void setValue(Object value) {
			if (formatter == null) {
				formatter = new SimpleDateFormat(TypeConverter.DATE_PATTERN_SHORT);
			}
			Date dateValue = null;
			dateValue = value instanceof Calendar calendar ? calendar.getTime() : (Date) value;
			setText((dateValue == null) ? "" : formatter.format(dateValue));
		}
	}

	public void updateModelMoneytransfer(List<MoneyTransfer> orderList) {
		modelMoneytransfer.setResultlist(orderList);
		modelMoneytransfer.fireTableDataChanged();
	}

	public void updatePanelBorder(String borderTitle) {
		accountMoneyTransfersPanelBorder.setTitle(borderTitle);
	}
	
	private ListSelectionListener getMoneytransferTableSelectionListener(JTable moneytransferListTable) {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int column = 1; // Moneytransfer-Id
				int row = moneytransferListTable.getSelectedRow();
				Object idValue = moneytransferListTable.getModel().getValueAt(row, column);
				if (idValue == null) {
					return;
				}
				int moneytransferId = (int) idValue;

				log.info(messages.getFormattedMessage("LOG_INFO_MONEYTRANSFER_SELECTED", moneytransferId));

				MoneyTransfer moneytransfer = modelMoneytransfer.getSelectedMoneytransfer(row);
//				Recipient recipient = dbController.getRecipientById(moneytransfer.getRecipientId());
				Recipient recipient = dbController.getByIdFull(Recipient.class, moneytransfer.getRecipientId());
				moneytransfer.setRecipient(recipient);
				MoneyTransferDetailListTabPanel parent = ((MoneyTransferDetailListTabPanel) parentPanel);
				parent.getMoneyTransferInputPanel().updatePanelFieldValues(moneytransfer);
			}
		};
	}

}
