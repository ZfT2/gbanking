package de.gbanking.gui.swing.panel.transaction;

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

import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.swing.components.GBankingTable;
import de.gbanking.gui.swing.model.BookingTableModel;
import de.gbanking.gui.swing.model.BookingTableModel.DateTuple;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.gui.swing.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.swing.panel.overview.OverviewBasePanel;
import de.gbanking.gui.swing.panel.overview.TransactionsOverviewBasePanel;
import de.gbanking.util.TypeConverter;

public class TransactionListPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2323586317316243587L;

	private static Logger log = LogManager.getLogger(TransactionListPanel.class);

	private GBankingTable bookingListTable;

	private OverviewBasePanel parentPanel;

	private TitledBorder accountTransactionsPanelBorder;

	private BookingTableModel modelBooking;

	private transient TableRowSorter<BookingTableModel> sorter;
	private JTextField filterText;

	public TransactionListPanel(OverviewBasePanel parent) {
		this.parentPanel = parent;
		createInnerTransactionsPanel();
	}

	private void createInnerTransactionsPanel() {

		log.debug("createInnerTransactionsPanel()");

		accountTransactionsPanelBorder = BorderFactory.createTitledBorder("Umsätze");
		setBorder(accountTransactionsPanelBorder);
		setLayout(new BorderLayout());

		List<Booking> bookingList = null;
		String[] titlesBooking = null;
		switch (parentPanel.getPageContext()) {
		case ACCOUNTS_TRANSACTIONS:
			titlesBooking = new String[] { "*", "Konto ID.", "<html>Datum<br>(Wert)</html>", "Verwendungszweck", "Betrag", "Saldo", "Typ", "Gegenkonto" };
			bookingList = bean.getBookingsForAccount(1);
			break;
		case ALL_TRANSACTIONS:
			titlesBooking = new String[] { "*", "Konto ID.", "<html>Datum<br>(Wert)</html>", "Verwendungszweck", "Betrag", "Saldo", "Typ", "Konto", "Konto" };
			bookingList = bean.getAllBookings();
			break;
		default:
			log.error("Unkown Page Context for retrieving bookings {}", parentPanel.getPageContext());
		}

		modelBooking = new BookingTableModel(titlesBooking, bookingList);

		bookingListTable = new GBankingTable(modelBooking) {

			private static final long serialVersionUID = 5718268208469955830L;

			DefaultTableCellRenderer renderPurpose = new DefaultTableCellRenderer();

			{
				renderPurpose.setHorizontalAlignment(SwingConstants.LEFT);
				renderPurpose.setVerticalAlignment(SwingConstants.TOP);
			}

			DateRenderer renderDate = new DateRenderer();

			@Override
			public TableCellRenderer getCellRenderer(int arg0, int arg1) {
				switch (arg1) {
				case 2:
					return renderPurpose;
				case 1:
					return renderDate;
				default:
					return super.getCellRenderer(arg0, arg1);
				}
			}
		};

		updateModelBooking(bookingList);

		bookingListTable.setRowHeight(70);

		bookingListTable.getColumnModel().getColumn(1).setMaxWidth(60);

		bookingListTable.getColumnModel().getColumn(2).setMinWidth(550);

		bookingListTable.getColumnModel().getColumn(3).setMaxWidth(70);
		bookingListTable.getColumnModel().getColumn(4).setMaxWidth(70);
		
		bookingListTable.getColumnModel().getColumn(3).setCellRenderer(bookingListTable.getBigDecimalRenderer());
		bookingListTable.getColumnModel().getColumn(4).setCellRenderer(bookingListTable.getBigDecimalRenderer());

		bookingListTable.getColumnModel().getColumn(5).setMaxWidth(25);
		bookingListTable.getColumnModel().getColumn(6).setMinWidth(75);

		ListSelectionModel cellSelectionModel = bookingListTable.getSelectionModel();

		BookingTableModel model = (BookingTableModel) bookingListTable.getModel();
		cellSelectionModel.addListSelectionListener(getBookingTableSelectionListener(model));

		sorter = new TableRowSorter<>(modelBooking);
		bookingListTable.setRowSorter(sorter);

		JScrollPane scrollPaneTransactions = new JScrollPane(bookingListTable);
		scrollPaneTransactions.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(scrollPaneTransactions, BorderLayout.CENTER /* gbc */);

		JLabel labelFilter = new JLabel("Suche:", SwingConstants.LEFT);

		JPanel filterPanel = new JPanel(new BorderLayout());
		filterPanel.add(labelFilter, BorderLayout.LINE_START);

		filterText = new JTextField();
		/* Whenever filterText changes, invoke newFilter. */
		filterText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				newFilter();
			}

			public void insertUpdate(DocumentEvent e) {
				newFilter();
			}

			public void removeUpdate(DocumentEvent e) {
				newFilter();
			}
		});
		labelFilter.setLabelFor(filterText);

		filterPanel.add(filterText, BorderLayout.CENTER);

		add(filterPanel, BorderLayout.PAGE_END);
	}

	/**
	 * Update the row filter regular expression from the expression in the text box.
	 */
	private void newFilter() {
		RowFilter<BookingTableModel, Object> rf = null;
		// If current expression doesn't parse, don't update.
		try {
			String text = filterText.getText();
			// text = text.replace(".", "\\.");
			List<RowFilter<Object, Object>> rfs = new ArrayList<>(2);
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

			if (value instanceof DateTuple dateTuple) {
				if (dateTuple.dateValue != null && !dateTuple.dateValue.equals(dateTuple.dateBooking)) {
					setText("<html><p>" + formatter.format(dateTuple.dateBooking.getTime()) + "<br>("
							+ formatter.format(dateTuple.dateValue.getTime()) + ")</p></html>");
				} else {
					setText(formatter.format(dateTuple.dateBooking.getTime()));
				}
			} else {
				Date date = null;
				date = value instanceof Calendar calendar ? calendar.getTime() : (Date) value;
				setText((date == null) ? "" : formatter.format(date));
			}
		}
	}

	private ListSelectionListener getBookingTableSelectionListener(BookingTableModel model) {
		return (ListSelectionEvent e) -> {
			if (!e.getValueIsAdjusting()) {

				int column = 1; // Booking-Id
				int row = bookingListTable.getSelectedRow();
				if (bookingListTable.getRowSorter() != null) {
					row = bookingListTable.getRowSorter().convertRowIndexToModel(row);
				}
				int bookingId = (int) bookingListTable.getModel().getValueAt(row, column);

				log.info(messages.getFormattedMessage("LOG_BOOKING_SELECTED", bookingId));

				Booking booking = model.getSelectedBooking(row);
				//Recipient recipient = dbController.getRecipientById(booking.getRecipientId());
				Recipient recipient = dbController.getByIdFull(Recipient.class, booking.getRecipientId());
				booking.setRecipient(recipient);
				TransactionsOverviewBasePanel parent = ((TransactionsOverviewBasePanel) parentPanel);
				parent.getTransactionDetailPanel().updatePanelFieldValues(booking);
				if (parent instanceof AccountsTransactionsOverviewPanel accountsTransactionsOverviewPanel) {
					accountsTransactionsOverviewPanel.enableTransactionDetailPanel();
				}
			}
		};
	}

	public void updateModelBooking(List<Booking> bookingList) {
		modelBooking.setResultlist(bookingList);
		modelBooking.fireTableDataChanged();
	}

	public void updatePanelBorder(String borderTitle) {
		accountTransactionsPanelBorder.setTitle(borderTitle);
	}
}
