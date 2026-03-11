package de.gbanking.gui.swing.model;

import java.util.ArrayList;
import java.util.List;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.util.TypeConverter;

public class AccountTableModel extends GBankingTableModel<BankAccount> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7720763082645143528L;

	public AccountTableModel(String[] columnNames, List<BankAccount> accounts) {
		this.columnNames = columnNames;
		this.resultlist = new ArrayList<>(accounts);
	}

	@Override
	public Object getValueAt(int row, int column) {
		
		if (resultlist.isEmpty()) {
			return null;
		}
		
		switch (column) {
		// "*", "ID", "Konto Name", "Stand"
		// "*", "ID", "Konto Name", "IBAN", "Bank", "Konto-Typ", "Saldo", "Stand" 
		case 0:
			return resultlist.get(row).isSelected();
		case 1:
			return resultlist.get(row).getId();
		case 2:
			return resultlist.get(row).getAccountName();
		case 3:
			return columnNames.length == 4 ? dateFormat.format(resultlist.get(row).getUpdatedAt().getTime()) : resultlist.get(row).getIban();
		case 4:
			return resultlist.get(row).getBankName();
		case 5:
			return resultlist.get(row).getAccountType().toString();
		case 6:
			return resultlist.get(row).getBalance() != null ? resultlist.get(row).getBalance().toString() : null;
		case 7:
			return dateFormat.format(resultlist.get(row).getUpdatedAt().getTime());
		default:
			return null;
		}
	}
	
	@Override
    public void setValueAt(Object value, int row, int column) {
		switch (column) {
		// "*", "ID", "Konto Name", "Stand"
		case 0:
			resultlist.get(row).setSelected((boolean) value);
			break;
		case 1:
			break;
		case 2:
			resultlist.get(row).setAccountName((String) value);
			break;
		case 3:
			resultlist.get(row).setUpdatedAt(TypeConverter.toCalendarFromTimestampStr((String) value));
			break;
		default:
			;
		}
		
        super.setValueAt(value, row, column);
    }
	
	public List<BankAccount> getCheckedAccounts() {
		List<BankAccount> bankAccountList = new ArrayList<>();
		for (BankAccount account : resultlist) {
			if (account.isSelected()) {
				bankAccountList.add(account);
			}
		}
		return bankAccountList;
	}

	public BankAccount getSelectedAccount(int rowIndex) {
		return resultlist.get(rowIndex);
	}

	public int getSelectedAccountId(int rowIndex) {
		return resultlist.get(rowIndex).getId();
	}
	
	public List<BankAccount> getAllAccounts() {
		return resultlist;
	}

}