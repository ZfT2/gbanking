package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.List;

import de.gbanking.db.dao.BankAccess;

public class BankAccessTableModel extends GBankingTableModel<BankAccess> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7720763082645143528L;

	public BankAccessTableModel(String[] columnNames, List<BankAccess> accesses) {
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(accesses);
	}
	
	@Override
	public Object getValueAt(int row, int column) {
		switch (column) {
		case 0:
			return resultlist.get(row).isSelected();
		case 1:
			return resultlist.get(row).getId();
		case 2:
			return resultlist.get(row).getBankName();
		case 3:
			return resultlist.get(row).getUserId();
		case 4:
			return resultlist.get(row).getTanProcedure();
		case 5:
			return resultlist.get(row).getHbciURL();
		case 6:
			return resultlist.get(row).isActive();
		case 7:
			return resultlist.get(row).getUpdatedAt() != null ? resultlist.get(row).getUpdatedAt().getTime() : null;
			//return resultlist.get(row).getUpdatedAt();
		default:
			return null;
		}
	}
	
	@Override
	public boolean isCellEditable(int row, int column) {
		if (column == 0) {
			return true;
		}
		return false;
	}


	public BankAccess getSelectedBankAccess(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedBankAccountId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}

}