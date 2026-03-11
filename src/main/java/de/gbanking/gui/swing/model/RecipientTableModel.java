package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.List;

import de.gbanking.db.dao.Recipient;
import de.gbanking.gui.swing.enu.PageContext;

public class RecipientTableModel extends GBankingTableModel<Recipient> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7082043034380715920L;

	private PageContext pageContext;

	public RecipientTableModel(String[] columnNames, List<Recipient> recipients, PageContext context) {
		this.pageContext = context;
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(recipients);
	}

	@Override
	public Object getValueAt(int row, int column) {

		if (resultlist.isEmpty()) {
			return null;
		}
		switch (column) {
		case 0:
			return resultlist.get(row).isSelected();
		case 1:
			return resultlist.get(row).getId();
		case 2:
			return resultlist.get(row).getName();
		case 3:
			return resultlist.get(row).getIban();
		default:
			;
		}

		if (pageContext == PageContext.ACCOUNTS_MONEYTRANSFERS) { /** "*", "ID", "Name", "IBAN", "Bank" **/
			switch (column) {
			case 4:
				return resultlist.get(row).getBank();
			case 5:
				return resultlist.get(row).getUpdatedAt().getTime();
			default:
				return null;
			}
		} else { /** "*", "ID", "Name", "IBAN", "Konto-Nr.", "BIC", "BLZ", "Bank", "Stand" **/
			switch (column) {
			case 4:
				return resultlist.get(row).getAccountNumber();
			case 5:
				return resultlist.get(row).getBic();
			case 6:
				return resultlist.get(row).getBlz();
			case 7:
				return resultlist.get(row).getBank();
			case 8:
				return resultlist.get(row).getUpdatedAt().getTime();
			default:
				return null;
			}
		}
	}

//	@Override
//	public Class<?> getColumnClass(int c) {
//		if (c == 2 && getValueAt(0, c) == null) {
//			return Date.class;
//		}
//		return super.getColumnClass(c);
//	}

	public Recipient getSelectedRecipient(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedRecipientId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}

}