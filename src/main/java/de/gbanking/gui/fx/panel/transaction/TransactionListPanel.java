package de.gbanking.gui.fx.panel.transaction;

import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.fx.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.fx.panel.overview.TransactionsOverviewBasePanel;
import de.gbanking.gui.fx.util.DateFormatUtils;
import de.gbanking.gui.fx.util.FxTableUtils;
import de.gbanking.gui.fx.util.TableColumnFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class TransactionListPanel extends AbstractFilterableTablePanel<Booking> {

	private static final Logger log = LogManager.getLogger(TransactionListPanel.class);

	private final TransactionsOverviewBasePanel parentPanel;
	private final ObservableList<Booking> masterData;

	public TransactionListPanel(TransactionsOverviewBasePanel parent) {
		this(FXCollections.observableArrayList(), parent);
	}

	private TransactionListPanel(ObservableList<Booking> data, TransactionsOverviewBasePanel parent) {
		super(data);
		this.masterData = data;
		this.parentPanel = parent;
		createInnerTransactionsPanel();
	}

	private void createInnerTransactionsPanel() {
		setPanelTitle(getText("UI_PANEL_TRANSACTIONS"));
		createColumns();
		tableView.setFixedCellSize(70);

		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			masterData.setAll(bean.getAllBookings());
		}

		tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, booking) -> {
			if (booking != null) {
				handleBookingSelection(booking);
			}
		});
	}

	private void createColumns() {
		TableColumn<Booking, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Booking::isSelected, Booking::setSelected);

		TableColumn<Booking, String> dateCol = TableColumnFactory.createFixedTextColumn(getText("UI_TABLE_DATE"),
				booking -> DateFormatUtils.formatBookingAndValue(booking.getDateBooking(), booking.getDateValue()), 115);

		TableColumn<Booking, String> purposeCol = TableColumnFactory.createWrappedTextColumn(getText("UI_TABLE_PURPOSE"), Booking::getPurpose, 320, 500);

		TableColumn<Booking, String> amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"),
				booking -> booking.getAmount() != null ? booking.getAmount().toString() : "", 110);

		TableColumn<Booking, String> balanceCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_BALANCE"),
				booking -> booking.getBalance() != null ? booking.getBalance().toString() : "", 110);

		TableColumn<Booking, String> typeCol = TableColumnFactory.createSymbolColumn(getText("UI_TABLE_BOOKING_TYPE"),
				booking -> booking.getSource() != null ? booking.getSource().getSymbol() : "", 70);

		if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			TableColumn<Booking, String> crossAccountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_COUNTER_ACCOUNT"),
					Booking::getCrossAccountName, 160, 200);
			tableView.getColumns().setAll(List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, crossAccountCol));
		} else {
			TableColumn<Booking, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT"), Booking::getAccountName, 160, 200);
			tableView.getColumns().setAll(List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, accountCol));
		}
	}

	@Override
	protected boolean matchesFilter(Booking booking, String filter) {
		if (filter.isBlank()) {
			return true;
		}

		return contains(booking.getPurpose(), filter) || contains(booking.getCurrency(), filter) || matchesNumbers(booking, filter)
				|| matchesSymbols(booking, filter) || contains(booking.getCrossAccountName(), filter) || contains(booking.getAccountName(), filter);
	}

	private boolean matchesNumbers(Booking booking, String filter) {
		return (booking.getAmount() != null && booking.getAmount().toString().toLowerCase().contains(filter))
				|| (booking.getBalance() != null && booking.getBalance().toString().toLowerCase().contains(filter));
	}

	private boolean matchesSymbols(Booking booking, String filter) {
		return booking.getSource() != null && booking.getSource().getSymbol() != null && booking.getSource().getSymbol().toLowerCase().contains(filter);
	}

	private void handleBookingSelection(Booking booking) {
		log.info(messages.getFormattedMessage("LOG_INFO_BOOKING_SELECTED", booking.getId()));

		Recipient recipient = dbController.getByIdFull(Recipient.class, booking.getRecipientId());
		booking.setRecipient(recipient);

		parentPanel.getTransactionDetailPanel().updatePanelFieldValues(booking);

		if (parentPanel instanceof AccountsTransactionsOverviewPanel parent) {
			parent.enableTransactionDetailPanel();
		}
	}

	public void updateModelBooking(List<Booking> bookingList) {
		masterData.setAll(bookingList);
	}

	public void updatePanelBorder(String title) {
		setPanelTitle(title);
	}

	public void reload() {
		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			masterData.setAll(bean.getAllBookings());
		}
	}
}