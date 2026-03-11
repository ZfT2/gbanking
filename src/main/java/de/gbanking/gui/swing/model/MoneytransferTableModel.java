package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.gbanking.db.dao.MoneyTransfer;

public class MoneytransferTableModel extends GBankingTableModel<MoneyTransfer> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 94451489288461247L;

	public MoneytransferTableModel(String[] columnNames, List<MoneyTransfer> orders) {
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(orders);
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
			return resultlist.get(row).getExecutionDate();
		case 3:
			return resultlist.get(row).getRecipient().getName();
		case 4:
			return "<html><p>"+ resultlist.get(row).getPurpose()+"</p></html>";
		case 5:
			return resultlist.get(row).getAmount();
		case 6:
			return resultlist.get(row).getRecipient().getIban();
		case 7:
			return resultlist.get(row).getRecipient().getBank();
		case 8:
			return resultlist.get(row).getStandingorderMode();
		case 9:
			return resultlist.get(row).getUpdatedAt().getTime();
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

	public MoneyTransfer getSelectedMoneytransfer(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedMoneytransferId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}

}