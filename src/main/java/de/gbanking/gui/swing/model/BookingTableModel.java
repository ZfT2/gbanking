package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import de.gbanking.db.dao.Booking;

public class BookingTableModel extends GBankingTableModel<Booking> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4655930093131678566L;

	public BookingTableModel(String[] columnNames, List<Booking> bookings) {
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(bookings);
	}

	public class DateTuple implements Comparable<DateTuple> {

		public final Calendar dateBooking;
		public final Calendar dateValue;

		public DateTuple(Calendar date1, Calendar date2) {
			this.dateBooking = date1;
			this.dateValue = date2;
		}

		@Override
		public int compareTo(DateTuple o) {
			return this.dateBooking.before(o.dateBooking) ? -1 : 1;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + Objects.hash(dateBooking, dateValue);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DateTuple other = (DateTuple) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			return Objects.equals(dateBooking, other.dateBooking) && Objects.equals(dateValue, other.dateValue);
		}

		private BookingTableModel getEnclosingInstance() {
			return BookingTableModel.this;
		}
	}

	@Override
	public Object getValueAt(int row, int column) {

		if (resultlist.isEmpty()) {
			return null;
		}

		switch (column) {
		// "*", "Konto ID.", "Datum (Wert)", "Wert", "Verwendungszweck", "Betrag",
		// "Saldo", "Typ", "Gegenkonto"
		case 0:
			return resultlist.get(row).isSelected();
		case 1:
			return resultlist.get(row).getId();
		case 2:
			return /* getDatesForColumn(resultlist.get(row)); */ new DateTuple(resultlist.get(row).getDateBooking(),
					resultlist.get(row).getDateValue());
//		case 3:
//			return resultlist.get(row).getDateValue() != null ? resultlist.get(row).getDateValue().getTime() : null;
		case 3:
			return "<html><p>" + resultlist.get(row).getPurpose() + "</p></html>";
		case 4:
			return resultlist.get(row).getAmount();
		case 5:
			return resultlist.get(row).getBalance();
		case 6:
			return resultlist.get(row).getSource() != null ? resultlist.get(row).getSource().getSymbol() : null;
		case 7:
			return columnNames.length == 8 ? resultlist.get(row).getCrossAccountName() : resultlist.get(row).getAccountName();
		case 8:
			return resultlist.get(row).getCrossAccountName();
		default:
			return null;
		}
	}

	@Override
	public Class<?> getColumnClass(int c) {
		if (c == 2 && getValueAt(0, c) == null) {
			return Date.class;
		}
		return super.getColumnClass(c);
	}

	public Booking getSelectedBooking(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedBookingId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}

}