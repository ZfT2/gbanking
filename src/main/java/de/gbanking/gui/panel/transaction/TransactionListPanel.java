package de.gbanking.gui.panel.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.enu.PageContext;
import de.gbanking.gui.panel.AbstractFilterableTablePanel;
import de.gbanking.gui.panel.overview.AccountsTransactionsOverviewPanel;
import de.gbanking.gui.panel.overview.TransactionsOverviewBasePanel;
import de.gbanking.gui.util.DateFormatUtils;
import de.gbanking.gui.util.FxTableUtils;
import de.gbanking.gui.util.TableColumnFactory;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;

public class TransactionListPanel extends AbstractFilterableTablePanel<Booking> {

	private static final Logger log = LogManager.getLogger(TransactionListPanel.class);

	private final TransactionsOverviewBasePanel parentPanel;

	public TransactionListPanel(TransactionsOverviewBasePanel parent) {
		super(FXCollections.observableArrayList());
		this.parentPanel = parent;
		createInnerTransactionsPanel();
	}

	private void createInnerTransactionsPanel() {
		setPanelTitleByKey("UI_PANEL_TRANSACTIONS");
		setColumns(createColumns());
		tableView.setFixedCellSize(70);
		onSelection(this::handleBookingSelection);

		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceItems(bean.getAllBookings());
		}
	}

	private List<TableColumn<Booking, ?>> createColumns() {
		TableColumn<Booking, Boolean> selectedCol = FxTableUtils.createSelectionColumn(getText("UI_TABLE_SELECTED"), Booking::isSelected, Booking::setSelected);
		TableColumn<Booking, Booking> dateCol = new TableColumn<>(getText("UI_TABLE_DATE"));
		dateCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		dateCol.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
			@Override
			protected void updateItem(Booking item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : DateFormatUtils.formatBookingAndValue(item.getDateBooking(), item.getDateValue()));
			}
		});
		dateCol.setComparator((left, right) -> {
			int bookingDateCompare = java.util.Comparator.nullsLast(LocalDate::compareTo).compare(left != null ? left.getDateBooking() : null,
					right != null ? right.getDateBooking() : null);
			if (bookingDateCompare != 0) {
				return bookingDateCompare;
			}
			return java.util.Comparator.nullsLast(LocalDate::compareTo).compare(left != null ? left.getDateValue() : null,
					right != null ? right.getDateValue() : null);
		});
		FxTableUtils.setFixedWidth(dateCol, 115);
		TableColumn<Booking, String> purposeCol = TableColumnFactory.createWrappedTextColumn(getText("UI_TABLE_PURPOSE"), Booking::getPurpose, 320, 500);
		TableColumn<Booking, BigDecimal> amountCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_AMOUNT"), Booking::getAmount, 110);
		TableColumn<Booking, BigDecimal> balanceCol = TableColumnFactory.createAmountColumn(getText("UI_TABLE_BALANCE"), Booking::getBalance, 110);
		TableColumn<Booking, String> typeCol = TableColumnFactory.createSymbolColumn(getText("UI_TABLE_BOOKING_TYPE"),
				booking -> booking.getSource() != null ? booking.getSource().getSymbol() : "", 70);

		if (parentPanel.getPageContext() == PageContext.ACCOUNTS_TRANSACTIONS) {
			TableColumn<Booking, String> crossAccountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_COUNTER_ACCOUNT"),
					Booking::getCrossAccountName, 160, 200);
			return List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, crossAccountCol);
		}

		TableColumn<Booking, String> accountCol = TableColumnFactory.createTextColumn(getText("UI_TABLE_ACCOUNT"), Booking::getAccountName, 160, 200);
		return List.of(selectedCol, dateCol, purposeCol, amountCol, balanceCol, typeCol, accountCol);
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
		log.log(Level.INFO, () -> getText("LOG_BOOKING_SELECTED", booking.getId()));

		Recipient recipient = dbController.getByIdFull(Recipient.class, booking.getRecipientId());
		booking.setRecipient(recipient);
		parentPanel.getTransactionDetailPanel().updatePanelFieldValues(booking);

		if (parentPanel instanceof AccountsTransactionsOverviewPanel parent) {
			parent.enableTransactionDetailPanel();
		}
	}

	public void updateModelBooking(List<Booking> bookingList) {
		replaceItems(bookingList);
	}

	public void updatePanelBorder(String title) {
		setPanelTitle(title);
	}

	public void reload() {
		if (parentPanel.getPageContext() == PageContext.ALL_TRANSACTIONS) {
			replaceItems(bean.getAllBookings());
		}
	}
}
